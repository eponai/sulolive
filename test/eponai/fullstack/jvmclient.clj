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
            [taoensso.timbre :refer [debug]]))

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

(defn- app-state [reconciler]
  (let [parser (backend/get-parser reconciler)
        env (backend/to-env reconciler)
        query (om/get-query (or (om/app-root reconciler) JvmRoot))]
    (parser env query)))

(defn mutation-state-machine [query-chan online?-chan clients client->callback]
  (letfn [(<transact! [client query]
            (om/transact! client query)
            (client->callback client))]
    (go
      (loop [online? true]
        (let [[v c] (async/alts! [query-chan online?-chan])]
          (condp = c
            online?-chan (recur v)
            query-chan (do
                         (let [[client query] v
                               _ (<! (<transact! client query))]
                           (when online?
                             (let [other-clients (remove (partial = client) clients)
                                   ;; Sync
                                   callbacks (map #(<transact! % (om/full-query (om/app-root %)))
                                                  other-clients)
                                   _ (run! #(async/<!! %) callbacks)
                                   eq? (apply = (map app-state clients))]
                               (assert eq? "App state was not equal.")
                               (debug "App state was equal! :D: " eq?))))
                         (recur online?))))))))

(defn- take-with-timeout [chan label & [timeout-millis]]
  {:pre [(string? label)]}
  (let [[v c] (async/alts!! [chan (async/timeout (or timeout-millis 5000))])]
    (when-not (= c chan)
      (throw (ex-info "client login timed out" {:where (str "awaiting " label)})))
    v))

(defn log-in! [client email-chan callback-chan]
  (debug "Transacting signin/email")
  (om/transact! client `[(session.signin/email ~{:input-email datomic-dev/test-user-email
                                                 :device :jvm})])
  (take-with-timeout callback-chan "email transact!")
  (debug "Transacted signin/email")
  (debug "Awaiting email")
  (let [v (take-with-timeout email-chan "email with verification")]
    (debug "Awaited email")
    (let [_ (debug "client recieved from email chan: " v)
          {:keys [:verification/uuid :verification/value]} (:verification v)
          _ (backend/drain-channel callback-chan)
          _ (om/transact! client `[(session.signin.email/verify ~{:verify-uuid (str uuid)})])
          _ (take-with-timeout callback-chan "verification")]
      (debug "Awaited verification: " v)
      (debug "hopefully logged in now?"))))

(defn logged-in-client [server-url email-chan callback-chan]
  (let [endpoint-atom (atom (str server-url "/api"))
        did-merge-fn (fn [client] (async/put! callback-chan client))
        client (create-client endpoint-atom did-merge-fn)
        _ (take-with-timeout callback-chan "initial merge")
        _ (log-in! client email-chan callback-chan)
        _ (reset! endpoint-atom (str server-url "/api/user"))]
    client))

(defn run []
  (let [email-chan (async/chan)
        server (core/start-server-for-tests {:email-chan email-chan})

        callback-chan (async/chan)
        server-url (str "http://localhost:" (.getPort (.getURI server)))
        _ (debug "CREATING USER 1...")
        client1 (logged-in-client server-url email-chan callback-chan)
        _ (debug "DONE USER 1.")
        _ (debug "CREATING USER 2...")
        client2 (logged-in-client server-url email-chan callback-chan)
        _ (debug "DONE USER 2.")
        query-chan (async/chan)
        online?-chan (async/chan)
        clients [client1 client2]
        client->callback (into {} (map #(vector % (async/chan 10))) clients)
        _ (go
            (while true
              (let [client (<! callback-chan)]
                (>! (client->callback client) client))))
        _ (mutation-state-machine query-chan online?-chan clients client->callback)]
    (async/put! query-chan [client1 [(om/get-query JvmRoot)]])
    (async/put! query-chan [client1 [(om/get-query JvmRoot)]])))



(comment `[(transaction/create {:transaction/tags       ({:tag/name "thailand"}),
                                :transaction/date       {:date/ymd "2015-10-10"}
                                :transaction/type       {:db/ident :transaction.type/expense},
                                :transaction/currency   {:currency/code "THB"},
                                :transaction/title      "lunch",
                                :transaction/project    [:project/uuid #uuid"57eeb170-4d6f-462c-b1e4-0b70c136924f"],
                                :transaction/uuid       #uuid"57eeb170-fc13-4f2d-b0e7-d36a624ab6d1",
                                :transaction/amount     180M,
                                :transaction/created-at 1})])
