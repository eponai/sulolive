(ns eponai.common.datascript
  (:require [datascript.btset :as btset]
            [datascript.arrays :as da]
            [datascript.core :as d]
            [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [error debug]]
            [clojure.data :as diff]))

(defn ui-schema
  "Additional schema we may use for our ui components"
  []
  {:ui/singleton {:db/unique :db.unique/identity}
   :ui.singleton.auth/user {:db/valueType :db.type/ref}
   :ui/component {:db/unique :db.unique/identity}
   :ui.component.cart/items {:db/cardinality :db.cardinality/many
                             :db/valueType :db.type/ref}
   :ui.store-chat-queue/store-id {:db/unique :db.unique/identity}
   :order/id {:db/unique :db.unique/identity}
   :country-spec/id {:db/unique :db.unique/identity}
   :country/code {:db/unique :db.unique/identity}
   :country/continent {:db/valueType :db.type/ref}
   :continent/code {:db/unique :db.unique/identity}
   :taxes/id {:db/unique :db.unique/identity}
   ;:store.item/uuid {:db/unique :db.unique/identity}
   :db/ident {:db/unique :db.unique/identity}
   ;; Datascript doesn't have an :vaet index, so to speed up
   ;; reverse search queryies, i.e. [[?e :some/thing "known"]]
   ;; we can index some attributes.
   :category/path {:db/index true}
   :category/name {:db/index true}
   :store.item/name {:db/index true}
   })

(defn schema-datomic->datascript [datomic-schema]
  (reduce (fn [datascript-s {:keys [db/ident db/valueType] :as datomic-s}]
            (assoc datascript-s ident
                                (-> (if (= valueType :db.type/ref)
                                      (-> datomic-s
                                          ;; Refs cannot be unique in datascript yet.
                                          ;; See: github.com/tonsky/datascript/issues/147
                                          (dissoc :db/unique)
                                          ;; There's no :vaet index for refs in datascript
                                          ;; so we index all refs to make queries fast
                                          ;; at the cost of size.
                                          (assoc :db/index true))
                                      ;; Refs are the only valueTypes we care about
                                      ;; so we dissoc the rest.
                                      (dissoc datomic-s :db/valueType))
                                    (select-keys (disj (set (cons :db/index (keys datomic-s)))
                                                       :db/doc
                                                       :db/id
                                                       :db.install/_attribute
                                                       :db/ident)))))
          {}
          datomic-schema))

(defn init-db [datomic-schema ui-data]
  (-> (merge-with merge
                  (schema-datomic->datascript datomic-schema)
                  (ui-schema))
      (d/create-conn)
      (d/db)
      (d/db-with ui-data)))

(defn pull-many
  "Experimental pull-many which has been much faster than the datascript one."
  [db query eids]
  (let [is-ref? (memoize (fn [k] (and (keyword? k) (= :db.type/ref (get-in (:schema db) [k :db/valueType])))))
        parse-query (memoize
                      (fn [query]
                        (let [query (mapv #(if (is-ref? %) {% [:db/id]} %) query)
                              {refs true others false} (group-by map? query)
                              ref-attrs (into #{} (map ffirst) refs)
                              ref-queries (into {} (map seq) refs)
                              other-attrs (set others)
                              ret {:ref-attrs   ref-attrs
                                   :ref-queries ref-queries
                                   :other-attrs other-attrs}]
                          ret)))
        cardinality-many? (memoize
                            (fn [attr] (= :db.cardinality/many (get-in (:schema db) [attr :db/cardinality]))))
        seen (atom {})
        eid->map (fn self [query eid]
                   (let [{:keys [ref-attrs ref-queries other-attrs]} (parse-query query)
                         m (reduce (fn [m {:keys [a v]}]
                                     (let [v (cond
                                               (contains? ref-attrs a)
                                               (or (get @seen v)
                                                   (self (get ref-queries a) v))
                                               (contains? other-attrs a)
                                               v
                                               :else nil)]
                                       (if (nil? v)
                                         m
                                         (if (cardinality-many? a)
                                           (update m a (fnil conj []) v)
                                           (assoc m a v)))))
                                   {:db/id eid}
                                   (d/datoms db :eavt eid))]
                     (swap! seen assoc eid m)
                     m))
        ret (into [] (map #(eid->map query %)) eids)]
    ret))

;; ----------------------
;; -- btset/Iter equallity

(defn- keys-eq? [a b]
  (or (identical? (.-keys a) (.-keys b))
      (let [achunk (btset/iter-chunk a)
            bchunk (btset/iter-chunk b)]
        (and (= (count achunk) (count bchunk))
             (every? #(= (nth achunk %)
                         (nth bchunk %))
                     (range (count achunk)))))))

(defn iter-equals?
  "Given two btset/Iter, return true if they iterate of the
  the identical items."
  [a b]
  (if (and (nil? a) (nil? b))
    true
    (when (and (some? a) (some? b) (keys-eq? a b))
      (recur (btset/iter-chunked-next a)
             (btset/iter-chunked-next b)))))

(defn entity-equal? [db1 db2 eid]
  (iter-equals? (d/datoms db1 :eavt eid)
                (d/datoms db2 :eavt eid)))

(defn attr-equal? [db1 db2 attr]
  (iter-equals? (d/datoms db1 :aevt attr)
                (d/datoms db2 :aevt attr)))

(defn has-id? [db id]
  (some? (first (d/datoms db :eavt id))))

(comment
  ;; Usage
  (require '[datascript.core :as d])

  (let [conn (d/create-conn {})
        _ (d/transact! conn [{:foo :bar}])
        db1 (d/db conn)
        _ (d/transact! conn [{:abc :xyz}])
        db2 (d/db conn)]
    ;; Fast check whether datoms are equal.
    ;; Will hit (identical? (.-keys iter) (.-keys iter2))
    ;; most of the time, comparing a bunch of items at
    ;; the same time.
    (assert (iter-equals? (d/datoms db1 :aevt :foo)
                          (d/datoms db2 :aevt :foo)))

    (assert (not (iter-equals? (d/datoms db1 :aevt :abc)
                               (d/datoms db2 :aevt :abc))))))