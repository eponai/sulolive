(ns eponai.common.database.functions
  #?(:cljs (:require-macros [eponai.common.database.functions :refer [def-dbfn]]))
  (:require
    [datascript.core :as datascript]
    [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [debug info]]
    #?(:clj [datomic.api :as datomic])
    #?(:clj [taoensso.encore :refer [if-cljs]])))

#?(:clj
   (defn comp-dbfn [f1 f2 f2-name f2-memoized?]
     (assert (symbol? f2-name)
             "Provided name was not a symbol. Put a quote on the :provides symbol.")
     (format (str `(let [~f2-name (cond-> (fn ~(:params f2)
                                            ~(when-let [req# (seq (:requires f2))]
                                               `(do ~@(map (fn [req-form#]
                                                             `(require (quote ~req-form#))) req#)))
                                            ~(symbol "%s"))
                                          (or ~f2-memoized? false)
                                          (memoize))]
                     ~(symbol "%s")))
             (:code f2)
             (:code f1))))


#?(:clj
   (defn dbfn [f]
     (let [ret (-> f (meta) :dbfn)]
       (assert (some? ret) "Function passed to dbfn needs to be created with def-dbfn.")
       ret)))

#?(:clj
   (defmacro def-dbfn
     "Defines a database function and a function
     so that code can be both used in db functions
     and other contexts, such as cljs."
     [name opts params body]
     (letfn [(remove-quotes [requires]
               (into [] (map second) requires))]
       `(if-cljs
          (defn ~name ~params ~body)
          (let [step# (fn [dbfn# dep#]
                        (datomic/function {:code     (comp-dbfn dbfn#
                                                                (dbfn (:dbfn dep#))
                                                                (:provides dep#)
                                                                (:memoized? dep#))
                                           :params   (:params dbfn#)
                                           :lang     :clojure
                                           :requires (:requires dbfn#)}))
                dbfn# (reduce step# (datomic/function {:code     (quote ~body)
                                                       :params   (quote ~params)
                                                       :lang     :clojure
                                                       :requires (quote ~(remove-quotes (:requires opts [])))})
                              (:deps ~opts))]
            (def ~name ^{:dbfn dbfn#}
            (fn ~params
              ~body)))))))

(def ^:dynamic cardinality-many?)
(def ^:dynamic ref?)
(def ^:dynamic unique-datom)
(def ^:dynamic tempid?)
(def ^:dynamic update-edit)

(defn cardinality-many?-datascript [db attr]
  (= :db.cardinality/many
     (get-in (:schema db) [attr :db/cardinality])))

#?(:clj
   (def-dbfn cardinality-many?-datomic {:requires ['[datomic.api]]}
     [db attr]
     (some? (datomic.api/q '{:find  [?e .]
                 :in    [$ ?attr]
                 :where [[?e :db/ident ?attr]
                         [?e :db/cardinality :db.cardinality/many]]}
               db
               attr))))

(defn ref?-datascript [db attr]
  (= :db.type/ref
     (get-in (:schema db) [attr :db/valueType])))

#?(:clj
   (def-dbfn ref?-datomic {:requires ['[datomic.api]]}
     [db attr]
     (some? (datomic.api/q '{:find  [?e .]
                 :in    [$ ?attr]
                 :where [[?e :db/ident ?attr]
                         [?e :db/valueType :db.type/ref]]}
               db
               attr))))

(defn unique-datom-datascript [db kv-pairs]
  (let [schema (:schema db)
        unique-keys (filter (fn [[k _]] (some? (:db/unique (get schema k))))
                            kv-pairs)]
    (->> (datascript/q '{:find  [?entity ?attr ?val]
                         :in    [$ [[?attr ?val] ...]]
                         :where [[?entity ?attr ?val]]}
                       db
                       unique-keys)
         (first)
         (zipmap [:e :a :v]))))

#?(:clj
   (def-dbfn unique-datom-datomic {:requires ['[datomic.api]]}
     [db kv-pairs]
     (->> (datomic.api/q '{:find  [?entity ?attr ?val]
               :in    [$ [[?attr ?val] ...]]
               :where [[?e :db/ident ?attr]
                       [?e :db/unique _]
                       [?entity ?attr ?val]]}
             db
             kv-pairs)
          (first)
          (zipmap [:e :a :v]))))

#?(:clj
   (def-dbfn tempid?-datomic {:requires ['[datomic.api]]}
     [x]
     (let [tempid-type (type (datomic.api/tempid :db.part/user))]
       (= (type x) tempid-type))))

(defn tempid?-datascript [x]
  (or (and (number? x) (neg? x))
      #?(:clj (tempid?-datomic x))))

#?(:clj
   (def-dbfn update-edit-datomic {:requires ['[datomic.api]]}
     [db created-at entity attr value]
     (let [latest-datom (some->> (apply datomic.api/datoms (datomic.api/history db)
                                        :eavt entity attr (when value [value]))
                                 (sort-by :tx #(compare %2 %1))
                                 (first))
           tx-entity (some->> (:tx latest-datom)
                              (datomic.api/entity db))]
       (if-let [last-edit (or (:tx/mutation-updated-at tx-entity)
                              (:tx/mutation-created-at tx-entity))]
         (when (<= last-edit created-at)
           (if (and value (= value (:v latest-datom)))
             [:db/add (:db/id tx-entity) :tx/mutation-updated-at created-at]
             true))
         ;; There's no last edit, we should update this attr or value.
         true))))

(defn update-edit-datascript [db created-at entity attr value]
  true)

(def-dbfn edit-attr
  {:requires ['[datomic.api]]
   :deps     [{:dbfn cardinality-many?-datomic :provides 'cardinality-many? :memoized? true}
              {:dbfn ref?-datomic :provides 'ref? :memoized? true}
              {:dbfn unique-datom-datomic :provides 'unique-datom}
              {:dbfn tempid?-datomic :provides 'tempid?}
              {:dbfn update-edit-datomic :provides 'update-edit}]}
  [db created-at entity attr value]
  (letfn [(edit-attr? [db created-at entity attr]
            (if (cardinality-many? db attr)
              ;; cardinality many updates need to be validated by value.
              true
              (update-edit db created-at entity attr nil)))
          (update-value? [x]
            (update-edit db created-at entity attr x))

          (to-txs [db-fn value]
            (if (or (sequential? value) (set? value))
              (do
                (assert (cardinality-many? db attr)
                        (str "Value was sequential or set for a non-cardinality/many attr: " attr
                             " Can only set sequential values for cardinality/many attr. Value was: " value))
                (into [] (mapcat db-fn) value))
              (do (assert (not (cardinality-many? db attr))
                          (str "Value was ref or atomic but for a :db.cardinality/many attr: " attr
                               " value: " value))
                  (db-fn value))))

          (db-add [new-value]
            (letfn [(x->txs [x]
                      (if (map? x)
                        (do (assert (ref? db attr)
                                    (str "Attribute was not a ref but it got a map as value."
                                         " Attr was: " attr " ref fn: " ref?))
                            (let [dbid (or (:db/id x)
                                           #?(:clj (datomic.api/tempid :db.part/user)
                                              :cljs (datascript/tempid :db.part/user)))
                                  id (or (when (tempid? dbid)
                                           (:e (unique-datom db (seq (dissoc x :db/id)))))
                                         dbid)]
                              (when-let [updated (or (tempid? id) (update-value? id))]
                                (cond-> []
                                        ;; update-value? can return a transaction
                                        ;; which updates the :tx entity assoc'ed
                                        ;; with the latest value with a new created-at.
                                        (vector? updated)
                                        (conj updated)
                                        ;; It's a new ref, non-empty ref.
                                        ;; Add the whole ref to the transaction.
                                        (and (tempid? id) (seq (dissoc x :db/id)))
                                        (conj (assoc x :db/id id))
                                        :always
                                        (conj [:db/add entity attr id])))))
                        (do (assert (not (coll? x)))
                            (when-let [updated (update-value? x)]
                              (cond-> [[:db/add entity attr x]]
                                      (vector? updated)
                                      (conj updated))))))]
              (to-txs x->txs new-value)))

          (db-retract [old-value]
            (letfn [(x->txs [x]
                      (if (map? x)
                        (do (assert (ref? db attr)
                                    (str "Attribute was not a ref but it got a map as value."
                                         " Attr was: " attr))
                            (let [id (or (as-> (:db/id x) id
                                               (when (and id (not (tempid? id))) id))
                                         (:e (unique-datom db (seq (dissoc x :db/id)))))]
                              (if (number? id)
                                (when-let [updated (update-value? id)]
                                  (cond-> [[:db/retract entity attr id]]
                                          (vector? updated)
                                          (conj updated)))
                                (throw (ex-info (str "Could not find a number id for attr"
                                                     " of valueType ref with value: " x)
                                                {:value  x
                                                 :entity entity
                                                 :attr   attr})))))
                        (do (assert (not (coll? x)))
                            (when-let [updated (update-value? x)]
                              (cond-> [[:db/retract entity attr x]]
                                      (vector? updated)
                                      (conj updated))))))]
              (to-txs x->txs old-value)))]

    (let [ret (when (edit-attr? db created-at entity attr)
                (let [{:keys [old-value new-value]} value]
                  (condp = [(some? old-value) (some? new-value)]
                    [false false] []
                    [false true] (db-add new-value)
                    [true false] (db-retract old-value)
                    [true true] (if (cardinality-many? db attr)
                                  (-> []
                                      (into (db-retract old-value))
                                      (into (db-add new-value)))
                                  ;; cardinality-one. New value would just replace the old.
                                  ;; ..right?
                                  (db-add new-value)))))]

      ret)))
