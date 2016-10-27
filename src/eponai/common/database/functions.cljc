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

(def ^:dynamic q)
(def ^:dynamic datoms)
(def ^:dynamic tempid)
(def ^:dynamic cardinality-many?)
(def ^:dynamic ref?)
(def ^:dynamic unique-datom)
(def ^:dynamic tempid?)

(defn cardinality-many?-datascript [db attr]
  (= :db.cardinality/many
     (get-in (:schema db) [attr :db/cardinality])))

#?(:clj
   (def-dbfn cardinality-many?-datomic {:requires ['[datomic.api :refer [q]]]}
     [db attr]
     (some? (q '{:find  [?e .]
                 :in    [$ ?attr]
                 :where [[?e :db/ident ?attr]
                         [?e :db/cardinality :db.cardinality/many]]}
               db
               attr))))

(defn ref?-datascript [db attr]
  (= :db.type/ref
     (get-in (:schema db) [attr :db/valueType])))

#?(:clj
   (def-dbfn ref?-datomic {:requires ['[datomic.api :refer [q]]]}
     [db attr]
     (some? (q '{:find  [?e .]
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
   (def-dbfn unique-datom-datomic {:requires ['[datomic.api :refer [q]]]}
     [db kv-pairs]
     (->> (q '{:find  [?entity ?attr ?val]
               :in    [$ [[?attr ?val] ...]]
               :where [[?e :db/ident ?attr]
                       [?e :db/unique _]
                       [?entity ?attr ?val]]}
             db
             kv-pairs)
          (first)
          (zipmap [:e :a :v]))))

#?(:clj
   (def-dbfn tempid?-datomic {:requires ['[datomic.api :refer [tempid]]]}
     [x]
     (let [tempid-type (type (tempid :db.part/user))]
       (= (type x) tempid-type))))

(defn tempid?-datascript [x]
  (and (number? x)
       (neg? x)))

(def-dbfn edit-attr
  {:requires ['[datomic.api :as datomic :refer [q datoms tempid]]]
   :deps     [{:dbfn cardinality-many?-datomic :provides 'cardinality-many? :memoized? true}
              {:dbfn ref?-datomic :provides 'ref? :memoized? true}
              {:dbfn unique-datom-datomic :provides 'unique-datom}
              {:dbfn tempid?-datomic :provides 'tempid?}]}
  [db created-at entity attr value]
  (letfn [(has-been-edited-since? [db created-at entity attr & [value]]
            (if (= ::client-edit created-at)
              ;; Escape for client edits where edits should always happen.
              false
              (do
                (if (cardinality-many? db attr)
                  (assert (some? value) (str "value was not passed for :db.cardinality/many attr: " attr))
                  (assert (nil? value) (str "value as passed for non :db.cardinality/many attr: " attr
                                            "value: " value)))
                (let [last-edit (some->> (apply datoms db :eavt entity attr (when value [value]))
                                         (sort-by :tx #(compare %2 %1))
                                         (first)
                                         :tx
                                         (q '{:find  [?last-edit .]
                                              :in    [$ ?tx]
                                              :where [[?tx :tx/mutation-created-at ?last-edit]]}
                                            db))]
                  (or (nil? last-edit)
                      (<= last-edit created-at))))))
          (edit-attr? [db created-at entity attr]
            (if (cardinality-many? db attr)
              true
              (has-been-edited-since? db created-at entity attr)))
          (edit-value? [db created-at entity attr value]
            (if (cardinality-many? db attr)
              (has-been-edited-since? db created-at entity attr value)
              true))
          ;; Version with shorter param list, looks better in code.
          (edit-val? [x]
            (edit-value? db created-at entity attr x))

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
                                           (tempid :db.part/user))
                                  id (if (tempid? dbid)
                                       (-> (unique-datom db (seq (dissoc x :db/id)))
                                           (:e dbid))
                                       dbid)]
                              (when (or (tempid? id) (edit-val? id))
                                (cond-> []
                                        ;; It's a new ref, non-empty ref.
                                        ;; Add the whole ref to the transaction.
                                        (and (tempid? id) (seq (dissoc x :db/id)))
                                        (conj (assoc x :db/id id))
                                        :always
                                        (conj [:db/add entity attr id])))))
                        (do (assert (not (coll? x)))
                            (when (edit-val? x)
                              [[:db/add entity attr x]]))))]
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
                                (when (edit-val? id)
                                  [[:db/retract entity attr id]])
                                (throw (ex-info (str "Could not find a number id for attr"
                                                     " of valueType ref with value: " x)
                                                {:value  x
                                                 :entity entity
                                                 :attr   attr})))))
                        (do (assert (not (coll? x)))
                            [[:db/retract entity attr x]])))]
              (to-txs x->txs old-value)))]

    (when (edit-attr? db created-at entity attr)
      (let [{:keys [old-value new-value]} value
            ret (condp = [(some? old-value) (some? new-value)]
                  [false false] []
                  [false true] (db-add new-value)
                  [true false] (db-retract old-value)
                  [true true] (if (cardinality-many? db attr)
                                (-> []
                                    (into (db-retract old-value))
                                    (into (db-add new-value)))
                                ;; cardinality-one. New value would just replace the old.
                                ;; ..right?
                                (db-add new-value)))]
        ret))))
