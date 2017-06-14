(ns eponai.common.parser.util
  (:refer-clojure :exclude [proxy])
  (:require [taoensso.timbre :refer [debug error]]
            [om.next :as om]
            [cognitect.transit :as transit]))

(defn get-time []
  #?(:clj (. System (currentTimeMillis))
     :cljs (system-time)))

#?(:clj
   (defmacro timeit [label & body]
     `(let [start# (get-time)
            ret# (do ~@body)]
        (debug "Elapsed time: " (double (- (get-time) start#)) " msecs" " <= for: " ~label)
        ret#)))

(defn post-process-parse
  "Calls post-parse-fn for each [k v] in the result of a (parser env query-expr).
  The post-parse-fn should be a 3 arity function taking [env key value].
  The return of the post-parse-fn will be assoced to the key k. The key can also be removed
  from the parsed result by returning :dissoc from the post-parse-fn."
  ([post-parse-fn] (post-process-parse post-parse-fn []))
  ([post-parse-fn keys-to-process-first]
   (fn
     ([env parsed-result]
      (letfn [(post-parse-subset [result subset]
                (reduce-kv (fn [m k v]
                             (let [ret (post-parse-fn env k v)]
                               (cond
                                 (or (nil? ret)  (identical? ret v)) m
                                 (= ret :dissoc) (dissoc m k)
                                 ;; For proxies: Merge the returned value into the resulting map
                                 ;;              Using merge-with merge incase keys are matching
                                 ;;              in the end result.
                                 (= ret :merge)  (merge-with merge
                                                             (assoc m k {})
                                                             (post-parse-subset v v))
                                 ;; For proxies: Call post-parse-fn for the values returned.
                                 ;; Also dissoc if there's no result left.
                                 (= ret :call)   (let [r (post-parse-subset v v)]
                                                   (if (and (coll? r) (empty? r))
                                                     (dissoc m k)
                                                     (assoc m k r)))
                                 :else (assoc m k ret))))
                           result
                           subset))]
        (reduce post-parse-subset
                parsed-result
                [(select-keys parsed-result keys-to-process-first)
                 (apply dissoc parsed-result keys-to-process-first)]))))))

(defn put-db-id-in-query [query]
  (cond (map? query)
        (reduce-kv (fn [q k v]
                     (assoc q k (put-db-id-in-query v)))
                   {}
                   query)

        (sequential? query)
        (into [:db/id]
              (comp (remove #(= :db/id %))
                    (map put-db-id-in-query))
              query)
        :else
        query))

(defn read-join [{:keys [parser query target ast] :as env} k _]
  (let [ret (parser env query target)]
    (if target
      (when (seq ret)
        {target (assoc ast :query ret)})
      {:value ret})))


(defn read-union [{:keys [query] :as env} k p union-key]
  (let [route-query (cond-> query
                            ;; Union. Query can also be passed with the
                            ;; selected union.
                            (map? query) (get union-key))]
    (assert (some? route-query)
            (str "Route-query must not be nil. No union-key: " union-key
                 " in query: " query
                 " resulting in route-query: " route-query))
    (read-join (assoc env :query route-query) k p)))

(defprotocol IReadAtBasisT
  (set-basis-t [this key basis-t params] "Sets basis-t for key and its params as [[k v]...]")
  (get-basis-t [this key params] "Gets basis-t for key and its params as [[k v]...]")
  (has-basis-t? [this key])
  ;; TODO: Write a set-all function based on key-param-set.
  ;; (key-param-set [this])
  )

;; Graph implementation:

(defn- find-basis-t [{:keys [node values] :as graph} node-values]
  (when (some? node)
    (let [node-val (get node-values node :not-found)]
      (cond
        (= :basis-t node)
        graph

        (empty? values)
        nil

        (= :not-found node-val)
        (if (= 1 (count values))
          (recur (val (first values)) node-values)
          (throw (ex-info (str "Was not passed param value for node: " node
                               ", where multiple values could be matched.")
                          {:node   node
                           :params node-values
                           :values values})))

        :else
        (if-let [next-graph (get values node-val :not-in-values)]
          (when-not (= next-graph :not-in-values)
            (recur next-graph node-values))
          (throw (ex-info "Unknown graph state" {:graph graph :node-values node-values})))))))

(defrecord GraphReadAtBasisT [graph]
  IReadAtBasisT
  (set-basis-t [this key basis-t params]
    (assert (vector? params) (str "params have to be an ordered pair of kv-pairs when setting basis-t"))
    ;; Do some updates-in with non-tco recursion.
    ;; something like (fn self [] ... (assoc m k (self v (rest params))))
    (letfn [(update-in-graph [{:keys [node] :as graph} [[k v] :as params]]
              (cond
                (= node :basis-t)
                (if (empty? params)
                  (assoc graph :value basis-t)
                  (throw (ex-info "Unable to set additional keys for a path once it has been set."
                                  {:params params
                                   :key    key})))

                (empty? params)
                (assoc graph :node :basis-t
                             :value basis-t)

                :else
                (do (when (and (some? node) (not= node k))
                      (throw (ex-info "Set failed. Graphs node order was different from the passed params"
                                      {:key       key
                                       :basis-t   basis-t
                                       :params    params
                                       :graph     graph
                                       :current-k k
                                       :current-v v
                                       :node      node})))
                    (-> graph
                        (assoc :node k)
                        (update-in [:values v] update-in-graph (rest params))))))]
      (update-in this [:graph key] #(update-in-graph % params))))
  (get-basis-t [this key params]
    (:value (find-basis-t (get graph key) (into {} params))))
  (has-basis-t? [this key]
    (some? (get graph key))))

(defn graph-read-at-basis-t
  ([] (graph-read-at-basis-t {} false))
  ([graph from-wire?]
   (let [graph (cond-> graph
                       ;; When sending over wire, the record will be sent as a map
                       ;; and we can extract the graph from the map.
                       (and from-wire? (some? (:graph graph)))
                       :graph)]
     (->GraphReadAtBasisT (or graph {})))))

;; TODO: Base this off of protocol functions instead of implementation detail of the graph?
(defn merge-graphs [a b]
  (letfn [(deep-merge [a b]
            (when (or a b)
              (if-not (and a b)
                (or a b)
                (cond
                  (map? b)
                  (merge-with deep-merge a b)
                  (coll? b)
                  (into a b)
                  :else
                  b))))]
    (if (and a b)
      (->GraphReadAtBasisT (deep-merge (:graph a) (:graph b)))
      (or a b (graph-read-at-basis-t)))))


(defn focus-subquery
  "Takes a query and a path through joins in the query and returns the pattern
  of the last join in the query path.
  Example: (focus-subquery [{:chat/messages [{:chat.message/user [:user/email]}]}]
                           [:chat/messages :chat.message/user])
           ;;=> [:user/email]
  "
  [query query-path]
  (letfn [(subquery-for-key [query key]
            (let [query-parser (om/parser {:mutate (constantly nil)
                                           :read   (fn [env k p]
                                                     (when (= k key)
                                                       {:value (:query env)}))})]
              (-> (query-parser {} query)
                  (get key))))]
    (reduce subquery-for-key query query-path)))

(defn remove-query-key
  "Removes a key from a query. Single level, does not walk the query."
  ([k] (comp (remove k) (remove #(and (map? %) (some k (keys %))))))
  ([k query]
   (into [] (remove-query-key k) query)))
