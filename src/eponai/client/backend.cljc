(ns eponai.client.backend
  (:require [eponai.common.database :as db]
            [eponai.client.utils :as client.utils]
            [eponai.common.parser.util :as parser.util]
            [eponai.common.ui.loading-bar :as loading-bar]
            [datascript.impl.entity :as e]
            [om.next :as om]
            [cognitect.transit :as transit]
            [datascript.core :as d]
            [clojure.set :as set]
            [clojure.walk :as walk]
            [datascript.transit]
            [taoensso.timbre :as timbre :refer [debug error trace warn info]]
    #?@(:clj
        [
            [clojure.core.async :as async :refer [go <! >! chan timeout]]
            [clj-http.client :as http]
            [clojure.edn :as reader]]
        :cljs
        [[cljs.core.async :as async :refer [<! >! chan timeout]]
         [cljs-http.client :as http]
         [cljs.reader :as reader]])
            [eponai.common.datascript :as common.datascript])
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go]]))
  #?(:clj (:import [datascript.impl.entity Entity]
                   [java.util UUID]
                   [eponai.common.parser.util GraphReadAtBasisT]))
  #?(:clj (:refer-clojure :exclude [send])))

(def max-retry-time-ms 2000)

(def DatascriptEntityAsMap (transit/write-handler (constantly "map")
                                                  (fn [v] (into {} v))))

(def GraphReadAtBasisTAsMap (transit/write-handler (constantly "map")
                                                   (fn [v] (into {} v))))

;; TODO: Create a new namespace for eponai.client.http ?

