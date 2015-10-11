(ns flipmunks.budget.core
  (:gen-class)
  (:require [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.middleware.json :as middleware]
            [ring.middleware.resource :as middleware.res]
            [flipmunks.budget.datomic.core :as dt]
            [flipmunks.budget.datomic.format :as f]
            [flipmunks.budget.openexchangerates :as exch]))

(def config
  (read-string (slurp "budget-private/config.edn")))

(defn respond-data
  "Create request response based on params."
  [params]
  (let [db-data (dt/pull-data params)
        db-schema (dt/pull-schema db-data)]
    {:schema   (vec (filter dt/schema-required? db-schema))
     :entities db-data}))

(defn valid-user-tx? [user-tx]
  (every? #(contains? user-tx %) #{:uuid :name :date :amount :currency :created-at}))

; Transact data to datomic
(defn post-user-txs
  "Put the user transaction maps into datomic. Will fail if one or
  more of the following required fields are not included in the map:
  #{:uuid :name :date :amount :currency}."
  [user-txs]
  (if (every? #(valid-user-tx? %) user-txs)
    (dt/transact-data f/user-txs->db-txs user-txs)          ;TODO: check if conversions exist for this date, and fetch if not.
    {:text "Missing required fields"}))                     ;TODO: fix this to pass proper error back to client.

(defn post-currencies
  " Fetch currencies (codes and names) from open exchange rates and put into datomic."
  []
  (let [app-id (:open-exhange-app-id config)]
    (dt/transact-data f/curs->db-txs (exch/currencies app-id))))

(defn post-currency-rates [date-str]
  (let [app-id (:open-exhange-app-id config)]
    (dt/transact-data f/cur-rates->db-txs (assoc (exch/currency-rates app-id date-str) :date date-str))))

; App stuff
(defroutes app-routes
           (context "/entries" [] (defroutes entries-routes
                                             (GET "/" {params :params} (str (respond-data params)))
                                             (POST "/" {body :body} (str (post-user-txs body)))))
           (route/not-found "Not Found"))

(def app
  (-> (handler/api app-routes)
      (middleware.res/wrap-resource "public")
      (middleware/wrap-json-body {:keywords? true})
      (middleware/wrap-json-response)))

(defn -main [& args]
  )
