(ns flipmunks.budget.datomic.format
  (:require [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.coerce :as c]))

(defn key-prefix
  "Returns a new keyword with k prefixed with p.
  E.g. (key-prefix :k 'p/') will return :p/k."
  [k p]
  (keyword (str p (name k))))

(defn trans
  "Transform data by applying key-fn to all the keys in the map,
  and using a map for applying different functions to the values for the
  given keys in value-fn-map. If no function is specified for a certain key,
  that value will be unchanged."
  [data key-fn value-fn-map]
  (let [map-fn (fn [[k v]]
                 [(key-fn k) ((or (k value-fn-map) identity) v)])]
    (into {} (map map-fn data))))

(defn date->ymd-string
  "Takes a date and returns a string for that date of the form yyy-MM-dd."
  [d]
  (f/unparse-local (f/formatters :date) d))

(defn date->db-tx
  "Returns a map representing a date datomic entity for the specified LocalDateTime,
  given a datomic id function (e.g. d/tempid) and a partition."
  [date dbid-fn part]
  {:db/id          (dbid-fn part)
   :date/ymd       (date->ymd-string date)
   :date/year      (t/year date)
   :date/month     (t/month date)
   :date/day       (t/day date)
   :date/timestamp (c/to-long date)})

(defn date-str->db-tx
  "Returns a LocalDateTime for the string of format 'yyy-MM-dd',
  given a datomic id function and partition (e.g. datomic.api/tempid
  part :db.part/user)."
  [date-str dbid-fn part]
  (date->db-tx (f/parse-local (f/formatters :date) date-str) dbid-fn part))

(defn tags->db-tx
  "Returns datomic entities representing tags, given a vector of tag names,
  given a datomic id function and partition (e.g. datomic.api/tempid
  part :db.part/user)."
  [tags dbid-fn part]
  (map (fn [n] {:db/id          (dbid-fn part)
                 :tag/name       n
                 :tag/persistent false}) tags))

(defn user-tx->db-tx
  "Takes a user input transaction and converts into a datomic entity,
  given a datomic id function (e.g. d/tempid) and a partition.
  A conversion function will be applied on the values for the following
  keys #{:currency :date :tags :amount :uuid} as appropriate for the database.
  All keys will be prefixed with 'transaction/' (e.g. :name -> :transaction/name)"
  [user-tx dbid-fn part]
  (let [conv-fn-map {:currency (fn [c] [:currency/code c])
                     :date     (fn [d] (date-str->db-tx d dbid-fn part))
                     :tags     (fn [tags] (tags->db-tx tags dbid-fn part))
                     :amount   (fn [a] (bigint a))
                     :uuid     (fn [uuid] (java.util.UUID/fromString uuid))}]
     (assoc (trans user-tx #(key-prefix % "transaction/") conv-fn-map) :db/id (dbid-fn part))))

(defn user-txs->db-txs [user-txs dbid-fn part]
  (map #(user-tx->db-tx % dbid-fn part) user-txs))

(defn cur-rates->db-txs
  "Returns a vector with datomic entites representing a currency conversions
  for the given date, given a datomic id function and partition (e.g. dbid-fn
  datomic.api/tempid part :db.part/user). m should be a  map with timestamp and
  rates of the form {:date \"yyyy-MM-dd\" :rates {:code rate ...}}"
  [data dbid-fn part]
  (let [map-fn (fn [[code rate]]
                 {:db/id                (dbid-fn part)
                  :conversion/date      (date-str->db-tx (:date data) dbid-fn part)
                  :conversion/currency  [:currency/code (name code)]
                  :conversion/rate      (bigdec rate)})]
    (map map-fn (:rates data))))

(defn curs->db-txs
  "Returns a sequence of currency datomic entries for the given map representing
  currencies of the form {:code \"name\" ...}. Pass the dbid-fn to generate a datomic
  tempid in partition part. (e.g. dbid-fn datomic.api/tempid part :db.part/user)"
  [currencies dbid-fn part]
  (let [map-fn (fn [[c n]] {:db/id         (dbid-fn part)
                            :currency/code (name c)
                            :currency/name n})]
    (map map-fn currencies)))