(ns eponai.server.external.openexchangerates
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [taoensso.timbre :refer [info error trace]]))

(defn currencies-url
  "Get the open exchange rates api URL for fetching currencies
  with the given api key."
  [app-id]
  (str "https://openexchangerates.org/api/currencies.json?app_id=" app-id))

(defn currencies
  "Get currencies with given app-id."
  [app-id]
  (if app-id
    (do
      (info "App Id provided, will pull currencies from OpenExchangeRates.")
      (let [currencies (json/read-str (:body (client/get (currencies-url app-id))) :key-fn keyword)]
        (info "Pulled currencies from OER.")
        currencies))

    (do
      (info "App Id nil, using local currencies.")
      ;; Dev mode currencies
      {:currencies {:SEK "Swedish Krona"
                    :USD "US Dollar"
                    :MYR "Malaysian Ringit"
                    :NOK "Norsk Krona"
                    :THB "Thai Baht"}})))

(defn currency-rates-url
  "Get open exchange rates api URL for fetching currency rates for the given
  date string of the form \"yy-MM-dd\" and the api key."
  [app-id date-str]
  (str "https://openexchangerates.org/api/historical/" date-str ".json?app_id=" app-id))

(defn currency-rates-fn
  "Get currency rates for the given date string of the form \"yy-MM-dd\" and the api key."
  [app-id]
  (fn [date-str]
    (info "Posting currency-rates for date:" date-str)
    (if app-id
      (info "App Id provided, will pull conversions from OpenExchangeRates.")
      (info "App Id nil, using local conversion rates."))
    (if (and app-id date-str)
      (let [rates (json/read-str (:body (client/get (currency-rates-url app-id date-str))) :key-fn keyword)]
        (info "Pulled conversions from OpenExchangeRates.")
        (assoc rates :date date-str))

      ;; Dev mode currency rates.
      {:date  date-str
       :rates {:SEK 8.56
               :USD 1
               :THB 35.64
               :MYR 3.8
               :NOK 7.5}})))