#?(:clj
   (defn to-cljs-http-response
     "Normalize http responses to match that of cljs-http.

     See implementation of an xhr request:
     https://github.com/r0man/cljs-http/blob/master/src/cljs_http/core.cljs"
     [send-fn url params]
     {:pre [(fn? send-fn)]}
     (try
       (let [{:keys [status body] :as response} (send-fn url params)
             success? (<= 200 status 299)]
         (cond-> response
                 success?
                 (assoc :success success?)
                 ;; clj-http doesn't parse the body to transit for some reason.
                 (and (string? body) (not (empty? body)))
                 (-> (update :body #(java.io.ByteArrayInputStream. (.getBytes %)))
                     (#(http/coerce-transit-body params % :json)))))
       (catch java.net.ConnectException e
         (debug "Unable to connect to host. Exception: " e
                " Returning offline status.")
         {:success false
          :error-code :offline
          :exception e})
       (catch Throwable e
         (warn "Unknown error: " e " message: " (.getMessage e))
         {:success false
          :error-code :exception
          :exception e}))))

(defn send [send-fn url opts]
  (let [transit-opts {:transit-opts
                      {:encoding-opts
                       {:handlers {#?(:clj Entity :cljs e/Entity)                                 DatascriptEntityAsMap
                                   #?(:clj GraphReadAtBasisT :cljs parser.util/GraphReadAtBasisT) GraphReadAtBasisTAsMap}}
                       :decoding-opts
                       ;; favor ClojureScript UUIDs instead of Transit UUIDs
                       ;; https://github.com/cognitect/transit-cljs/pull/10
                       {:handlers (merge
                                    {"u" (transit/read-handler #?(:clj  #(UUID/fromString %)
                                                                  :cljs uuid))
                                     "n" (transit/read-handler reader/read-string)
                                     "f" (transit/read-handler reader/read-string)}
                                    datascript.transit/read-handlers)}}}
        ;; http-clj/cljs has different apis to their http.client/post method.
        #?@(:clj  [params (-> transit-opts

                              (update :transit-opts set/rename-keys {:encoding-opts :encode
                                                                     :decoding-opts :decode})
                              ;; TODO: rename transit-params in the remotes to something else?
                              (assoc :form-params (:transit-params opts)
                                     :content-type :transit+json
                                     :cookie-store (:cookie-store opts)))]
            :cljs [params (-> (merge opts transit-opts)
                              )])]
    ;; http-cljs returns a channel with the response on it.
    ;; http-clj doesnt.
    (try #?(:cljs (debug "Send request: " params))
         #?(:cljs (send-fn url params)
            :clj  (go (to-cljs-http-response send-fn url params)))
         (catch #?@(:clj [Throwable e] :cljs [:default e])
                (error "Error sending request: " e)
           #?(:cljs (error "Error stack: " (.-stack e)))
           (throw e)))))

(comment
  ;;TODO: Put in production, later.
  (assoc-in [:headers "X-CSRF-Token"] (.. js/document (getElementById "__anti-forgery-token") -value)))

(defn- <send [remote->send remote-key query]
  (go
    (try
      (loop [retry-time-ms 500]
        (let [{:keys  [method url opts response-fn post-merge-fn shutting-down?
                       redirect-fn]
               ::keys [skip?]
               :as reified-remote}
              ((get remote->send remote-key) query)]
          (if skip?
            (do (debug "Skipping send of query: " query)
                {::skip? true})
            (do
              #?(:cljs (debug "Sending to " " url: " url " remote: " remote-key " query: " query
                              "method: " method "opts: " opts))
              (let [{:keys [success body status headers error-code] :as response}
                   (response-fn (cond
                                  shutting-down?
                                  {:success false}
                                  :else
                                  (<! (send (condp = method
                                              :get http/get
                                              :post http/post
                                              (throw (ex-info (str "unknown send method: " method)
                                                              {:method     method
                                                               :remote     reified-remote
                                                               :remote-key remote-key
                                                               :query      query})))
                                            url opts))))]
               (cond
                 (true? success)
                 (do
                   #?(:cljs (debug "Recieved response from remote:" body "status:" status))
                   {:response      body
                    :post-merge-fn post-merge-fn})
                 (= :offline error-code)
                 (do
                   (<! (timeout retry-time-ms))
                   (recur (min max-retry-time-ms (* 2 retry-time-ms))))

                 ;; Redirect
                 (and (number? status) (<= 300 status 399))
                 (do
                   (debug "Redirect. What to do?")
                   (if redirect-fn
                     (do
                       (debug "Calling redirect function: " redirect-fn
                              " with response: " response)
                       (redirect-fn response))
                     (do
                       (debug "No redirect-fn to handle redirect: " response
                              ". Doing nothing.")
                       {:response      nil
                        :post-merge-fn post-merge-fn})))
                 :else
                 (throw (ex-info "Not 2xx response remote."
                                 {:remote remote-key
                                  :status status
                                  :url    url
                                  :body   body
                                  :query  query}))))))))
      (catch :default e
        ;; TODO: Do something about errors.
        ;; We'll know what to do once we start with messages?
        {:error e}))))



