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

(defn user-attrs [db user-entities]
  (persistent!
    (reduce (fn [attrs eid]
              (reduce conj! attrs (keys (d/entity db eid))))
            (transient #{})
            user-entities)))

(defn- user-or-public-entity-filter [db user-id]
  (let [owned-entities (user-entities db user-id)
        attrs (user-attrs db owned-entities)
        eids (set owned-entities)]
    (d/filter db
              (fn [_ [eid attr]]
                (or (contains? eids eid)
                    (not (contains? attrs attr)))))))

(defn- private-attr-filter [db]
  (let [private-attrs #{:password/credential :verification/uuid}]
    (d/filter db
              (fn [db [eid]]
                (not (some private-attrs (keys (d/entity db eid))))))))

(defn- no-auth-filter [db]
  (let [user-attrs #{:transaction/uuid :budget/uuid}]
    (d/filter db
             (fn [db [eid]]
                  (not (some user-attrs (keys (d/entity db eid))))))))

(defn user-db [db user-id]
  (-> db
      (private-attr-filter)
      (user-or-public-entity-filter user-id)))

(defn authenticated-db [db user-id]
  (user-db db user-id))

(defn not-authenticated-db [db]
  (-> db
      (no-auth-filter)))