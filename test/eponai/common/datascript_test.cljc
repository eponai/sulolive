(ns eponai.common.datascript_test
  (:require [eponai.common.datascript :as project.d]
            [datascript.core :as d]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop #?@(:cljs [:include-macros true])]
            [clojure.test.check.clojure-test #?(:clj  :refer
                                                :cljs :refer-macros) [defspec]]))

(defn gen-datomic-keyword 
  "generates a keywords which contains alphanumeric characters.
   Used to avoid keywords starting with underscore, like: :_ref or :foo/_bar"
  ([] (gen-datomic-keyword "not-a-key" nil))
  ([forbidden-key replacement-key]
   (gen/bind
     (gen/not-empty gen/string-alphanumeric)
     (fn [s]
       (gen/bind gen/keyword-ns
                 (fn [k]
                   (if (= k forbidden-key)
                     (gen/return replacement-key)
                     (gen/return (if-let [n (namespace k)]
                                   (keyword (str n "/" s))
                                   (keyword s))))))))))

(defn gen-datomic-schema []
  (gen/hash-map :db/id (gen/return "#db.id[:db.part/db]")
                :db.install/_attribute (gen/return :db.part/db)
                :db/ident (gen-datomic-keyword)
                :db/valueType (gen/elements [:db.type/string :db.type/uuid :db.type/ref])
                :db/cardinality (gen/elements [:db.cardinality/one :db.cardinality/many])
                :db/doc gen/string-ascii))

(defn datomic-schema->datascript-txs [datomic-schema]
  (->> datomic-schema 
       (map (fn [{:keys [db/ident db/valueType db/cardinality]}]
              (let [value (if (= valueType :db.type/ref) 1 "value")
                    value (if (= cardinality :db.cardinality/many) (repeat 3 value) value)]
                [ident value]))) 
       (group-by (fn [[k _]] (if-let [n (namespace k)] n k)))
       (reduce (fn [m [k pairs]] 
                 (assoc m k (->> pairs
                                 (map (partial apply hash-map)) 
                                 (reduce merge {})))) 
               {})
       (vals)))

(defspec datomic-schemas-can-be-translated-to-datascript
  10
  (prop/for-all [datomic-schema (gen/not-empty (gen/vector (gen-datomic-schema)))]
                (when-let [datascript-schema (project.d/schema-datomic->datascript
                                               datomic-schema)] 
                  (let [conn (d/create-conn datascript-schema)] 
                    ;; create an entity to have something to refer to
                    (d/transact conn [{:foo "bar"}]) 
                    (when (every? (fn [{:keys [db/ident]}] (contains? datascript-schema ident))
                                  datomic-schema)
                      (let [datascript-txs (datomic-schema->datascript-txs datomic-schema)
                            before-tx @conn
                            res (d/transact conn datascript-txs)]
                          (not= before-tx @conn)))))))



