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

(defn- user-or-public-entity-filter-map
  "Keeps a specific user's entities and public entities.
  Can be incrementally updated with the :update-props function."
  [user-id]
  {:props        {:owned-entities #{}
                  :user-attrs     #{}
                  :basis-t        -1}
   :update-props (fn [{:keys [basis-t] :as props} db]
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
                         (not (contains? user-attrs attr)))))})

(defn- private-attr-filter-map []
  {:props {:private-attrs #{:password/credential :verification/uuid}}
   :f     (fn [{:keys [private-attrs]}]
            (fn [db [eid]]
              (not (some private-attrs (keys (d/entity db eid))))))})

(defn- no-auth-filter-map []
  ;;TODO: Need to just return the map and apply filters with
  ;;      a vector, because filters need ordering.
  {:props {:user-attrs #{:transaction/uuid :budget/uuid}}
   :f     (fn [{:keys [user-attrs]}]
            (fn [db [eid]]
              (not (some user-attrs (keys (d/entity db eid))))))})

;; Updating and applying filters

(defn update-filters
  "Updating filters with db."
  [db filters]
  {:pre [(sequential? filters)]}
  (mapv (fn [{:keys [update-props] :as m}]
          (cond-> m
                  (some? update-props)
                  (update :props update-props db)))
        filters))

(defn apply-filters
  "Applies filters to a db."
  [db filters]
  {:pre [(sequential? filters)]}
  (reduce (fn [db {:keys [f props]}]
            (d/filter db (f props)))
          db
          filters))

(defn authenticated-db-filters [user-id]
  [(user-or-public-entity-filter-map user-id)
   (private-attr-filter-map)])

(defn not-authenticated-db-filters []
  [(no-auth-filter-map)])

;; Deprecated, using for testing.
(defn filter-db [db filters]
  (->> filters
       (update-filters db)
       (apply-filters db)))

(defn authenticated-db [db user-id]
  (filter-db db (authenticated-db-filters user-id)))

(defn not-authenticated-db [db]
  (filter-db db (not-authenticated-db-filters)))
