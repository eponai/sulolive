(ns flipmunks.budget.core
  (:gen-class)
  (:require [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.middleware.json :as middleware]
            [ring.middleware.resource :as middleware.res]
            [flipmunks.budget.datomic.core :as dt]
            [flipmunks.budget.datomic.format :as f]
            [flipmunks.budget.openexchangerates :as exch]
            [datomic.api :only [q db] :as d]))

(def ^:dynamic conn)

(def config
  (read-string (slurp "budget-private/config.edn")))

(defn respond-data
  "Create request response based on params."
  [db params]
  (let [db-data (dt/pull-all-data db params)
        db-schema (dt/pull-schema db db-data)]
    {:schema   (vec (filter dt/schema-required? db-schema))
     :entities db-data}))

(defn transact-data [conn txs]
  (dt/transact-data conn txs))

(defn valid-user-tx? [user-tx]
  (every? #(contains? user-tx %) #{:uuid :name :date :amount :currency :created-at}))

; Transact data to datomic
(defn post-user-txs
  "Put the user transaction maps into datomic. Will fail if one or
  more of the following required fields are not included in the map:
  #{:uuid :name :date :amount :currency}."
  [f conn user-txs]
  (if (every? #(valid-user-tx? %) user-txs)
    (try
      (f conn (f/user-txs->db-txs user-txs))          ;TODO: check if conversions exist for this date, and fetch if not.
      (catch Exception e
         (ex-data e)))
    {:text "Missing required fields"}))                     ;TODO: fix this to pass proper error back to client.

(defn post-currencies
  " Fetch currencies (codes and names) from open exchange rates and put into datomic."
  [f conn curs]
  (f conn (f/curs->db-txs curs)))

(defn post-currency-rates [f conn date-str rates]
  (f conn (f/cur-rates->db-txs (assoc rates :date date-str))))

; App stuff
(defroutes app-routes
           (context "/entries" [] (defroutes entries-routes
                                             (GET "/" {params :params} (str (respond-data (d/db conn) params)))
                                             (POST "/" {body :body} (str (post-user-txs transact-data conn body)))))
           (route/not-found "Not Found"))

(def app
  (-> (handler/api app-routes)
      (middleware.res/wrap-resource "public")
      (middleware/wrap-json-body {:keywords? true})
      (middleware/wrap-json-response)))

(defn -main [& args]
  )
