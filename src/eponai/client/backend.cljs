(ns eponai.client.backend
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async :refer [<! >! chan timeout]]
            [eponai.common.parser.util :as parser.util]
            [eponai.common.parser :as parser]
            [eponai.client.utils :as client.utils]
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


(def history-id-parser
  (om/parser {:read   (constantly nil)
              :mutate (fn [_ _ {:keys [::mutation-db-history-id]}]
                        {:action (constantly mutation-db-history-id)})}))

(defn query-history-id [query]
  (let [parsed-query (history-id-parser {} query nil)
        history-id (->> parsed-query
                        (sequence (comp (filter #(symbol? (key %)))
                                        (map (comp :result val))
                                        (filter some?)))
                        (first))]
    (when (and (nil? history-id)
               (or (some #(symbol? (key %)) parsed-query)
                   true))
      (warn
        (str "Could not find mutation-db-history-id in"
             " any of the query's mutations."
             " Query: " query " parsed-query: " parsed-query)))
    history-id))

(defn- db-before-mutation [reconciler history-id]
  (if history-id
    (om/from-history reconciler history-id)
    (d/db (om/app-state reconciler))))

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
    (concat conversions
            (cond->> transactions
                     (seq transactions)
                     (sort-by #(get-in % [:transaction/date :date/timestamp]) >)))))

(defn transact-pending [reconciler pending-queries]
  ;; We could also merge the pending mutation in to a single mutation. Hmm.
  (doseq [query (map :query pending-queries)]
    (try
      (om/transact! (om/app-root reconciler) query)
      (catch :default e
        (error "Error when re-applying mutation: " query
               " error: " e)))))

;; TODO: Un-hard-code this. Put it in the app-state or something.
(def KEY-TO-RERENDER-UI :routing/app-root)

;; TODO: Copied from om internals
(defn- to-env [r]
  {:pre [(om/reconciler? r)]}
  (-> (:config r)
      (select-keys [:state :shared :parser :logger :pathopt])
      ;; Adds :reconciler as it is also passed in om.next's
      ;; environment when parsing in om.next/transact*
      (assoc :reconciler r)))

(defn- get-parser [r]
  {:pre [(om/reconciler? r)]
   :post [(fn? %)]}
  (-> r :config :parser))

;; JEEB

(defn only-reads? [history-id]
  (nil? history-id))

(defn flatten-db [db]
  (client.utils/clear-queue db))

(defn flatten-db-up-to [db history-id]
  (if (only-reads? history-id)
    db
    (client.utils/keep-mutations-after db history-id)))`

(defn mutations-after [db history-id]
  ;; returns all mutations for reads.
  (if (only-reads? history-id)
    (client.utils/mutation-queue db)
    (client.utils/mutations-after db history-id)))

(defn apply-and-queue-mutations [reconciler stable-db mutation-queue history-id]
  ;; Trim mutation queue to only contain mutations after history-id
  (let [mutation-queue (client.utils/keep-mutations-after mutation-queue history-id)
        mutations-after-query (client.utils/mutations mutation-queue)
        _ (debug "Applying mutations: " mutations-after-query)
        ;; Mutate the stable-db with mutations that have happened after history-id
        conn (d/conn-from-db stable-db)
        parser (get-parser reconciler)
        env (to-env reconciler)
        target nil
        _ (parser (assoc env :state conn) mutations-after-query target)
        ;; Given the mutated db in conn, queue all mutations after history-id
        ;; so that we keep the queue around for the next remote request.
        db-after (->> (d/db conn)
                      (client.utils/clear-queue)
                      ;; copy to keep the id's the same.
                      (client.utils/copy-queue mutation-queue))]
    db-after))

(defn merge-response! [cb stable-db received]
  (let [{:keys [response error post-merge-fn]} received
        {:keys [result meta]} response]
    ;; Reset db and apply response
    (if (nil? error)
      (do (cb {:db     stable-db
               :result result
               :meta   meta}))
      (do
        (debug "Error ")
        (cb {:db stable-db})))
    (when post-merge-fn
      (post-merge-fn))))

(defn <stream-chunked-response
  "Stream chunked responses. Return the new stable-db and the current mutation-queue."
  [reconciler cb stable-db mutation-queue received history-id]
  (let [{:keys [response error]} received
        app-state (om/app-state reconciler)]
    (go
      (when (nil? error)
        (when-let [chunked-txs (seq (query-transactions->ds-txs response))]
          (loop [stable-db stable-db mutation-queue mutation-queue txs chunked-txs]
            (if (empty? (seq txs))
              ;; Returning the new stable-db and mutation queue.
              ;; The app state is currently one with db and it's mutations.
              ;; (not that the current state of the app-state matters).
              {:new-stable-db  stable-db
               :mutation-queue mutation-queue}
              (let [[head tail] (split-at 100 txs)]
                (cb {:db     stable-db
                     :result {KEY-TO-RERENDER-UI {:just/transact head}}})
                (let [new-stable-db (d/db app-state)
                      db-with-mutations (apply-and-queue-mutations reconciler
                                                                   new-stable-db
                                                                   mutation-queue
                                                                   history-id)
                      _ (cb {:db db-with-mutations})
                      ;; Apply pending mutations locally only.
                      ;; Timeout to allow for re-render.
                      _ (<! (timeout 0))
                      ;; there may be new mutations in the app state. Get them.
                      mutation-queue (d/db app-state)]
                  (recur new-stable-db mutation-queue tail))))))))))

(defn- current-mutations [mutation-queue history-id]
  (client.utils/mutations
    (client.utils/keep-mutations-after mutation-queue history-id)))

(defn jeeb [reconciler-atom query-chan]
  (go
    (while true
      (try
        (let [{:keys [remote->send cb remote-key query]} (<! query-chan)
              reconciler @reconciler-atom
              history-id (query-history-id query)
              app-state  (om/app-state reconciler)
              stable-db  (db-before-mutation reconciler history-id)
              stable-db  (cond-> stable-db
                                ;; Initially, we get the latest stable db. Stable db means
                                ;; that it has no optimistic transactions.
                                ;; If our query from query-chan is a read, it means there
                                ;; aren't any optimistic transactions, so we can clear the
                                ;; pending mutation queue.
                                ;; TODO: enable this optimization when
                                ;; we think everything works?
                                (only-reads? history-id)
                                (flatten-db))]

          (loop [stable-db stable-db
                 query query
                 remote-key remote-key
                 history-id history-id]
            ;; Make mutations that came before before history-id
            ;; permanent by removing them from the queue.
            ;; They have already been applied.
            (let [stable-db (flatten-db-up-to stable-db history-id)

                  ;; The stable-db is the db we want to use when we
                  ;; merge the response. We can get pending mutations
                  ;; from app-state after this call.
                  received (<! (<send remote->send remote-key query))

                  ;; Get all pending queries that have happened while
                  ;; we were waiting for response
                  ;; Note: Applying response may be async if we're streaming
                  ;; chunks. So we'll return the next stable-db and the mutations
                  ;; pending. Since it's async, the UI may have been used to apply
                  ;; more mutations.
                  ;; mutation-queue need to be saved before merging response.
                  mutation-queue (d/db app-state)
                  _ (debug "Mutation-queue before merging response: "
                           (current-mutations mutation-queue history-id))
                  _ (merge-response! cb stable-db received)
                  ;; app-state has now been changed and is the new stable db.
                  stable-db (d/db app-state)

                  {:keys [new-stable-db mutation-queue]
                   :or   {new-stable-db stable-db mutation-queue mutation-queue}}
                  (<! (<stream-chunked-response reconciler
                                                cb
                                                stable-db
                                                mutation-queue
                                                received
                                                history-id))

                  ;; Reset has been done. Apply mutations after history-id
                  db-with-mutations (apply-and-queue-mutations reconciler
                                                               new-stable-db
                                                               mutation-queue
                                                               history-id)]
              ;; Set the current db to the db with mutations.
              (cb {:db db-with-mutations})

              ;; THIS WAS COMPLICATED LOL. REBASING FTW.
              ;; Now if there's another query in the query-channel, use the
              ;; new-stable-db as the "rebase" point.
              ;; otherwise, exit the loop.
              (when-let [{:keys [query remote-key]} (async/poll! query-chan)]
                (recur new-stable-db
                       query
                       remote-key
                       (query-history-id query)))))

          ;; End loop
          ;; End with a final flatten.
          (cb {:db (flatten-db (d/db app-state))}))

        ;; TODO: Add more try catches.

        (catch :default e
          (debug "Error in query loop: " e ". Will recur with the next query.")
          (error e))))))

(defn leeb [reconciler-atom query-chan]
  (go
    (while true
      (let [{:keys [remote->send cb remote-key query]} (<! query-chan)
            ;; To be used only after (<! (<send )) has been called.
            pending-queries (delay (drain-channel query-chan))
            reconciler @reconciler-atom]
        (try
          (let [history-id (query-history-id query)
                db (db-before-mutation reconciler history-id)
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
        _ (jeeb reconciler-atom query-chan)]
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
