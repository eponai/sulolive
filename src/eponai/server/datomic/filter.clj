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

(defn- user-owned-filter [user-id db]
  (let [owned-entities (set (user-entities db user-id))]
    (fn [_ [eid]]
      (contains? owned-entities eid))))

(defn- private-attr-filter []
  (let [private-attrs #{:password/credential :verification/uuid}]
    (fn [db [eid]]
     (not (some private-attrs (keys (d/entity db eid)))))))

(defn- public-attr-filter [db]
  (let [public-attrs #{:conversion/date :currency/code :date/ymd :tag/name}]
    (fn [db [eid]]
     (some public-attrs (keys (d/entity db eid))))))

(defn or-filter
  "Filters a db on filters returned by the filter-fns.
  Each filter-fn returns a filter when it is passed a database.
  The or-filter returns true once a single filter returns true
  using (reduced true)."
  [db & filter-fns]
  (let [filters (map (fn [f] (f db)) filter-fns)]
    (d/filter db (fn [db e]
                  (reduce (fn [bool filter]
                            (if (filter db e)
                              (reduced true)
                              bool))
                          false
                          filters)))))

(defn user-db [db user-id]
  (-> db
      (d/filter (private-attr-filter))
      (or-filter (partial user-owned-filter user-id)
                 public-attr-filter)))
