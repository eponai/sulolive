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

  #?@(:cljs
      [ISeqable
       (-seq [this] (seq coll))

       IHash
       (-hash [p] (hash coll))

       IEquiv
       (-equiv [p o] (and (instance? PrefixList o) (= (.-coll p) (.-coll o))))

       IPrintWithWriter
       (-pr-writer [pl writer opts]
                   (-write writer (str "#eponai/PrefixList "))
                   (-write writer (str {:coll (.-coll pl) :depth (.-depth pl)
                                        :by (.-by pl) :lists (.-lists pl)})))]

      :clj
      [Object
       (hashCode [p] (hash coll))

       clojure.lang.Seqable
       (seq [_] (seq coll))

       clojure.lang.IPersistentCollection
       (equiv [p o] (and (instance? PrefixList o) (= (.coll p) (.coll o))))
       (empty [p] (PrefixList. [] 0 nil {}))
       (count [p] (count coll))
       (cons [p v] (PrefixList. (cons v (.coll p)) 0 (.by p) {}))]))

(extend-protocol IFilterPrefix
  nil
  (-walk-prefix [this prefix] nil)
  (-filter-prefix [this prefix] nil))

;; --- API ------------------------

(defn prepare-prefix [pl prefix]
  (-walk-prefix pl prefix))

(defn filter-prefix [pl prefix]
  (seq (-filter-prefix (-walk-prefix pl prefix) prefix)))

(defn prefix-list-by [f coll]
  (PrefixList. coll 0 f {}))

(defn prefix-list [coll]
  (prefix-list-by nil coll))


(defn prefix-list? [x]
  (instance? PrefixList x))

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
  #?@(:cljs [string
             (-is-key? [this] (= (.-length this) 1))]
      :clj  [String
             (-is-key? [this] (= (.length this) 1))])


  #?(:cljs Keyword :clj clojure.lang.Keyword)
  (-is-key? [this] (-is-key? (keyword->str this)))

  #?@(:clj [java.lang.Character (-is-key? [this] true)])

  #?(:cljs default :clj Object)
  (-is-key? [this] (and (number? this) (pos? this) (> 10 this))))

(extend-protocol IKeywordize
  #?(:cljs Keyword :clj clojure.lang.Keyword)
  (-keywordize [this] (seq (keyword->str this)))

  nil
  (-keywordize [this] nil)

  #?(:cljs default :clj Object)
  (-keywordize [this]
    (cond
      (number? this) (num->digits this)
      :else (seq this))))

(extend-protocol IFilterAt
  #?(:cljs string :clj String)
  (-filter-at [item depth key]
    (when (< depth #?(:clj (.length item) :cljs (.-length item)))
      (= (.charAt item depth)
         (cond-> key (string? key) (.charAt 0)))))

  #?(:cljs Keyword :clj clojure.lang.Keyword)
  (-filter-at [item depth key]
    (-filter-at (keyword->str item) depth (cond-> key (keyword? key) (keyword->str))))

  #?(:cljs default :clj Object)
  (-filter-at [item depth key]
    (when (number? item)
      (= (nth (num->digits item) depth nil) key))))

#?(:clj (defmethod print-method PrefixList [pl w]
          (.write w (str "#eponai/PrefixList "))
          (binding [*out* w]
            (pr {:coll (.coll pl) :depth (.depth pl) :by (.by pl) :lists (.lists pl)}))))


;; Bug:
;; (def d (deep-list ["foo"]))
;; (seq (get d :foo) ;=> ("foo").
;; Solution: wrap keywordized :foo in somthing?
;; But it's probably just fine.

;; TODO: Add conj (which would update DeepLists recursively)
