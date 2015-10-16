(ns flipmunks.budget.datomic.transact
  (:require [datomic.api :as d]
            [flipmunks.budget.datomic.format :as f]))


(defn- transact
  "Transact a collecion of entites into datomic."
  [conn txs]
  (try
    @(d/transact conn txs)
    (catch Exception e
      (throw (ex-info (.getMessage e) {:cause ::transaction-error
                                       :params {:conn conn
                                                :txs txs}})))))

(defn- valid-user-tx? [user-tx]
  (every? #(contains? user-tx %) #{:uuid :name :date :amount :currency :created-at}))

(defn user-txs
  "Put the user transaction maps into datomic. Will fail if one or
  more of the following required fields are not included in the map:
  #{:uuid :name :date :amount :currency}."
  [conn user-email user-txs]
  (if (every? #(valid-user-tx? %) user-txs)
    (let [txs (f/user-owned-txs->dbtxs user-email user-txs)]
      (transact conn txs))                                  ;TODO: check if conversions exist for this date, and fetch if not.
    {:text "Missing required fields"}))                     ;TODO: fix this to pass proper error back to client.

(defn currency-rates [conn date-str rates]
  (transact conn (f/cur-rates->db-txs (assoc rates :date date-str))))

(defn currencies [conn currencies]
  (transact conn (f/curs->db-txs currencies)))