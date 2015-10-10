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
  "Returns a map representing a date datomic entity for the specified LocalDateTime."
  [date dbid-fn part]
  {:db/id          (dbid-fn part)
   :date/ymd       (date->ymd-string date)
   :date/year      (t/year date)
   :date/month     (t/month date)
   :date/day       (t/day date)
   :date/timestamp (c/to-long date)})

(defn date-str->db-tx
  "Returns a LocalDateTime for the string of format 'yyy-MM-dd'."
  [date-str dbid-fn part]
  (date->db-tx (f/parse-local (f/formatters :date) date-str) dbid-fn part))

(defn tags->db-tx
  "Returns datomic entities representing tags, given a vector of tag names."
  [tags dbid-fn part]
  (mapv (fn [n] {:db/id          (dbid-fn part)
                 :tag/name       n
                 :tag/persistent false}) tags))

(defn user-tx->db-tx
  "Takes a user input transaction and converts into a datomic entity.
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

(defn cur-rates->db-txs
  "Returns a vector with datomic entites representing a currency conversions
  for the given date. d a date string of the form \"yy-MM-dd\" to be matched
  to the conversion. m map with timestamp and rates of the form
  {:timestamp 190293 :rates {:SEK 8.333 ...}}"
  [date-ymd m dbid-fn part]
  (let [timestamp (:timestamp m)
        map-fn (fn [[code rate]]
                 {:db/id                (dbid-fn part)
                  :conversion/timestamp timestamp
                  :conversion/date      (date-str->db-tx date-ymd dbid-fn part)
                  :conversion/currency  [:currency/code (name code)]
                  :conversion/rate      (bigdec rate)})]
    (mapv map-fn (:rates m))))

