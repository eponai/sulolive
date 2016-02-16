(ns eponai.server.datomic.filter
  (:require [datomic.api :as d]))

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

;; Filter maps

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
  ;;TODO: Need to just return the map and apply filters with
  ;;      a vector, because filters need ordering.
  {:no-auth
   {:props {:user-attrs #{:transaction/uuid :budget/uuid}}
    :f     (fn [{:keys [user-attrs]}]
             (fn [db [eid]]
               (not (some user-attrs (keys (d/entity db eid))))))}})

(defn authenticated-db-map [user-id]
  (merge (private-attr-filter-map)
         (user-or-public-entity-filter-map user-id)))

(defn not-authenticated-db-map []
  (no-auth-filter-map))

;; Updating and applying filters

(defn update-filters [db filter-map]
  (reduce-kv (fn [m k {:keys [props update-props]}]
               (cond-> m
                       (some? update-props)
                       (assoc-in [k :props] (update-props db props))))
             filter-map
             filter-map))

(defn apply-filters [db filter-map]
  (reduce-kv (fn [db _ {:keys [f props]}]
               (d/filter db (f props)))
             db
             filter-map))


;; Deprecated, using for testing.
(defn filter-db [db filter-map]
  (->> filter-map
       (update-filters db)
       (apply-filters db)))

(defn authenticated-db [db user-id]
  (filter-db db (authenticated-db-map user-id)))

(defn not-authenticated-db [db]
  (filter-db db (not-authenticated-db-map)))
