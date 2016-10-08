(ns eponai.fullstack.jvmclient
  (:require [om.next :as om]
            [om.dom :as dom]
            [clojure.core.async :as async :refer [go <! >!]]
            [eponai.common.parser :as parser]
            [eponai.client.parser.mutate]
            [eponai.client.parser.read]
            [eponai.client.backend :as backend]
            [eponai.client.remotes :as remotes]
            [eponai.client.utils :as utils]
            [eponai.client.parser.merge :as merge]
            [eponai.server.core :as core]
            [eponai.server.datomic-dev :as datomic-dev]
            [clj-http.client :as http]
            [clj-http.cookies :as cookies]
            [clojure.core.async :as async]
            [taoensso.timbre :refer [debug error warn]]
            [medley.core :as medley]
            [clojure.data :as diff]
            [datomic.api :as datomic])
  (:import (org.eclipse.jetty.server Server)))

(om/defui JvmRoot
  static om/IQuery
  (query [this] [:datascript/schema
                 {:query/current-user [:user/uuid]}
                 :user/current
                 {:query/all-projects [:project/uuid
                                       :project/created-at
                                       :project/users]}
                 {:query/transactions [:transaction/title
                                       {:transaction/date [:date/timestamp]}]}
                 ])
  Object
  (render [this]
    (dom/div nil
      (dom/p nil (str "Schema keys: " (keys (:datascript/schema (om/props this)))))
      (dom/p nil "First 10 transaction titles:")
      (apply dom/div nil (map #(dom/p nil (:transaction/title %))
                              (:query/transactions (om/props this)))))))

(defmulti jvm-client-merge om/dispatch)

(defn create-client [endpoint-atom did-merge-fn]
  (let [conn (utils/create-conn)
        reconciler-atom (atom nil)
        cookie-store (cookies/cookie-store)
        reconciler (om/reconciler {:state   conn
                                   :parser  (parser/client-parser)
                                   :send    (backend/send! reconciler-atom
                                                           {:remote (-> (remotes/post-to-url nil)
                                                                        (remotes/wrap-update :url (fn [& _] @endpoint-atom))
                                                                        (remotes/read-basis-t-remote-middleware conn)
                                                                        (remotes/wrap-update :opts assoc
                                                                                             :cookie-store cookie-store))}
                                                           did-merge-fn)
                                   :merge   (merge/merge! jvm-client-merge)
                                   :migrate nil
                                   :history 100})
        _ (reset! reconciler-atom reconciler)
        c (om/add-root! reconciler JvmRoot nil)]
    (prn "c: " c)
    (prn "app-root: " (om/app-root reconciler))
    reconciler))

(defn- call-parser [reconciler query target]
  (let [parser (backend/get-parser reconciler)
        env (backend/to-env reconciler)]
    (parser env query target)))

(defn- app-state [reconciler]
  (call-parser reconciler
               (om/get-query (or (om/app-root reconciler) JvmRoot))
               nil))

(defn- <transact! [client->callback client query]
  (om/transact! client query)
  (client->callback client))

