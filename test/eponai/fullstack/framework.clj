(ns eponai.fullstack.framework
  (:require [om.next :as om]
            [om.util]
            [clojure.core.async :as async :refer [go <! >!]]
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
            [datascript.core :as datascript])
  (:import (org.eclipse.jetty.server Server)))

;; Om utils

(defn call-parser [reconciler query & [target]]
  {:pre [(some? query)]}
  (let [parser (backend/get-parser reconciler)
        env (backend/to-env reconciler)]
    (parser env query target)))

(defn- <transact! [client->callback client query]
  (om/transact! client query)
  (client->callback client))

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

(defn mutation-state-machine [query-chan clients client->callback]
  (let [<transact! (partial <transact! client->callback)
        result (atom {::successes [] ::failures []})]
    (go
      (try
        (loop [id 0]
          (let [{:keys [::transaction ::asserts] :as action} (<! query-chan)]
            (when (some? action)
              (assert (some? transaction)
                      (str "Got an action, but it did not contain key: " ::transaction
                           (if (map? action)
                             (str " had keys: " (keys action))
                             (str " action was not a map. Was: " (type action))))))
            (when transaction
              (let [[client query] transaction
                    remote-query? (seq (call-parser client query :remote))
                    test-result {::test-id id :query query}]
                (if-not remote-query?
                  ;; Just apply query locally to a client:
                  (do
                    (warn "non-remote query: " query)
                    (om/transact! client query))
                  ;; Apply and wait for query to be merged with remote response.
                  ;; Add the full query to read after the mutations have been done
                  ;; to sync the first client.
                  (let [query (into (vec query) (om/get-query JvmRoot))
                        ;; Do the first one synchronously, to make sure it finished.
                        _ (async/<!! (<transact! client query))
                        other-clients (->> (remove (partial = client) clients)
                                           (map #(<transact! % (om/full-query (om/app-root %)))))]
                    ;; Sync
                    (run! #(async/<!! %) other-clients)))
                (try
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
                       (when-let [{:keys [::transaction]} (async/<! query-chan)]
                         (debug "Skipping transaction: " (second transaction)
                                "because of previous error.")
                         (recur))))))
      @result)))


(defn create-clients [n {:keys [server email-chan query-chan result-chan]}]
  (let [callback-chan (async/chan)
        server-url (str "http://localhost:" (.getPort (.getURI ^Server server)))
        clients (repeatedly n #(client/logged-in-client server-url email-chan callback-chan))
        client->callback (into {} (map #(vector % (async/chan 10))) clients)
        _ (go
            (loop [client (<! callback-chan)]
              (if (nil? client)
                (medley/map-vals async/close! client->callback)
                (do (>! (client->callback client) client)
                    (recur (<! callback-chan))))))

        _ (init-clients-state clients client->callback)
        [sm-query-chan teardown-query-chan] (fs.utils/multiply-chan query-chan 2)
        sm (mutation-state-machine sm-query-chan clients client->callback)
        _ (async/go
            (try
              (loop []
                (if (async/<! teardown-query-chan)
                  (recur)
                  ;; query-chan is closed. Tear down:
                  (let [result (fs.utils/take-with-timeout sm "state-machine" (* 60 1000))]
                    (async/close! callback-chan)
                    ;; Ok to drain because everything is closed at this point.
                    (backend/drain-channel (async/merge (vals client->callback)))
                    (async/>! result-chan result))))
              (finally
                (async/close! result-chan))))]
    clients))

(defn reset-system [system])

(defn setup-system []
  (let [conn (datomic-dev/create-connection {::datomic-dev/transaction-count 105})
        email-chan (async/chan)]
    {:conn         conn
     :email-chan   email-chan
     :results-atom (atom [])
     :server       (core/start-server-for-tests {:conn       conn
                                                 :email-chan email-chan})}))

(defn run-test [{:keys [server] :as system} test-fn]
  (let [query-chan (async/chan)
        result-chan (async/chan)]
    (.start server)
    (try
      (let [clients (create-clients 2 (assoc system :query-chan query-chan
                                                    :result-chan result-chan))
            {:keys [actions label]} (test-fn server clients)
            _ (try
                (doseq [action actions]
                  (async/put! query-chan action))
                (finally
                  (async/close! query-chan)))
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
                        (map (juxt ::successes ::failures))
                        (map #(map count %)))
                  (completing #(map + % %2))
                  [0 0])
       (zipmap [:successes :failures])))

(defn run-tests [& test-fns]
  (let [system (setup-system)
        system (reduce run-test system test-fns)
        end-result (deref (:results-atom system))]
    (when-let [has-failures (seq (filter (comp seq ::failures :result) end-result))]
      (error "Test failures: " (->> has-failures
                                    (into [] (map #(update % :result dissoc ::successes))))))
    (info "Test results:" (result-summary end-result))
    (prn "Test results:" (result-summary end-result))))
