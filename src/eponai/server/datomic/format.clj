(ns eponai.server.datomic.format
  (:require [clj-time.core :as t]
            [clj-time.coerce :as c]
            [datomic.api :only [db a] :as d]
            [eponai.common.format :as common.format]
            [eponai.server.datomic.pull :as p]))

(defn user->db-user-password [new-user]
  (let [user-id (d/tempid :db.part/user)]
    [{:db/id user-id
      :user/email (new-user :username)}
     {:db/id (d/tempid :db.part/user)
      :password/credential user-id
      :password/bcrypt (new-user :bcrypt)}]))

(defn db-entity->db-verification [entity attribute status]
  {:db/id                   (d/tempid :db.part/user)
   :verification/status     (or status :verification.status/pending)
   :verification/created-at (c/to-long (t/now))
   :verification/uuid       (d/squuid)
   :verification/entity     (entity :db/id)
   :verification/attribute  attribute
   :verification/value      (entity attribute)})

(defn db-budget [user-eid]
  {:db/id (d/tempid :db.part/user)
   :budget/uuid (d/squuid)
   :budget/created-by user-eid})

(defn cur-rates->db-txs
  "Returns a vector with datomic entites representing a currency conversions
  for the given date. m should be a  map with timestamp and
  rates of the form {:date \"yyyy-MM-dd\" :rates {:code rate ...}}"
  [data]
  (let [map-fn (fn [[code rate]]
                 {:db/id                (d/tempid :db.part/user)
                  :conversion/date      (common.format/date-str->db-tx (:date data))
                  :conversion/currency  [:currency/code (name code)]
                  :conversion/rate      (bigdec rate)})]
    (map map-fn (:rates data))))

(defn curs->db-txs
  "Returns a sequence of currency datomic entries for the given map representing
  currencies of the form {:code \"name\" ...}."
  [currencies]
  (let [map-fn (fn [[c n]] {:db/id         (d/tempid :db.part/user)
                            :currency/code (name c)
                            :currency/name n})]
    (map map-fn currencies)))

(defn cur-infos->db-txs
  [cur-infos]
  (map (fn [info]
         {:db/id                   (d/tempid :db.part/user)
          :currency/code           (:code info)
          :currency/symbol         (:symbol info)
          :currency/symbol-native  (:symbol_native info)
          :currency/decimal-digits (:decimal_digits info)})
       cur-infos))