(defn mutation-state-machine [query-chan clients client->callback]
  (let [<transact! (partial <transact! client->callback)]
    (go
      (try
        (loop []
          (let [{:keys [::transaction ::test-fn]} (<! query-chan)]
            (when transaction
              (let [[client query] transaction
                    remote-query? (seq (call-parser client query :remote))]
                (if-not remote-query?
                  ;; Just apply query locally to a client:
                  (om/transact! client query)
                  ;; Apply and wait for query to be merged with remote response.
                  (let [client-tx (<transact! client query)
                        other-clients (->> (remove (partial = client) clients)
                                           (map #(<transact! % (om/full-query (om/app-root %)))))]
                    ;; Sync
                    (run! #(async/<!! %) (cons client-tx other-clients))))
                (when test-fn
                  (test-fn))
                (recur)))))
        (catch Throwable e
          (debug "Exception in state-machine: " e))
        (finally
          (async/<!! (async/go-loop []
                       (when (async/<! query-chan)
                         (recur)))))))))

(defn- take-with-timeout [chan label & [timeout-millis]]
  {:pre [(string? label)]}
  (let [[v c] (async/alts!! [chan (async/timeout (or timeout-millis 5000))])]
    (when-not (= c chan)
      (throw (ex-info "client login timed out" {:where (str "awaiting " label)})))
    v))

(defn- multiply-chan [chan n]
  (let [chan-mult (async/mult chan)
        readers (repeatedly n #(async/chan))]
    (doseq [r readers]
      (async/tap chan-mult r))
    readers))

(defn log-in! [client email-chan callback-chan]
  (om/transact! client `[(session.signin/email ~{:input-email datomic-dev/test-user-email
                                                 :device :jvm})])
  (take-with-timeout callback-chan "email transact!")
  (let [{:keys [verification]} (take-with-timeout email-chan "email with verification")
        {:keys [:verification/uuid]} verification]
    (backend/drain-channel callback-chan)
    (om/transact! client `[(session.signin.email/verify ~{:verify-uuid (str uuid)})])
    (take-with-timeout callback-chan "verification")))

(defn logged-in-client [server-url email-chan callback-chan]
  (let [endpoint-atom (atom (str server-url "/api"))
        did-merge-fn (fn [client] (async/put! callback-chan client))
        client (create-client endpoint-atom did-merge-fn)]
    (take-with-timeout callback-chan "initial merge")
    (log-in! client email-chan callback-chan)
    (reset! endpoint-atom (str server-url "/api/user"))
    client))

(defn init-clients-state
  "Init the state of each client to be the full query of the root component.

  I can think of three ways of doing this:
  - Sequentially: initiating 1 client at a time.
  - Parallel: initiating all clients at the same time.
  - Copying: initiating 1 client and copying its state to the other clients.

  Copying would be the fastest, but is it the same thing as doing it
  sequentially?"
  [clients client->callback]
  (->> clients
       (map #(<transact! client->callback % (om/get-query JvmRoot)))
       (run! #(async/<!! %))))

(defn create-clients [n {:keys [server email-chan query-chan done-chan]}]
  (let [callback-chan (async/chan)
        server-url (str "http://localhost:" (.getPort (.getURI server)))
        clients (repeatedly n #(logged-in-client server-url email-chan callback-chan))
        client->callback (into {} (map #(vector % (async/chan 10))) clients)
        _ (go
            (loop [client (<! callback-chan)]
              (if (nil? client)
                (medley/map-vals async/close! client->callback)
                (do (>! (client->callback client) client)
                    (recur (<! callback-chan))))))

        _ (init-clients-state clients client->callback)
        [sm-query-chan teardown-query-chan] (multiply-chan query-chan 2)
        sm (mutation-state-machine sm-query-chan clients client->callback)
        _ (async/go
            (try
              (loop []
                (if (async/<! teardown-query-chan)
                  (recur)
                  ;; query-chan is closed. Tear down:
                  (do (take-with-timeout sm "state-machine" (* 60 1000))
                      (async/close! email-chan)
                      (async/close! callback-chan)
                      ;; Ok to drain because everything is closed at this point.
                      (backend/drain-channel (async/merge (vals client->callback))))))
              (finally
                (async/close! done-chan))))]
    clients))

(defn test-setup []
  (let [conn (datomic-dev/create-connection)
        email-chan (async/chan)]
    {:conn       conn
     :email-chan email-chan
     :server     (core/start-server-for-tests {:conn       conn
                                               :email-chan email-chan})}))

(defn run-test [{:keys [server] :as system} test-fn]
  ;; TODO: Somehow re-use datomic connections?
  (let [query-chan (async/chan)
        done-chan  (async/chan)]
    (.start server)
    (try
      (let [clients (create-clients 2 (assoc system :query-chan query-chan
                                                    :done-chan done-chan))
            actions (test-fn clients)]
        (try
          (doseq [action actions]
            (async/put! query-chan action))
          (finally
            (async/close! query-chan)
            (async/<!! done-chan))))
      (finally
        (.stop server)
        (.join server)
        (debug "DONE!")))
    system))

(defn equal-app-states? [clients]
  (let [app-states (map app-state clients)
        eq? (apply = app-states)]
    (if eq?
      (debug "App state was equal! :D: " eq?)
      (run! (fn [[a b]]
              (when (not= a b)
                (error "App state NOT eq. diff: " (vec (diff/diff a b)))))
            (partition 2 1 app-states)))))

(defn run []
  (let [system (test-setup)]
    (-> system
        (run-test
          (fn [[client1 :as clients]]
            [{::transaction [client1 (om/get-query JvmRoot)]
              ::test-fn     #(do (when (.isRunning ^Server (:server system))
                                   (equal-app-states? clients)))}])))))

(comment `[(transaction/create {:transaction/tags       ({:tag/name "thailand"}),
                                :transaction/date       {:date/ymd "2015-10-10"}
                                :transaction/type       {:db/ident :transaction.type/expense},
                                :transaction/currency   {:currency/code "THB"},
                                :transaction/title      "lunch",
                                :transaction/project    [:project/uuid #uuid"57eeb170-4d6f-462c-b1e4-0b70c136924f"],
                                :transaction/uuid       #uuid"57eeb170-fc13-4f2d-b0e7-d36a624ab6d1",
                                :transaction/amount     180M,
                                :transaction/created-at 1})])
