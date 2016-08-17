(ns eponai.client.backend
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async :refer [<! >! chan timeout]]
            [eponai.common.parser.util :as parser.util]
            [eponai.common.parser :as parser]
            [cljs-http.client :as http]
            [datascript.impl.entity :as e]
            [om.next :as om]
            [cognitect.transit :as transit]
            [taoensso.timbre :refer-macros [debug error trace warn]]
            [datascript.core :as d]))


(def DatascriptEntityAsMap (transit/write-handler (constantly "map")
                                                  (fn [v] (into {} v))))

(defn- send [send-fn url opts]
  (let [transit-opts {:transit-opts
                      {:encoding-opts {:handlers {e/Entity DatascriptEntityAsMap}}
                       :decoding-opts
                       ;; favor ClojureScript UUIDs instead of Transit UUIDs
                       ;; https://github.com/cognitect/transit-cljs/pull/10
                                      {:handlers {"u" uuid
                                                  "n" cljs.reader/read-string
                                                  "f" cljs.reader/read-string}}}}]
    (send-fn url (merge opts transit-opts))))


(defn <send [remote->send remote-key query]
  (go
    (try
      (let [{:keys [method url opts response-fn post-merge-fn]}
            ((get remote->send remote-key) query)
            _ (debug "Sending to " remote-key " query: " query
                     "method: " method " url: " url "opts: " opts)
            {:keys [body status headers]}
            (response-fn (<! (send (condp = method
                                     :get http/get
                                     :post http/post)
                                   url opts)))]
        (cond
          (<= 200 status 299)
          (do
            (debug "Recieved response from remote:" body "status:" status)
            {:response body
             :post-merge-fn post-merge-fn})
          :else
          (throw (ex-info "Not 2xx response remote."
                          {:remote remote-key
                           :status status
                           :url    url
                           :body   body
                           :query  query}))))
      (catch :default e
        {:error e}))))


(def query-parser (om/parser {:read   (constantly nil)
                              :mutate (fn [_ _ {:keys [::mutation-db-history-id]}]
                                        {:action (constantly mutation-db-history-id)})}))

