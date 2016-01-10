(ns eponai.common.database.pull
  (:require [eponai.common.format :as f]
    #?(:clj
            [datomic.api :as d]
       :cljs [datascript.core :as d]))
  #?(:clj
     (:import (datomic Database))))

(defn- db-instance? [db]
  #?(:clj (instance? Database db)
     :cljs (instance? datascript.db db)))

(defn- throw-error [e cause data]
  (let [#?@(:clj  [msg (.getMessage e)]
            :cljs [msg (.-message e)])]
    (throw (ex-info msg
                    {:cause     cause
                     :data      data
                     :message   msg
                     :exception e
                     #?@(:clj [:status :eponai.server.http/service-unavailable])}))))

(defn q [query & inputs]
  (try
    (apply (partial d/q query) inputs)
    (catch #?(:clj Exception :cljs :default) e
      (throw-error e ::query-error {:query  query
                                    :inputs inputs}))))

(defn pull [db pattern eid]
  (try
    (let [ret (d/pull db pattern eid)]
      (if-not (= {:db/id nil}
                 ret)
        ;; Datomic returns {:db/id nil} if there's noting found for a lookup ref for example... so just return nil in that case.
        ret
        nil))
    (catch #?(:clj Exception :cljs :default) e
      (throw-error e ::pull-error {:pattern pattern
                                   :eid     eid}))))

(defn verification
  "Pull specific verification from the database using the unique uuid field.
  Returns an entity if onlu uuid is provided, or an entire tree matching the passed in query."
  ([db ver-uuid]
   (let [verification (verification db '[:db/id] ver-uuid)]
     (d/entity db (:db/id verification))))
  ([db query ver-uuid]
   (let [id (f/str->uuid ver-uuid)]
     (pull db query [:verification/uuid id]))))

(defn user
  ([db email]
   (let [db-user (:db/id (user db '[*] email))]
     (d/entity db db-user)))
  ([db query email]
    (pull db query [:user/email email])))

(defn verifications
  [db user-db-id status]
  {:pre [(db-instance? db)
         (number? user-db-id)
         (keyword? status)]}
  (q '[:find [?v ...]
           :in $ ?u ?s
           :where
           [?v :verification/entity ?u]
           [?u :user/email ?e]
           [?v :verification/value ?e]
           [?v :verification/status ?s]]
         db
         user-db-id
         status))