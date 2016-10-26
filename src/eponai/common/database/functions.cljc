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

;; TODO: This is now a db.fn.
;; TODO: Use the dbfn as in a transaction
;; TODO: Use this fn when we format/edit client side.
;; TODO: Use the dbfn when we should edit-attr
;; TODO: Some how skip should-edit-attr on the client side.

;; TODO: Possibly specify dependency fns in def-dbfn, where you
;;       can depend on other functions created by def-dbfn.


;; TODO: Build this in to the macro instead. DONE.
;; TODO: What to do about client though?
;; TODO: Write cardinality-many?-datascript and ref?-datascript
;; TODO: Bind them to cardinality-many? and ref? vars. Run. AWAY!

(def ^:dynamic q nil)
(def ^:dynamic datoms nil)
(def ^:dynamic tempid nil)
(def ^:dynamic cardinality-many? nil)
(def ^:dynamic ref? nil)
(def ^:dynamic unique-datom nil)

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

(def-dbfn edit-attr
  {:requires ['[datomic.api :as datomic :refer [q datoms tempid]]]
   :deps     [{:dbfn cardinality-many?-datomic :provides 'cardinality-many? :memoized? true}
              {:dbfn ref?-datomic :provides 'ref? :memoized? true}
              {:dbfn unique-datom-datomic :provides 'unique-datom}]}
  [db created-at entity attr value]
  (letfn [(tempid? [id]
            (not (number? id)))
          (has-been-edited-since? [db created-at entity attr & [value]]
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

          (something-new [new-value]
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
                                        (seq (dissoc x :db/id))
                                        (conj (assoc x :db/id id))
                                        :always
                                        (conj [:db/add entity attr id])))))
                        (do (assert (not (coll? x)))
                            (when (edit-val? x)
                              [[:db/add entity attr x]]))))]
              (if (or (sequential? new-value) (set? new-value))
                (into [] (mapcat x->txs) new-value)
                (x->txs new-value))))
          (something-old [old-value]
            (letfn [(x->txs [x]
                      (if (map? x)
                        (do (assert (ref? db attr)
                                    (str "Attribute was not a ref but it got a map as value."
                                         " Attr was: " attr))
                            (let [id (or (:db/id x)
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
              (if (or (sequential? old-value) (set? old-value))
                (into [] (mapcat x->txs) old-value)
                (x->txs old-value))))]

    (when (edit-attr? db created-at entity attr)
      (let [{:keys [old-value new-value]} value
            ret (condp = [(some? old-value) (some? new-value)]
                  [false false] []
                  [false true] (something-new new-value)
                  [true false] (something-old old-value)
                  [true true] (if (cardinality-many? db attr)
                                (-> []
                                    (into (something-old old-value))
                                    (into (something-new new-value)))
                                ;; cardinality-one. New value would just replace the old.
                                ;; ..right?
                                (something-new new-value)))]
        ret))))
