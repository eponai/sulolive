(ns eponai.common.parser.util
  (:refer-clojure :exclude [proxy])
  (:require [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [debug error]]
            [om.next :as om]
            [om.next.impl.parser :as om.parser]))

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

;; TODO, make a special function for caching our transactions.
;; (That's the only thing we use this for anyway).
(defn cache-last-read
  "Takes a read function caches its last call if data
  arguments are equal. (e.g. params, :db, :target and :query)."
  [f]
  (let [last-call (atom nil)]
    (fn [& [env k params :as args]]
      (let [[[last-env _ last-params] last-ret] @last-call
            equal-key? (fn [k] (= (get env k)
                                  (get last-env k)))]
        (let [ret (if (and (identical? (:db env) (:db last-env))
                           (= params last-params)
                           (every? equal-key? [:query :target]))
                    (do
                      (debug (str "Returning cached for:" k))
                      last-ret)
                    ;; Cache miss, call the function.
                    (f (assoc env ::last-db (:db last-env))
                       k params))]
          ;; Always reset the last call args, to possibly
          ;; hit true on cljs.core/identical? for the
          ;; equality checks.
          (reset! last-call [args ret])
          ret)))))

(defn put-db-id-in-query [query]
  (cond (map? query)
        (reduce-kv (fn [q k v]
                     (assoc q k (put-db-id-in-query v)))
                   {}
                   query)

        (sequential? query)
        (->> query
             (remove #(= :db/id %))
             (map put-db-id-in-query)
             (cons :db/id)
             (into []))

        :else
        query))

(defn proxy [{:keys [parser query target ast] :as env} k _]
  (let [ret (parser env query target)]
    (if target
      (when (seq ret)
        {target (assoc ast :query ret)})
      {:value ret})))


(defn union-query [{:keys [query] :as env} k p union-key]
  (let [route-query (cond-> query
                            ;; Union. Query can also be passed with the
                            ;; selected union.
                            (map? query) (get union-key))]
    (assert (some? route-query)
            (str "Route-query must not be nil. No union-key: " union-key
                 " in query: " query
                 " resulting in route-query: " route-query))
    (proxy (assoc env :query route-query) k p)))

(defn return
  "Special read key (special like :proxy) that just returns
  whatever it is bound to.

  Example:
  om/IQuery
  (query [this] ['(:return/foo {:value 1 :remote true})])
  Will always return 1 and be remote true."
  [_ _ p]
  p)