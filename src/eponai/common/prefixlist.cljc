(ns eponai.common.prefixlist)

;; --- Protocols ---------

(defprotocol IKey
  (-is-key? [this]))

(defprotocol IKeywordize
  (-keywordize [this]))

(defprotocol IFilterAt
  (-filter-at [item depth key]))

(defprotocol IFilterPrefix
  (-walk-prefix [this prefix])
  (-filter-prefix [this prefix]))

;; --- PrefixList impl ----

(deftype PrefixList [coll depth by lists]
  IFilterPrefix
  (-walk-prefix [this prefix]
    (let [prefix (-keywordize prefix)
          key (first prefix)
          rest-prefix (rest prefix)]
      (cond
        (nil? key) this
        (not (-is-key? key)) (-walk-prefix this key)

        (contains? lists key)
        (PrefixList. coll depth by
                     (cond-> lists
                             (seq rest-prefix)
                             (update key #(-walk-prefix % rest-prefix))))

        :else
        (let [filtered (filter (fn [item]
                                 {:pre [(-is-key? key)]}
                                 (-filter-at (cond-> item by (by)) depth key))
                               coll)]
          (when (seq filtered)
            (PrefixList. coll depth by
                         (assoc lists key
                                      (cond-> (PrefixList. filtered (inc depth) by lists)
                                              (seq rest-prefix)
                                              (-walk-prefix rest-prefix)))))))))
  (-filter-prefix [this prefix]
    (let [prefix (-keywordize prefix)
          key (first prefix)]
      (cond
        (nil? key) this
        (not (-is-key? key)) (some-> (-filter-prefix this key)
                                     (-filter-prefix (rest prefix)))
        (contains? lists key) (-filter-prefix (get lists key) (rest prefix))
        ;; Not found:
        :else nil)))

  clojure.lang.IPersistentCollection
  (equiv [d o] (and (instance? PrefixList o) (= coll (.coll o))))
  (empty [d] (PrefixList. [] 0 nil {}))
  (count [d] (count coll))
  (cons [d v] (PrefixList. (cons v coll) depth by {}))

  clojure.lang.Seqable
  (seq [this] (seq coll)))

;; --- API ------------------------

(defn filter-prefix [pl prefix]
  (seq (-filter-prefix (-walk-prefix pl prefix) prefix)))

(defn prefix-list
  ([coll] (prefix-list coll nil))
  ([coll by] (PrefixList. coll 0 by {})))

;; --- Protocol extensions --------

;; TODO: Re-reverse this (can then return lazy-seq again).
(defn num->digits [num]
    (if (> 10 num)
        [num]
        (conj (num->digits (quot num 10)) (mod num 10))))

(defn keyword->str [k]
  (if-let [ns (namespace k)]
    (str ns "/" (name k))
    (name k)))

(extend-protocol IKey
  #?(:cljs js/String :clj String)
  (-is-key? [this] (= (count this) 1))

  #?(:cljs Keyword :clj clojure.lang.Keyword)
  (-is-key? [this] (-is-key? (keyword->str this)))

  #?@(:clj [java.lang.Character (-is-key? [this] true)])
  Object
  (-is-key? [this] (and (number? this) (pos? this) (> 10 this))))

(extend-protocol IKeywordize
  #?(:cljs Keyword :clj clojure.lang.Keyword)
  (-keywordize [this] (seq (keyword->str this)))

  nil
  (-keywordize [this] nil)

  Object
  (-keywordize [this]
    (cond
      (number? this) (num->digits this)
      :else (seq this))))

(extend-protocol IFilterAt
  #?(:cljs js/String :clj String)
  (-filter-at [item depth key]
    (when (< depth (count item))
      (= (.charAt item depth)
         (cond-> key (string? key) (.charAt 0)))))

  #?(:cljs Keyword :clj clojure.lang.Keyword)
  (-filter-at [item depth key]
    (-filter-at (keyword->str item) depth (cond-> key (keyword? key) (keyword->str))))

  Object
  (-filter-at [item depth key]
    (when (number? item)
      (= (nth (num->digits item) depth nil) key))))

#?(:clj (defmethod print-method PrefixList [dl w]
          (.write w (str "#eponai.DeepList "))
          (binding [*out* w]
            (pr [(.coll dl) (.lists dl) (.depth dl) (.by dl)]))))


;; Bug:
;; (def d (deep-list ["foo"]))
;; (seq (get d :foo) ;=> ("foo").
;; Solution: wrap keywordized :foo in somthing?
;; But it's probably just fine.

;; TODO: Make it work in cljs.
;; TODO: Add conj (which would update DeepLists recursively)