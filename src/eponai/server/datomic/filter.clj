(ns eponai.server.datomic.filter
  (:require [datomic.api :as d]))

(defn apply-filter [db filter-map]
  (reduce-kv (fn [db _ {:keys [f props update-props]}]
               (d/filter db (f (cond->> props
                                       (some? update-props)
                                       (update-props db)))))
             db
             filter-map))

;; TODO: Using :user/email right now. Should use :user/uuid when it's done.
(def user-owned-rule
  '[[(owner? ?user-id ?e)
     [?e :user/email ?user-id]]
    [(owner? ?user-id ?e)
     [?e ?ref-attr ?ref]
     (owner? ?user-id ?ref)]])

(defn user-entities [db user-id]
  (d/q '{:find  [[?e ...]]
         :in    [$ ?user-id %]
         :where [(owner? ?user-id ?e)]}
       db
       user-id
       user-owned-rule))

(defn user-attributes [db user-es]
  (d/q '{:find  [[?a ...]]
         :in    [$ [?e ...]]
         :where [[?e ?a]]}
       db
       user-es))

(defn- user-or-public-entity-filter-map [user-id]
  {:keep-user-or-public-entities
   {:props        {:owned-entities #{}
                   :user-attrs     #{}
                   :basis-t        -1}
    :update-props (fn [db {:keys [basis-t] :as props}]
                    (if (= basis-t (d/basis-t db))
                      props
                      (let [db-since (d/since db basis-t)
                            new-entities (user-entities db-since user-id)
                            new-attrs (user-attributes db-since new-entities)]
                        (-> props
                            (update :owned-entities into new-entities)
                            (update :user-attrs into new-attrs)
                            (assoc :basis-t (d/basis-t db))))))
    :f            (fn [{:keys [owned-entities user-attrs]}]
                    (fn [_ [eid attr]]
                      (or (contains? owned-entities eid)
                          (not (contains? user-attrs attr)))))}})

(defn- private-attr-filter-map []
  {:no-private-attrs
   {:props {:private-attrs #{:password/credential :verification/uuid}}
    :f     (fn [{:keys [private-attrs]}]
             (fn [db [eid]]
               (not (some private-attrs (keys (d/entity db eid))))))}})

(defn- no-auth-filter-map []
  {:no-auth
   {:props {:user-attrs #{:transaction/uuid :budget/uuid}}
    :f     (fn [{:keys [user-attrs]}]
             (fn [db [eid]]
               (not (some user-attrs (keys (d/entity db eid))))))}})

(defn- user-or-public-entity-filter [db user-id]
  (apply-filter db (user-or-public-entity-filter-map user-id)))

(defn- private-attr-filter [db]
  (apply-filter db (private-attr-filter-map)))

(defn- no-auth-filter [db]
  (apply-filter db (no-auth-filter-map)))

(defn authenticated-db [db user-id]
  (-> db
      (private-attr-filter)
      (user-or-public-entity-filter user-id)))

(defn not-authenticated-db [db]
  (-> db
      (no-auth-filter)))
