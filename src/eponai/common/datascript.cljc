(ns eponai.common.datascript
  (:require [clojure.walk :as w]))

(defn schema-datomic->datascript [datomic-schema]
  (-> (reduce (fn [datascript-s {:keys [db/ident db/valueType] :as datomic-s}]
            (assoc datascript-s ident
                   (-> (if (not= valueType :db.type/ref)
                         (dissoc datomic-s :db/valueType)
                         datomic-s)
                       (select-keys (disj (set (keys datomic-s))
                                          :db/id
                                          :db.install/_attribute
                                          :db/ident)))))
          {}
          datomic-schema)
      ;; add support for enums-like things, since datascript doesn't support
      ;; keyword->eid lookup.
      (assoc :db/ident {:db/unique :db.unique/identity})))

(defn id-generator
  "Returns a function which takes a number and returns a new, negative id.
   It'll always return the same id, given a number.
   The returned function is not thread safe."
  []
  (let [id-state (atom {:next-id -1 :ids {}})]
    (fn [i]
      (let [{:keys [next-id ids]} @id-state]
        (if (contains? ids i)
          (get ids i)
          (do
            ;; change this to be a compare-and-swap! to make it thread-safe.
            (swap! id-state #(-> %
                                 (update :ids assoc i next-id)
                                 (update :next-id dec)))
            next-id))))))

(defn db-id->temp-id
  "given a set of keys which have values of type db.type/ref, and any datastructure,
  return the same datastructure with {:db/id <id>} entries and {<ref-key> <id>} assigned
  to datascript temporary id's (negative integers)."
  [ref-keys forms]
  (let [kv?    #(and (vector? %) (= 2 (count %)) (keyword? (first %)))
        db-id? #(and (kv? %) (= :db/id (first %)))
        ref?   #(and (kv? %) (contains? ref-keys (first %)))
        id-gen (id-generator)]
    (w/postwalk (fn [x]
                  (cond
                    (db-id? x) (update x 1 id-gen)
                    (ref? x)   (let [[k v] x]
                                 (cond (number? v) [k (id-gen v)]
                                       (vector? v) [k (mapv id-gen v)]
                                       (map? v) x
                                       :else (throw (ex-info (str "unsupported reference value: " x)
                                                             {:when "postwalking reference in db-id->temp-id"
                                                              :error-value x}))))
                    :else x))
                forms)))

