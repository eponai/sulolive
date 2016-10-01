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
            [clj-http.client :as http]
            [clojure.core.async :as async]
            [taoensso.timbre :refer [debug]]))

(om/defui JvmRoot
  static om/IQuery
  (query [this] [:datascript/schema {:query/transactions [:transaction/title]}])
  Object
  (render [this]
    (dom/div nil
      (dom/p nil (str "Schema keys: " (keys (:datascript/schema (om/props this)))))
      (dom/p nil "First 10 transaction titles:")
      (apply dom/div nil (map #(dom/p nil (:transaction/title %))
                              (:query/transactions (om/props this)))))))

(defmulti jvm-client-merge om/dispatch)

(defn create-client [server did-merge-fn]
  {:pre [(.isRunning server)]}
  (let [server-url (str "http://localhost:" (.getPort (.getURI server)) "/api")
        conn (utils/create-conn)
        reconciler-atom (atom nil)
        reconciler (om/reconciler {:state   conn
                                   :parser  (parser/client-parser)
                                   :send    (backend/send! reconciler-atom
                                                           {:remote (-> (remotes/post-to-url server-url)
                                                                        (remotes/read-basis-t-remote-middleware conn))}
                                                           did-merge-fn)
                                   :merge   (merge/merge! jvm-client-merge)
                                   :migrate nil})
        _ (reset! reconciler-atom reconciler)
        c (om/add-root! reconciler JvmRoot nil)]
    (prn "c: " c)
    (prn "app-root: " (om/app-root reconciler))
    reconciler))

(defn- app-state [reconciler]
  @(om/app-state reconciler))

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
                                   _ (backend/drain-channel (async/merge callbacks))
                                   eq? (apply = (map app-state clients))]
                               (assert eq? "App state was not equal.")
                               (debug "App state was equal! :D: " eq?))))
                         (recur online?))))))))

(defn run []
  (let [server (core/start-server-for-tests)
        callback-chan (async/chan)
        callback-fn (fn [client] (async/put! callback-chan client))
        client->callback (let [state (atom {})]
                           (go
                             (while true
                               (let [client (<! callback-chan)]
                                 (swap! state update client (fnil #(async/>!! % client) (async/chan 1))))))
                           (fn [client]
                             (swap! state update client (fnil identity (async/chan 1)))
                             (get @state client)))
        client1 (create-client server callback-fn)
        client2 (create-client server callback-fn)
        query-chan (async/chan)
        online?-chan (async/chan)
        clients [client1 client2]
        _ (mutation-state-machine query-chan online?-chan clients client->callback)]
    (async/put! query-chan
                [client1 `[(transaction/create {:transaction/tags       ({:tag/name "thailand"}),
                                                :transaction/date       {:date/ymd       "2015-10-10"}
                                                :transaction/type       {:db/ident :transaction.type/expense},
                                                :transaction/currency   {:currency/code "THB"},
                                                :transaction/title      "lunch",
                                                :transaction/project    [:project/uuid #uuid"57eeb170-4d6f-462c-b1e4-0b70c136924f"],
                                                :transaction/uuid       #uuid"57eeb170-fc13-4f2d-b0e7-d36a624ab6d1",
                                                :transaction/amount     180M,
                                                :transaction/created-at 1})]])))
