(ns flipmunks.budget.core
  (:gen-class)
  (:require [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.resource :as middleware.res]
            [flipmunks.budget.datomic.core :as dt]
            [flipmunks.budget.datomic.format :as f]
            [flipmunks.budget.openexchangerates :as exch]
            [datomic.api :only [q db] :as d]
            [cemerick.friend :as friend]
            [cemerick.friend.credentials :as creds]
            [cemerick.friend.workflows :as workflows]))

(def ^:dynamic conn)

(def config
  (read-string (slurp "budget-private/config.edn")))        ;TODO use edn/read

(defn respond-data
  "Create request response based on params."
  [db params]
  (let [db-data (dt/pull-all-data db params)
        db-schema (dt/pull-schema db db-data)]
    {:schema   (vec (filter dt/schema-required? db-schema))
     :entities db-data}))

(defn transact-data [conn txs]
  (try
    @(dt/transact-data conn txs)
    (catch Exception e
      {:db/error (.getMessage e)})))

(defn valid-user-tx? [user-tx]
  (every? #(contains? user-tx %) #{:uuid :name :date :amount :currency :created-at}))

; Transact data to datomic
(defn post-user-txs
  "Put the user transaction maps into datomic. Will fail if one or
  more of the following required fields are not included in the map:
  #{:uuid :name :date :amount :currency}."
  [conn user-txs]
  (if (every? #(valid-user-tx? %) user-txs)
    (transact-data conn (f/user-txs->db-txs user-txs))      ;TODO: check if conversions exist for this date, and fetch if not.
    {:text "Missing required fields"}))                     ;TODO: fix this to pass proper error back to client.

(defn post-currencies
  " Fetch currencies (codes and names) from open exchange rates and put into datomic."
  [conn curs]
  (transact-data conn (f/curs->db-txs curs)))

(defn post-currency-rates [conn date-str rates]
  (transact-data conn (f/cur-rates->db-txs (assoc rates :date date-str))))

; Auth stuff

(defn user-creds [email]
  (let [db-user (dt/pull-user (d/db conn) email)]
    (when db-user
      {:username (:user/email db-user)
       :password (:user/enc-password db-user)
       :roles #{::user}})))

; App stuff
(defroutes app-routes
           (GET "/" [] "Hello World")
           (GET "/entries" {params :params}
             (friend/authorize #{::user} (str (respond-data (d/db conn) params))))
           (GET "/login" [] "<h2>Login</h2>\n\n<form action=\"/login\" method=\"POST\">\n    Username: <input type=\"text\" name=\"username\" value=\"\" /><br />\n    Password: <input type=\"password\" name=\"password\" value=\"\" /><br />\n    <input type=\"submit\" name=\"submit\" value=\"submit\" /><br />")
           (route/not-found "Not Found"))

(def app
  (-> (handler/api app-routes)
      (middleware.res/wrap-resource "public")
      (friend/authenticate {:credential-fn (partial creds/bcrypt-credential-fn user-creds)
                            :workflows     [(workflows/interactive-form)]})
      (wrap-keyword-params)
      (wrap-params)
      (wrap-session)))

(defn -main [& args]
  )
