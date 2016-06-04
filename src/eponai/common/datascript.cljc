(ns eponai.common.datascript)

(defn ui-schema
  "Additional schema we may use for our ui components"
  []
  {:ui/singleton {:db/unique :db.unique/identity}
   :ui/component {:db/unique :db.unique/identity}
   :ui.component.transactions/selected-transaction {:db/valueType :db.type/ref}
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