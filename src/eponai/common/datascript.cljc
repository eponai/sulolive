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
   :ui/component {:db/unique :db.unique/identity}
   :ui.singleton.auth/user {:db/valueType :db.type/ref}
   :db/ident {:db/unique :db.unique/identity}})

(defn schema-datomic->datascript [datomic-schema]
  (reduce (fn [datascript-s {:keys [db/ident db/valueType] :as datomic-s}]
            (assoc datascript-s ident
                                (-> (if (= valueType :db.type/ref)
                                      ;; Refs cannot be unique in datascript yet.
                                      ;; See: github.com/tonsky/datascript/issues/147
                                      (dissoc datomic-s :db/unique)
                                      ;; Refs are the only valueTypes we care about
                                      ;; so we dissoc the rest.
                                      (dissoc datomic-s :db/valueType))
                                    (select-keys (disj (set (keys datomic-s))
                                                       :db/doc
                                                       :db/id
                                                       :db.install/_attribute
                                                       :db/ident)))))
          {}
          datomic-schema))


;;TODO: This is now old? Remove this?
(defn mark-entity-txs
  "Returns transactions which makes the new datascript db contain a datom with e a v in the new db.

  The reason we're doing this is to notice when attributes have been retracted. There's no way in
  datascript to list datoms that have been retracted. By marking entities that may have retracted
  something, we can list these entities later and get their updated values. This is needed to keep
  caches of entities correct. See :query/transactions.

  Caution: do not use (mark-entity-txs [:unique-attr :val] :unique-attr :val) because
  :unique-attr :val will be retracted first and will not be found when doing the :db/add."
  [e a v]
  [[:db/retract e a v] [:db/add e a v]])

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