(ns eponai.fullstack.framework
  (:require [om.next :as om]
            [om.util]
            [clojure.core.async :as async :refer [go <! >!]]
            [eponai.common.parser.util :as p.util]
            [eponai.common.database.functions :as dbfn]
            [eponai.client.parser.mutate]
            [eponai.client.parser.read]
            [eponai.client.backend :as backend]
            [eponai.server.core :as core]
            [eponai.server.datomic-dev :as datomic-dev]
            [eponai.fullstack.jvmclient :as client :refer [JvmRoot]]
            [eponai.fullstack.utils :as fs.utils]
            [clojure.core.async :as async]
            [taoensso.timbre :refer [debug error warn info]]
            [medley.core :as medley]
            [datascript.core :as datascript]
            [datomic.api :as datomic]
            [taoensso.timbre :as timbre])
  (:import (org.eclipse.jetty.server Server ServerConnector Connector)))

(defn call-parser [reconciler query & [target]]
  {:pre [(some? query)]}
  (let [parser (backend/get-parser reconciler)
        env (backend/to-env reconciler)]
    (parser env query target)))

(defn- <transact! [client->callback client query]
  (om/transact! client query)
  (client->callback client))

(defn multiply-chan [chan n]
  (let [chan-mult (async/mult chan)
        readers (repeatedly n #(async/chan))]
    (doseq [r readers]
      (async/tap chan-mult r))
    readers))

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
       (mapv #(async/<!! (<transact! client->callback % (om/get-query JvmRoot))))))

(defn mutation-state-machine [server action-chan clients client->callback]
  (let [<transact! (partial <transact! client->callback)
        result (atom {::successes [] ::failures [] ::skips []})
        with-timeout! (fn [c label] (fs.utils/take-with-timeout c label 3000))]
    (go
      (try
        (loop [id 0]
          (let [action (<! action-chan)
                action (if (fn? action) (action) action)
                {:keys [::transaction ::asserts ::await-clients ::sync-clients!]} action]
            (when action
              (assert (every? #(= (namespace %) (namespace ::this-ns)) (keys action))
                      (str "Every key must be one of this namespace, otherwise there's a typo?"
                           " Keys in action: " (keys action)))
              ;; First wait for all previous actions to finish.
              ;; They might have timed out and now finished.
              ;; (run! (comp backend/drain-channel client->callback) clients)
              (let [[client query :as tx] (if (fn? transaction) (transaction) transaction)
                    remote-query? (when client
                                    (seq (call-parser client query :remote)))
                    remote-sync? (and remote-query? (.isRunning server))
                    test-result {::test-id id :query query :some-client? (some? client)}]
                (debug "Running test transaction: " tx)
                (try
                  ;; Transact query if there is one.
                  (when client
                    (let [query (cond-> (vec query)
                                        ;; Sync all data also if it's a remote query.
                                        remote-query?
                                        (into (om/get-query JvmRoot)))]
                      (debug "Client: " client " transacting query: " query)
                      (om/transact! client query)))

                  ;; Await current or previous actions.
                  (let [clients-to-await (cond-> (vec await-clients)
                                                 (and remote-sync?)
                                                 (conj client))]
                    (->> clients-to-await
                         (map (juxt identity client->callback))
                         (run! (fn [[client callback]]
                                 (debug "Awaiting client:" client)
                                 (with-timeout! callback (str "awaiting client: " client))
                                 (debug "Awaited client:" client)))))

                  ;; Sync other clients
                  (when (or remote-sync? sync-clients!)
                    (let [clients-to-sync (cond->> clients
                                                   (some? client)
                                                   (remove (partial = client)))]
                      (warn "Syncing clients: " (vec clients-to-sync))
                      (->> clients-to-sync
                           (map (juxt identity #(<transact! % (om/get-query (or (om/app-root %) JvmRoot)))))
                           (run! (fn [[client chan]]
                                   (debug "syncing client: " client)
                                   (with-timeout! chan (str "syncing client: " client))
                                   (debug "synced client: " client))))))

                  ;; Run asserts
                  (debug "Running asserts for " tx)
                  (when asserts
                    (asserts))
                  (swap! result update ::successes conj test-result)
                  (catch Throwable e
                    (swap! result update ::failures conj (assoc test-result :error e))))
                (recur (inc id))))))
        (catch Throwable e
          (.printStackTrace e)
          (error "Exception in state-machine: " e))
        (finally
          (async/<!! (async/go-loop []
                       (when-let [action (async/<! action-chan)]
                         (try
                           (let [{:keys [::transaction]} (if (fn? action) (action) action)]
                             (debug "Skipping transaction: " (second transaction)
                                    "because of previous error."))
                           (catch Throwable e
                             (debug "Exception " e " while skipping action: " action)))
                         (swap! result update ::skips (fnil conj []) action)
                         (recur))))))
      @result)))

(defn create-clients [n {:keys [server email-chan action-chan result-chan]}]
  (let [callback-chan (async/chan)
        teardown-atom (atom false)
        server-url (str "http://localhost:" (.getPort (.getURI ^Server server)))
        clients (map #(client/logged-in-client (inc %) server-url email-chan callback-chan teardown-atom)
                     (range n))
        client->callback (into {} (map #(vector % (async/chan 10))) clients)
        _ (go
            (loop [client (<! callback-chan)]
              (if (nil? client)
                (medley/map-vals async/close! client->callback)
                (do (>! (client->callback client) client)
                    (recur (<! callback-chan))))))

        clients (mapv #(client/update-callback-chan % (client->callback %)) clients)

        _ (init-clients-state clients client->callback)
        [sm-query-chan teardown-query-chan] (multiply-chan action-chan 2)
        sm (mutation-state-machine server sm-query-chan clients client->callback)
        _ (async/go
            (try
              (loop []
                (if (async/<! teardown-query-chan)
                  (recur)
                  ;; action-chan is closed. Tear down:
                  (let [result (async/<! sm)]
                    (reset! teardown-atom true)
                    (async/close! callback-chan)
                    ;; Ok to drain because everything is closed at this point.
                    (backend/drain-channel (async/merge (vals client->callback)))
                    (async/>! result-chan result))))
              (finally
                (async/close! result-chan))))]
    clients))

(defn- start-system [{:keys [db-transactions db-schema] :as system}
                     test-fn-meta]
  (let [conn (datomic-dev/create-new-inmemory-db)
        _ (datomic-dev/add-data-to-connection conn
                                              db-transactions
                                              db-schema)
        test-specific-server-config (:server test-fn-meta)
        _ (when (seq test-specific-server-config)
            (info "Running with test-specific-server-config: "
                  test-specific-server-config)
            true)
        email-chan (async/chan)
        server (core/start-server-for-tests
                 (-> {:conn                       conn
                      ;; Re-use the server's port
                      ;; to be more kind to the test system.
                      :port                       (:server-port system 0)
                      :email-chan                 email-chan
                      ;; Don't run anti-forgery checks for fullstack tests
                      ;; as it's hard to fake this? (we don't have html available
                      ;; during our tests).
                      ::core/disable-anti-forgery true}
                     (#(merge-with merge % test-specific-server-config))))
        ;; The server could be started with a random port.
        ;; Set the selected port to the server so it
        ;; keeps the same port between restarts
        port (.getPort (.getURI server))
        connector (doto (ServerConnector. server) (.setPort port))
        server (doto server
                 (.stop)
                 (.join)
                 (.setConnectors (into-array Connector [connector]))
                 (.start))]
    (assoc system :conn conn
                  :email-chan email-chan
                  :server server
                  :server-port port)))

(defn- stop-system [system]
  (async/close! (:email-chan system))
  (datomic/release (:conn system))
  (doto (:server system)
    (.stop)
    (.join))
  system)

(defn setup-system []
  {:results-atom    (atom [])
   :db-schema       (datomic-dev/read-schema-files)
   :db-transactions (datomic-dev/transaction-data 105)})

(defn run-test [{:keys [server] :as system} test-fn]
  (let [action-chan (async/chan)
        result-chan (async/chan)]
    (.start server)
    (try
      (let [clients (create-clients 2
                                    (assoc system :action-chan action-chan
                                                  :result-chan result-chan))
            {:keys [actions label]} (test-fn server clients)
            _ (try
                (doseq [action actions]
                  (async/put! action-chan action))
                (finally
                  (async/close! action-chan)))
            result (async/<!! result-chan)]
        (update system :results-atom swap! conj {:label  (or label (str (datascript/squuid)))
                                                 :result result}))
      (finally
        (.stop server)
        (.join server)))
    system))

(defn result-summary [results]
  (->> results
       (transduce (comp (map :result)
                        (map (juxt ::successes ::failures ::skips))
                        (map #(map count %)))
                  (completing #(map + % %2))
                  [0 0 0])
       (zipmap [:successes :failures :skips])))

(defn print-summary [end-result]
  (when-let [has-failures (seq (concat (filter (comp seq ::failures :result) end-result)
                                       (filter (comp seq ::skips :result) end-result)))]
    (error "Test failures and skips: "
           (->> has-failures
                (into [] (map #(update % :result dissoc ::successes))))))
  (info "Test results:" (result-summary end-result)))

(defn run-tests [test-fns]
  (let [system (reduce (fn [system test]
                         (try
                           (-> system
                               (start-system (:system (meta test)))
                               (run-test test)
                               (stop-system))
                           (catch Throwable e
                             (error "Uncaught error when running test: " test " error: " e
                                    " Stopping test.")
                             (reduced (stop-system system)))))
                       (setup-system)
                       test-fns)
        end-result (deref (:results-atom system))]
    (print-summary end-result)
    end-result))
