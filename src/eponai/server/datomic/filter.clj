(ns eponai.server.datomic.filter
  "Filter maps can be combined, incrementally updated and
  applied as either AND or OR filters.

  A filter map is defined as:
  {:f filter-function
   :props map-of-values-which-describe-a-filter}.

  The filter function takes keys from :props as input
  and returns a datomic filter function (fn [db datom]).

  Each key in props contain:
  {:init initial-value
  :update-fn (fn [db old-val] return new-val)}
  The purpose of the :props is to describe a filter to be incrementaly
  updated.

  It sounds more complicated than it is..?
  Hopefully overtime, we figure out an easier way to describe
  incrementally updatable database filters."
  (:require [clojure.set :as s]
            [datomic.api :as d]
            [taoensso.timbre :refer [debug]]))

;; Updating and applying filters

(defn update-props
  "Preforms an incremental update on the props of a filter-map.

  By adding the last time we update the props onto the map, we
  can quickly avoid computing new filters for the database."
  [db {:keys [::basis-t] :as props}]
  {:pre [(map? props)]}
  (let [basis-t (or basis-t -1)
        new-basis-t (d/basis-t db)]
    (if (= basis-t new-basis-t)
      props
      (let [db-since (d/since db basis-t)]
        (let [updated-props (reduce-kv (fn [props key {:keys [init update-fn value]}]
                                         (when-not update-fn
                                           (prn "key:" key))
                                         (let [old-val (or value init)]
                                           (assoc-in props [key :value] (update-fn db-since old-val))))
                                       {}
                                       (dissoc props ::basis-t))]
          (assoc updated-props ::basis-t new-basis-t))))))

(defn update-filters
  "Updating filters with db."
  [db filters]
  {:pre [(sequential? filters)]}
  (mapv #(assoc % :props (update-props db (:props %))) filters))

(defn- extract-prop-values
  "Takes a filter-map's props and assoc's the value of each key onto the keys."
  [props]
  {:pre [(map? props)]}
  (reduce-kv (fn [p k v] (assoc p k (:value v)))
             {}
             props))

(defn apply-filters
  "Applies filters with AND to a db."
  [db filters]
  {:pre [(sequential? filters)]}
  (reduce (fn [db {:keys [f props]}]
            (d/filter db (f (extract-prop-values props))))
          db
          filters))

(defn or-filter
  "Combines a filter-map such that the filter passes if either of the filters pass."
  [& filter-maps]
  ;; No filter has the same props keys.
  {:pre [(empty? (apply s/intersection (->> filter-maps (map :props) (map keys) (map set))))]}
  (let []
    (reduce (fn [filter1 filter2]
              {:props (merge (:props filter1) (:props filter2))
               :f     (fn [props]
                        (let [f1 ((:f filter1) props)
                              f2 ((:f filter2) props)]
                          (fn [db datom]
                            (or (f1 db datom)
                                (f2 db datom)))))})
            (first filter-maps)
            (rest filter-maps))))

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

(defn user-attributes [db]
  ;;TODO: Add more user entities? We want to filter out anything that has to do with sensitive
  ;;      user data. Such as what they have bought.
  (let [user-entity-keys [:budget/uuid :transaction/uuid :dashboard/uuid]]
    (d/q '{:find  [[?a ...]]
           :in    [$ [?user-entity-key ...]]
           :where [[?e ?user-entity-key]
                   [?e ?a]]}
         db
         user-entity-keys)))

(defn- user-entities-filter-map [user-id]
  {:props {:owned-entities {:init      #{}
                            :update-fn (fn [db old-val]
                                         (into old-val (user-entities db user-id)))}}
   :f     (fn [{:keys [owned-entities]}]
            {:pre [(set? owned-entities)]}
            (fn [_ [eid]]
              (contains? owned-entities eid)))})

(defn- public-entities-filter-map []
  {:props {:user-attrs {:init      #{}
                        :update-fn (fn [db old-val]
                                     (into old-val (user-attributes db)))}}
   :f     (fn [{:keys [user-attrs]}]
            {:pre [(set? user-attrs)]}
            (fn [_ [_ attr]]
              (not (contains? user-attrs attr))))})

(defn authenticated-db-filters
  "When authenticated, we can access entities specific to one user
  or public entities."
  [user-id]
  [(or-filter (user-entities-filter-map user-id)
              (public-entities-filter-map))])

(defn not-authenticated-db-filters []
  [(public-entities-filter-map)])