(defn- query-history-id [query]
  (let [id-parser (om/parser {:read   (constantly nil)
                              :mutate (fn [_ _ {:keys [::mutation-db-history-id]}]
                                        {:action (constantly mutation-db-history-id)})})
        parsed-query (id-parser {} query nil)
        history-id (->> parsed-query
                        (sequence (comp (filter #(symbol? (key %)))
                                        (map (comp :result val))
                                        (filter some?)))
                        (first))]
    (when (and (nil? history-id)
               (some #(symbol? (key %)) parsed-query))
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

(defn chunk-response
  "Given the novelty returned by the server, return a seq transactables that can be
  split up and transacted little by little, every frame until all of it has been
  transacted."
  [novelty]
  nil)

;; TODO: Un-hard-code this. Put it in the app-state or something.
(def KEY-TO-RERENDER-UI :routing/app-root)

;; TODO: Copied from om internals
(defn to-env [r]
  {:pre [(om/reconciler? r)]}
  (-> (:config r)
      (select-keys [:state :shared :parser :logger :pathopt])
      ;; Adds :reconciler as it is also passed in om.next's
      ;; environment when parsing in om.next/transact*
      (assoc :reconciler r)))

(defn get-parser [r]
  {:pre [(om/reconciler? r)]
   :post [(fn? %)]}
  (-> r :config :parser))

;; JEEB

(defn- only-reads? [history-id]
  (nil? history-id))

(defn- flatten-db [db]
  (client.utils/clear-queue db))

(defn- flatten-db-up-to [db history-id is-remote-fn]
  (if (only-reads? history-id)
    db
    (client.utils/keep-mutations-after db history-id is-remote-fn)))`

(defn- mutations-after [db history-id is-remote-fn]
  ;; returns all mutations for reads.
  (if (only-reads? history-id)
    (client.utils/mutation-queue db)
    (client.utils/mutations-after db history-id is-remote-fn)))

(defn- make-is-remote-fn [reconciler remote-key]
  (let [parser (get-parser reconciler)
        env (to-env reconciler)]
    (fn [mutation]
      ;; If the parser returns something
      ;; with the remote-key as target with the mutation,
      ;; then the mutation is remote.
      (let [parsed (parser env [mutation] remote-key)]
        (some? (seq parsed))))))

(defn update-query-ids
  "Update :db/id's in the query from the db when the query was created to :db/id's in
  the new-db."
  [query db-when-query-was-created new-db]
  ;; mutation may have been updated in the mutation queue.
  (let [unique-attributes (delay (into #{}
                                       (comp (filter #(contains? (val %) :db/unique))
                                             (map key))
                                       (:schema new-db)))
        old-id->new-id (fn [old-db id]
                         (cond
                           ;; Entity hasn't changed between old and new db.
                           ;; return the same eid.
                           (common.datascript/entity-equal? old-db new-db id)
                           id
                           (not (common.datascript/has-id? old-db id))
                           id
                           :else
                           (let [old-entity (d/entity old-db id)
                                 unique-attrs (into [] (comp (filter @unique-attributes)
                                                             (map (juxt identity #(get old-entity %))))
                                                    (keys old-entity))
                                 new-eid (db/one-with new-db
                                                        {:where   '[[?e ?attr ?val]]
                                                         :symbols {'[[?attr ?val] ...] unique-attrs}})]
                             (if (nil? new-eid)
                               (warn "Unable to find a new eid for id: " id
                                     " entity: " (into {} old-entity)
                                     ". Returning the old id")
                               (debug "Found new id: " new-eid " for id: " id))
                             (or new-eid id))))
        target :alter-ids
        swap-ids (fn [{:keys [ast mutation-db]} k params]
                   (let [old-id->new-id (partial old-id->new-id mutation-db)]
                     {target (cond-> ast
                                     (seq params)
                                     (assoc :params
                                            (walk/postwalk (fn [x]
                                                             (cond-> x
                                                                     (and (vector? x)
                                                                          (= 2 (count x))
                                                                          (= :db/id (first x)))
                                                                     (update 1 old-id->new-id)))
                                                           params)))}))
        parser (om/parser {:read   swap-ids
                           :mutate swap-ids})
        ret (parser {:mutation-db db-when-query-was-created} query target)]
    ret))

(defn update-mutations [mutation-queue new-db]
  (client.utils/map-mutations mutation-queue
                              (fn [db mutation]
                                (first (update-query-ids [mutation] db new-db)))))

(defn- apply-and-queue-mutations
  [reconciler stable-db mutation-queue is-remote-fn history-id]
  ;; Trim mutation queue to only contain mutations after history-id
  (let [mutation-queue (client.utils/keep-mutations-after mutation-queue
                                                          history-id
                                                          is-remote-fn)
        mutation-queue (update-mutations mutation-queue stable-db)
        mutations-after-query (client.utils/mutations mutation-queue)
        _ (when (seq mutations-after-query)
            (debug "Applying mutations: " mutations-after-query))
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

(defn- merge-response! [cb stable-db received history-id]
  (let [{:keys [response error] ::keys [skip?]} received
        {:keys [result meta auth]} response]
    ;; Reset db and apply response
    (cond
      skip?
      (cb {:db stable-db})

      (some? error)
      (do
        (debug "Error: " error)
        (cb {:db stable-db}))

      :else
      (cb {:db         stable-db
           :result     result
           :meta       meta
           :auth       auth
           :history-id history-id}))))

(defn- <stream-chunked-response
  "Stream chunked responses. Return the new stable-db and the current mutation-queue."
  [reconciler cb stable-db mutation-queue received is-remote-fn history-id]
  {:pre [(om/reconciler? reconciler)
         (fn? cb)
         (d/db? stable-db)
         (satisfies? client.utils/IQueueMutations mutation-queue)
         (map? received)
         (fn? is-remote-fn)]}
  (let [{:keys [response error] ::keys [skip?]} received
        app-state (om/app-state reconciler)]
    (go
      (when (and (nil? error) (not skip?))
        (when-let [chunked-txs (seq (chunk-response response))]
          (loop [stable-db stable-db mutation-queue mutation-queue txs chunked-txs]
            (let [[head tail] (split-at 50 txs)]
              ;; Merge the head.
              (cb {:db     stable-db
                   :result {KEY-TO-RERENDER-UI {:just/transact head}}})
              ;; Get the new stable app-state.
              (let [new-stable-db (d/db app-state)]
                (if (empty? (seq tail))
                  ;; There's nothing more to stream, so we can exit the loop
                  ;; in an app state where we need to merge the mutation queue
                  ;; (which was the state we entered this go-block in).
                  {:new-stable-db  new-stable-db
                   :mutation-queue mutation-queue}
                  ;; There's more to stream:
                  ;; Apply mutation queue and stream the rest.
                  (let [db-with-mutations (apply-and-queue-mutations reconciler
                                                                     new-stable-db
                                                                     mutation-queue
                                                                     is-remote-fn
                                                                     history-id)
                        _ (cb {:db db-with-mutations})
                        ;; Apply pending mutations locally only.
                        ;; Timeout to allow for re-render.
                        _ (<! (timeout 0))
                        ;; there may be new mutations in the app state. Get them.
                        mutation-queue (d/db app-state)]
                    (recur new-stable-db mutation-queue tail)))))))))))

(defn- hide-loading-bar [reconciler]
  (try
    (some-> (get-in reconciler [:config :shared :shared/loading-bar])
            (loading-bar/stop-loading! reconciler))
    (catch #?@(:cljs [:default e]
               :clj  [Throwable e])
      (error "Error hiding the loading bar: " e))))

(defn leeb
  "Takes a channel with remote queries and handles the resetting of
  optimistic mutations, merging of real responses and replays
  of local optimistic mutations.
  This function basically does a \"rebase\" of the app state
  everytime we get a \"real\" response from an optimistic mutation,
  replacing the optimistic mutation with the real response.
  If there was no optimistic mutation or if the query is only reads,
  we'll place the real response before any of the pending optimistic
  mutations.

  All optimistic and local mutations is stored in the app state,
  and the app state follows the client.utils/IQueueMutations
  protocol.

  Good luck maintaining this!
  - Petter

  Named after Lee Byron who talked about this approach in his talk
  url: https://twitter.com/petterik_/status/735864074659995648"
  [reconciler-atom query-chan did-merge-fn]
  (go
    (while true
      (try
        (let [{:keys [remote->send cb remote-key query]} (<! query-chan)
              reconciler @reconciler-atom
              app-state  (om/app-state reconciler)
              history-id (query-history-id query)
              stable-db  (db-before-mutation reconciler history-id)]
          (loop [stable-db stable-db
                 query query
                 remote-key remote-key
                 history-id history-id]
            (let [is-remote-fn (make-is-remote-fn reconciler remote-key)

                  ;; Make mutations that came before before history-id
                  ;; permanent by removing them from the queue.
                  ;; They have already been applied.
                  stable-db (flatten-db-up-to stable-db history-id is-remote-fn)

                  ;; The stable-db is the db we want to use when we
                  ;; merge the response. We can get pending mutations
                  ;; from app-state after this call.
                  received (<! (<send remote->send remote-key query))

                  ;; Get all pending queries that has happened while
                  ;; we were waiting for response
                  mutation-queue (d/db app-state)
                  _ (when-let [mq (seq (client.utils/mutations-after mutation-queue
                                                                     history-id
                                                                     is-remote-fn))]
                      #?(:cljs
                         (debug "Pending mutations for query: " query
                                "queue: " (mapv #(cond-> % (map? %) (dissoc :db)) mq))))
                  _ (merge-response! cb stable-db received history-id)
                  ;; app-state has now been changed and is the new stable db.
                  stable-db (d/db app-state)

                  ;; We "stream" responses that are too big, letting the UI re-render
                  ;; for better UX.
                  ;; The streaming is async so and we may get new mutations
                  ;; so the streaming will need to return the new mutation queue
                  ;; as well as the new stable db.
                  ;; If streaming does nothing, we'll use the current stable-db
                  ;; and the current mutation-queue.
                  {:keys [new-stable-db mutation-queue]
                   :or   {new-stable-db stable-db mutation-queue mutation-queue}}
                  (<! (<stream-chunked-response reconciler
                                                cb
                                                stable-db
                                                mutation-queue
                                                received
                                                is-remote-fn
                                                history-id))

                  ;; Reset has been done. Apply mutations after history-id
                  db-with-mutations (apply-and-queue-mutations reconciler
                                                               new-stable-db
                                                               mutation-queue
                                                               is-remote-fn
                                                               history-id)]

              ;; Set the current db to the db with mutations.
              (cb {:db db-with-mutations})

              ;; We've now merged everything. Call the post-merge fn.
              ;; It might mutate app-state, but it should be fine.
              (when-let [f (:post-merge-fn received)]
                (debug "Calling post-merge-fn for remote key: " remote-key " query: " query)
                (f))

              ;; If there's another query in the query-channel, use the
              ;; new-stable-db as the "rebase" point.
              ;; otherwise, exit the loop.
              (when-let [{:keys [query query-db remote-key]} (async/poll! query-chan)]
                (recur new-stable-db
                       ;; Update query with new db ids.
                       (update-query-ids query query-db (d/db app-state))
                       remote-key
                       (query-history-id query)))))

          ;; Outside the loop.
          ;; No more remote queries right now. Flatten the db
          ;; making all queued mutations permanent.
          (cb {:db (flatten-db (d/db app-state))}))


        (catch #?@(:cljs [:default e]
                   :clj  [Throwable e])
               (debug "Error in query loop: " e ". Will recur with the next query.")
          (error e))
        (finally
          (hide-loading-bar @reconciler-atom)
          (when did-merge-fn
            (did-merge-fn @reconciler-atom)))))))

(defn send!
  [reconciler-atom remote-config & [{:keys [did-merge-fn query-fn will-send-fn]}]]
  {:pre [(map? remote-config)]}
  (let [query-chan (async/chan 10000)
        ;; Make leeb listen to the query-chan:
        ;; TODO: Make it possible to exit the leeb go block (by closing query-chan?)
        _ (leeb reconciler-atom query-chan did-merge-fn)]
    (fn [queries cb]
      (when (some (comp seq val) queries)
        (let [r @reconciler-atom]
          (some-> (get-in r [:config :shared :shared/loading-bar])
                  (loading-bar/start-loading! r))
          (when (some? will-send-fn)
            (will-send-fn r))))
     (run! (fn [[remote query]]
             (when (seq query)
               (async/put! query-chan {:remote->send remote-config
                                       :cb           cb
                                       :query        (cond-> query (some? query-fn) (query-fn remote))
                                       :remote-key   remote
                                       :query-db     (db-before-mutation @reconciler-atom
                                                                         (query-history-id query))})))
           queries))))
