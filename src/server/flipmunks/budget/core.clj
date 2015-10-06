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
            [datomic.api :only [q db] :as d]))

(def test-data (atom {:currency :USD
                      :dates    {2015 {1 {3 {:purchases [{:name "coffee" :cost {:currency :LEK :price 150}}]
                                             :rates     {:LEK 0.0081
                                                         :SEK 0.1213}}
                                          4 {:purchases [{:name "coffee" :cost {:currency :LEK :price 150}}, {:name "market" :cost {:currency :LEK :price 300}}]
                                             :rates     {:LEK 0.0081
                                                         :SEK 0.1213}}}}}}))
(defn deep-merge
  "Returns a map that consists of the rest of the maps merged onto the first.\n
  If a key occurs in more than one map, the mapping from\n  the latter (left-to-right) will be the mapping in the result.\n
  If a key holding a map value occurs in more than one map, the mapping(s) from the latter (left-to-right)\n
  will be combined with the mapping in the result by calling (deep-merge val-in-result val-in-latter)."
  [& maps]
  (when (some identity maps)
    (let [merge-entry (fn [m [k v]]
                        (if (and (map? v) (map? (get m k)))
                          (assoc m k (deep-merge (get m k) v))
                          (assoc m k v)))
          merge1 (fn [m1 m2]
                   (reduce merge-entry m1 m2))]
      (reduce merge1 maps))))

(defroutes app-routes
           (context "/entries" [] (defroutes entries-routes
                                             (GET "/" [] (str @test-data))
                                             (POST "/" {body :body} (str (swap! test-data deep-merge body)))))
           (route/not-found "Not Found"))

(def app
   (middleware/wrap-json-response (middleware/wrap-json-body (handler/api app-routes) {:keywords? true})))



;; Handle fetch currency data and insert in datomic.
(def currencies
  (json/read-str (:body (client/get "https://openexchangerates.org/api/currencies.json?app_id=APP_ID")) :key-fn keyword))

(def currency-rate-data
  (json/read-str (:body (client/get "https://openexchangerates.org/api/latest.json?app_id=APP_ID")) :key-fn keyword))

(defn currency-enum
  "Returns a datomic currency enum for the given keyword.\n
  Example: code-key :SEK returns :currency/SEK."
  [code-key]
  (keyword (str "currency/" (name code-key))))

(defn db-enums
  "Returns vector of datomic entities representing enums using the data from the specified map m.\n
  Key k in m are represent the db/ident value with the applied enum-fn, (enum-fn k) will be called where enum-fn should take a keyword as argument.\n
  The values in m are the values for entity attribute attr."
  [m enum-fn attr]
  (let [map-fn (fn [[k v]]
                 {:db/id         (d/tempid :db.part/user)
                  :db/ident      (enum-fn k)
                  attr v})]
    (mapv map-fn m)))

(defn ymd-string
  "Takes a date and returns a string for that date of the form yyy-MM-dd."
  [d]
  (f/unparse-local (f/formatters :date) d))

(defn db-date
  "Returns a map representing a date datomic entity."
  [date]
  {:db/id          (d/tempid :db.part/user)
   :date/ymd       (ymd-string date)
   :date/year      (t/year date)
   :date/month     (t/month date)
   :date/day       (t/day date)
   :date/timestamp (c/to-long date)})

(defn db-currency-rates
  "Returns a vector with datomic entites representing a currency conversions for the given date.\n
  d a LocalDate to be matched to the conversion.\n
  m map with timestamp and rates of the form {:timestamp 190293 :rates {:SEK 8.333 ...}}
  "
  [d m]
  (let [timestamp (:timestamp m)
        date-ymd (ymd-string d)
        map-fn (fn [[code rate]]
                 {:db/id                (d/tempid :db.part/user)
                  :conversion/timestamp timestamp
                  :conversion/date      [:date/ymd date-ymd]
                  :conversion/currency  (currency-enum code)
                  :conversion/rate      (bigdec rate)})]
    (mapv map-fn (:rates m))))

(defn -main [& args]
  (println (db-date (t/today)))
  ;(println (db-enums currencies currency-enum :currency/name))
  )