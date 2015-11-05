(ns flipmunks.budget.openexchangerates
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]))

(defn currencies-url
  "Get the open exchange rates api URL for fetching currencies
  with the given api key."
  [app-id]
  (str "https://openexchangerates.org/api/currencies.json?app_id=" app-id))

(defn currencies
  "Get currencies with given app-id."
  [app-id]
  (json/read-str (:body (client/get (currencies-url app-id))) :key-fn keyword))

(defn currency-rates-url
  "Get open exchange rates api URL for fetching currency rates for the given
  date string of the form \"yy-MM-dd\" and the api key."
  [app-id date-str]
  (str "https://openexchangerates.org/api/historical/" date-str ".json?app_id=" app-id))

(defn currency-rates
  "Get currency rates for the given date string of the form \"yy-MM-dd\" and the api key."
  [app-id date-str]
  (let [rates (json/read-str (:body (client/get (currency-rates-url app-id date-str))) :key-fn keyword)]
    (assoc rates :date date-str)))

;TODO fix this to have opts somewhere and pass in the appropriate place to fetch currencies from.
(defn local-currency-rates [date-str]
  (let [rates (json/read-str (slurp "resources/private/test/currency-rates.json") :key-fn keyword)]
    (assoc rates :date date-str)))