(defn- db-before-mutation [reconciler query remote-key]
  (let [parsed-query (query-parser {} query remote-key)
        history-id (->> parsed-query
                        (sequence (comp (filter #(symbol? (key %)))
                                        (map (comp ::mutation-db-history-id :result val))
                                        (filter some?)))
                        (first))]
    (when-not (or (some? history-id)
                  (not-any? #(symbol? (key %)) parsed-query))
      (warn
        (str "Could not find mutation-db-history-id in"
             " any of the query's mutations."
             " Query: " query)))
    (if history-id
      (om/from-history reconciler history-id)
      (d/db (om/app-state reconciler)))))

(defn drain-channel
  "Takes everything pending in the channel.
  Does not close it when it's been drained."
  [chan]
  (take-while some? (repeatedly #(async/poll! chan))))

(defn query-transactions->ds-txs [novelty]
  (let [{:keys [transactions conversions] :as all}
        (->> (:result novelty)
             (tree-seq map? vals)
             (filter #(and (map? %) (contains? % :query/transactions)))
             (map :query/transactions)
             ;; TODO: What should we do about query/transactions
             ;; occurring in multiple places?
             ;; It should always return the same thing now that we
             ;; don't filter anymore.
             (first))]
    ;; transact conversions before transactions
    ;; because transactions depend on conversions.
    (concat conversions (cond->> transactions
                                 (seq transactions)
                                 (sort-by #(get-in % [:transaction/date :date/timestamp]) >)))))

(defn transact-pending [reconciler pending-queries]
  ;; We could also merge the pending mutation in to a single mutation. Hmm.
  (doseq [mutation (map :query pending-queries)]
    (try
      (om/transact! reconciler mutation)
      (catch :default e
        (error "Error when re-applying mutation: " mutation
               " error: " e)))))

;; TODO: Un-hard-code this. Put it in the app-state or something.
(def KEY-TO-RERENDER-UI :routing/app-root)

(defn leeb [reconciler-atom query-chan]
  (go
    (while true
      (let [{:keys [remote->send cb remote-key query]} (<! query-chan)
            ;; To be used only after (<! (<send )) has been called.
            pending-queries (delay (drain-channel query-chan))
            reconciler @reconciler-atom]
        (try
          (let [db (db-before-mutation reconciler query remote-key)
                _ (assert (false? (realized? pending-queries))
                          (str "pending-queries was realized before calling"
                               "(<! (<send ...))"))
                received (<! (<send remote->send remote-key query))
                {:keys [response error post-merge-fn]} received]
            (if (nil? error)
              (let [{:keys [result meta]} response]
                (debug "Merging response: " response)
                ;;   -Success-> Call cb with old db + response.
                ;;         -??> Also need to replay the mutations that has
                ;;              happened in between.
                ;;         -!!> this is done in the finally block.
                (cb {:db     db
                     :result result
                     :meta   meta})
                (let [state (om/app-state reconciler)
                      chunked-txs (query-transactions->ds-txs response)]
                  ;; Loop for streaming/chunking large results
                  (when (seq chunked-txs)
                    (loop [db (d/db state) txs chunked-txs]
                      (if (empty? (seq txs))
                        ;; Call with db to undo the applied pending mutations
                        ;; as they will be applied in the finally block.
                        (cb {:db db})
                        (let [[head tail] (split-at 100 txs)]
                          (cb {:db     db
                               :result {KEY-TO-RERENDER-UI {:just/transact head}}})
                          (let [new-db (d/db state)]
                            ;; Apply pending mutations locally only.
                            (when (seq @pending-queries)
                              (binding [parser/*parser-allow-remote* false]
                                (transact-pending reconciler @pending-queries)))
                            ;; Timeout to allow for re-render.
                            (<! (timeout 0))
                            (recur new-db tail))))))))
              ;; On error:
              (do
                (debug "Error after sending query: " query " error: " error
                       ". Resetting state to the state before the optimistic mutation.")
                ;;  -Failure-> Call cb with db without response.
                (cb {:db db})))
            ;; call post-merge-fn after merge (if there is one).
            (when post-merge-fn
              (post-merge-fn)))
          (catch :default e
            (debug "Error in query loop: " e ". Will recur with the next query.")
            (error e))
          (finally
            ;; Now that we've reset the db to the db-before-mutation + server response
            ;; transact all the pending mutations again.
            ;; Note: It should be ok to do this without some db-with logic in merge
            ;;       with the pending mutations. It's alright because javascript
            ;;       only has one thread and om.next batches the updates.
            (transact-pending reconciler @pending-queries)))))))

(defn- send-query! [remote->send cb remote-key query]
  (let [query (parser.util/unwrap-proxies query)]
    (go
      (try
        (let [{:keys [method url opts response-fn post-merge-fn]} ((get remote->send remote-key) query)
              _ (debug "Sending to " remote-key " query: " query
                       "method: " method " url: " url "opts: " opts)
              {:keys [body status headers]}
              (response-fn (<! (send (condp = method
                                       :get http/get
                                       :post http/post)
                                     url opts)))]
          (cond
            (<= 200 status 299)
            (do
              (debug "Recieved response from remote:" body "status:" status)
              (let [txs (query-transactions->ds-txs body)]
                (cb body)
                (loop [txs txs]
                  (when (seq txs)
                    (<! (timeout 0))
                    (let [[head tail] (split-at 100 txs)]
                      (cb {:result {:routing/app-root {:just/transact head}}})
                      (recur tail))))
                (when post-merge-fn
                  (post-merge-fn))))

            :else
            (throw (ex-info "Not 2xx response remote."
                            {:remote remote-key
                             :status status
                             :url    url
                             :body   body
                             :query query
                             :TODO   "Handle HTTP errors better."}))))
        (catch :default e
          (error "Error when posting query to remote:" remote-key "error:" e)
          (throw e))))))

(defn send!
  [reconciler-atom remote->send]
  {:pre [(map? remote->send)]}
  (let [query-chan (async/chan 10000)
        _ (leeb reconciler-atom query-chan)]
    (fn [queries cb]
     (run! (fn [[key query]]
             (async/put! query-chan {:remote->send remote->send
                                     :cb           cb
                                     :query        (parser.util/unwrap-proxies query)
                                     :remote-key   key}))
           queries))))

;; Remote helpers

(defn post-to-url [url]
  (fn [query]
    {:method      :post
     :url         url
     :opts        {:transit-params {:query query}}
     :response-fn identity}))
