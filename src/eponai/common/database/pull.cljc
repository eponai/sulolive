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
      (println "Result: " ret)
      ret)
    (catch #?(:clj Exception :cljs :default) e
      (throw-error e ::pull-error {:pattern pattern
                                   :eid     eid}))))

(defn verification
  "Pull specific verification from the database using the unique uuid field."
  [db query ver-uuid]
  (prn "verification pull")
  (let [id (f/str->uuid ver-uuid)]
    (prn "UUID: " id)
    (prn "Query: " query)
    (prn "Pulling verification: " (q '[:find ?e
                                           :in $ ?id
                                           :where [?e :verification/uuid ?id]]
                                         db
                                         id))
    (pull db query [:verification/uuid id])))

(defn user
  ([db email]
    (user db '[*] email))
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