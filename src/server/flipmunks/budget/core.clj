(ns flipmunks.budget.core
  (:gen-class)
  (:require [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.middleware.json :as middleware]
            [clj-http.client :as client]
            [clojure.data.json :as json]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.coerce :as c]
            [datomic.api :only [q db] :as d])
  (:import (java.util UUID)))

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

;; Handle fetch currency data and insert in datomic.
(defn currencies [app-id]
  (let [url (str "https://openexchangerates.org/api/currencies.json?app_id=" app-id)]
    (json/read-str (:body (client/get url)) :key-fn keyword))
  )


(defn currency-rates [app-id date-str]
  (let [url (str "https://openexchangerates.org/api/historical/" date-str ".json?app_id=" app-id)]
    (json/read-str (:body (client/get url)) :key-fn keyword))
  )

(defn date->ymd-string
  "Takes a date and returns a string for that date of the form yyy-MM-dd."
  [d]
  (f/unparse-local (f/formatters :date) d))

(defn date->db-tx
  "Returns a map representing a date datomic entity for the specified LocalDateTime."
  [date]
  {:db/id          (d/tempid :db.part/user)
   :date/ymd       (date->ymd-string date)
   :date/year      (t/year date)
   :date/month     (t/month date)
   :date/day       (t/day date)
   :date/timestamp (c/to-long date)})

(defn date-str->db-tx
  "Returns a LocalDateTime for the string of format 'yyy-MM-dd'."
  [date-str]
  (date->db-tx (f/parse-local (f/formatters :date) date-str)))

(defn tags->db-tx
  "Returns datomic entities representing tags, given a vector of tag names."
  [tags]
  (mapv (fn [n] {:db/id          (d/tempid :db.part/user)
                 :tag/name       n
                 :tag/persistent false}) tags))

(defn user-tx->db-tx
  "Takes a user input transaction and converts into a datomic entity.
  A conversion function will be applied on the values for the following
  keys #{:currency :date :tags :amount :uuid} as appropriate for the database.
  All keys will be prefixed with 'transaction/' (e.g. :name -> :transaction/name)"
  [user-tx]
  (let [conv-fn-map {:currency (fn [c] [:currency/code c])
                     :date     (fn [d] (date-str->db-tx d))
                     :tags     (fn [tags] (tags->db-tx tags))
                     :amount   (fn [a] (bigint a))
                     :uuid     (fn [uuid] (java.util.UUID/fromString uuid))}]
    (assoc (trans user-tx #(key-prefix % "transaction/") conv-fn-map) :db/id (d/tempid :db.part/user))))

(defn cur-rates->db-txs
  "Returns a vector with datomic entites representing a currency conversions
  for the given date. d a date string of the form \"yy-MM-dd\" to be matched
  to the conversion. m map with timestamp and rates of the form
  {:timestamp 190293 :rates {:SEK 8.333 ...}}"
  [date-ymd m]
  (let [timestamp (:timestamp m)
        map-fn (fn [[code rate]]
                 {:db/id                (d/tempid :db.part/user)
                  :conversion/timestamp timestamp
                  :conversion/date      (date-str->db-tx date-ymd)
                  :conversion/currency  [:currency/code (name code)]
                  :conversion/rate      (bigdec rate)})]
    (mapv map-fn (:rates m))))

(def conn (d/connect "datomic:dev://localhost:4334/test-budget"))

(defn distinct-ids
  "Get distinct db ids from the nested entity with the given keyword k in a list of entities.
  If f is provided, it will be applied on the list of entities matching the k keyword.
  E.g. if k points to a list of values, #(apply concat %) can be provided to fetch
  the distinct values in all of the lists."
  ([k entities]
    (distinct-ids nil k entities))
  ([f k entities]
   (mapv :db/id (distinct ((or f identity) (mapv k entities))))))

(defn pull-data
  "Pull transaction data from datomic for the specified date string of the form \"yy-MM-dd\"."
  [date]
  (let [transactions (:transaction/_date (d/pull (d/db conn) '[{:transaction/_date [*]}] [:date/ymd date]))]
    (vec (concat transactions
                 (d/pull-many (d/db conn) '[*] (concat [[:date/ymd date]]
                                                       (distinct-ids #(apply concat %) :transaction/tags transactions)
                                                       (distinct-ids :transaction/currency transactions)))))))

(defn pull-schema [data]
  (let [idents (set (apply concat (mapv keys data)))
        ident-tuples (mapv #(vector :db/ident %) idents)]
    (d/pull-many (d/db conn) '[:db/ident {:db/valueType [:db/ident]} {:db/cardinality [:db/ident]} {:db/unique [:db/ident]}] ident-tuples)))

(defn schema-required?
  "Return true if the entity is required to be passed with schema.
  Is true if the entity has a type ref, or cardinality many."
  [db-entity]
  (or (= (get-in db-entity [:db/valueType :db/ident]) :db.type/ref)
      (= (get-in db-entity [:db/cardinality :db/ident]) :db.cardinality/many)
      (get db-entity :db/unique))
  )

(defn respond-data [date]
  (let [db-data (pull-data date)
        db-schema (pull-schema db-data)
        flatten-fn (fn [m] (reduce (fn [m [k v]] (if (get v :db/ident) (assoc m k (:db/ident v)) (assoc m k v))) {} m))]
    {:schema (vec (map flatten-fn (filter schema-required? db-schema)))
     :entities db-data}))

(defn post-user-tx
  "Put the user transaction maps into datomic. Will fail if one or
  more of the following required fields are not included in the map:
  #{:uuid :name :date :amount :currency}."
  [user-tx]
  (if (every? #(contains? user-tx %) #{:uuid :name :date :amount :currency})
    (d/transact conn [(user-tx->db-tx user-tx)])            ;TODO: check if conversions exist for this date, and fetch if not.
    {:text "Missing required fields"}))                     ;TODO: fix this to pass proper error back to client.

(defroutes app-routes
           (context "/entries" [] (defroutes entries-routes
                                             (GET "/ymd=:date" [date] (str (respond-data date)))
                                             (POST "/" {body :body} (str (post-user-tx body)))))
           (route/not-found "Not Found"))

(def app
  (middleware/wrap-json-response (middleware/wrap-json-body (handler/api app-routes) {:keywords? true})))

(defn -main [& args]
  )