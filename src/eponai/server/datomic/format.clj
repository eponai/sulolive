(ns eponai.server.datomic.format
  (:require [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.coerce :as c]
            [datomic.api :only [db a] :as d]))

(defn date->ymd-string
  "Takes a date and returns a string for that date of the form yyy-MM-dd."
  [d]
  (f/unparse-local (f/formatters :date) d))

(defn date->db-tx
  "Returns a map representing a date datomic entity for the specified LocalDateTime,
  given a datomic id function (e.g. d/tempid) and a partition."
  [date]
  {:db/id          (d/tempid :db.part/user)
   :date/ymd       (date->ymd-string date)
   :date/year      (t/year date)
   :date/month     (t/month date)
   :date/day       (t/day date)
   :date/timestamp (c/to-long date)})

(defn date-str->db-tx
  "Returns a LocalDateTime for the string of format 'yyy-MM-dd',
  given a datomic id function and partition (e.g. datomic.api/tempid
  part :db.part/user)."
  [date-str]
  (date->db-tx (f/parse-local (f/formatters :date) date-str)))

(defn tags->db-tx
  "Returns datomic entities representing tags, given a vector of tag names."
  [tags]
  (map (fn [n] {:db/id          (d/tempid :db.part/user)
                :tag/name       n
                :tag/persistent false}) tags))

(defn user-tx->db-tx
  "Takes a user input transaction and converts into a datomic entity.
  A conversion function will be applied on the values for the following
  keys #{:currency :date :tags :amount :uuid} as appropriate for the database.
  All keys will be prefixed with 'transaction/' (e.g. :name -> :transaction/name)"
  [user-tx]
  (let [conv-fn-map {:transaction/currency (fn [c] [:currency/code c])
                     :transaction/date     (fn [d] (date-str->db-tx d))
                     :transaction/tags     (fn [t] (tags->db-tx t))
                     :transaction/amount   (fn [a] (bigint a))
                     :transaction/uuid     (fn [uuid] (java.util.UUID/fromString uuid))
                     :transaction/budget   (fn [b] [:budget/uuid b])}
        update-fn (fn [m k] (update m k (conv-fn-map k)))]
    (assoc (reduce update-fn user-tx (keys conv-fn-map))
      :db/id (d/tempid :db.part/user))))

(defn user-owned-txs->dbtxs [user-txs]
  (map user-tx->db-tx user-txs))

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
                  :conversion/date      (date-str->db-tx (:date data))
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
