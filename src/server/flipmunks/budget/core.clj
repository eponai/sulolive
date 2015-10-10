(ns flipmunks.budget.core
  (:gen-class)
  (:require [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.middleware.json :as middleware]
            [clj-http.client :as client]
            [clojure.data.json :as json]
            [flipmunks.budget.datomic.core :as dt]))

;; Handle fetch currency data.
(defn currencies [app-id]
  (let [url (str "https://openexchangerates.org/api/currencies.json?app_id=" app-id)]
    (json/read-str (:body (client/get url)) :key-fn keyword)))


(defn currency-rates [app-id date-str]
  (let [url (str "https://openexchangerates.org/api/historical/" date-str ".json?app_id=" app-id)]
    (json/read-str (:body (client/get url)) :key-fn keyword)))

(defn respond-data [date]
  (let [db-data (dt/pull-data date)
        db-schema (dt/pull-schema db-data)
        flatten-fn (fn [m] (reduce (fn [m [k v]] (if (get v :db/ident) (assoc m k (:db/ident v)) (assoc m k v))) {} m))]
    {:schema (vec (map flatten-fn (filter dt/schema-required? db-schema)))
     :entities db-data}))

(defroutes app-routes
           (context "/entries" [] (defroutes entries-routes
                                             (GET "/ymd=:date" [date] (str (respond-data date)))
                                             (POST "/" {body :body} (str (dt/post-user-tx body)))))
           (route/not-found "Not Found"))

(def app
  (middleware/wrap-json-response (middleware/wrap-json-body (handler/api app-routes) {:keywords? true})))

(defn -main [& args]
  )