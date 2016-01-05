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


(def public-attrs #{:conversion/date :currency/code :date/ymd :tag/name})
(def private-attrs #{:password/owner :verification/uuid})

(defn user-db [db user-id]
  (let [users-entities (set (user-entities db user-id))]
    (d/filter db
              (fn [db [eid]]
                (or (contains? users-entities eid)
                    (some public-attrs (keys (d/entity db eid)))
                    (not (some private-attrs (keys (d/entity db eid)))))))))
