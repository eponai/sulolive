(ns flipmunks.budget.core
  (:gen-class)
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer :all]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.session.cookie :as cookie]
            [flipmunks.budget.datomic.core :as dt]
            [flipmunks.budget.datomic.format :as f]
            [flipmunks.budget.openexchangerates :as exch]
            [datomic.api :only [q db] :as d]
            [cemerick.friend :as friend]
            [cemerick.friend.credentials :as creds]
            [cemerick.friend.workflows :as workflows])
  (:import (com.amazonaws.services.importexport.model MissingParameterException)))

(def ^:dynamic conn)

(def config
  (read-string (slurp "budget-private/config.edn")))        ;TODO use edn/read

(defn pull
  "Wraps the given pull-fn to catch and catches potential exceptions thrown from datomic,
  returning a map {:db/error error-msg}. Pass any args that are expected in pull-fn."
  [pull-fn & args]
  (try
    (apply pull-fn args)
    (catch MissingParameterException e
      {:db/error (.getMessage e)})))

(defn transact
  "Transacts entities on the given connection conn and catches potential exceptions,
  returning a map {:db/error error-msg}."
  [conn txs]
  (try
    (dt/transact conn txs)
    (catch Exception e
      {:db/error (.getMessage e)})))

(defn valid-user-tx? [user-tx]
  (every? #(contains? user-tx %) #{:uuid :name :date :amount :currency :created-at}))

(defn current-user [session]
  (let [user-id (get-in session [::friend/identity :current])]
    (get-in session [::friend/identity :authentications user-id])))

; Transact data to datomic
(defn post-user-txs
  "Put the user transaction maps into datomic. Will fail if one or
  more of the following required fields are not included in the map:
  #{:uuid :name :date :amount :currency}."
  [conn session user-txs]
  (if (every? #(valid-user-tx? %) user-txs)
    (let [user-email ((current-user session) :username)
          txs (f/user-owned-txs->dbtxs user-email user-txs)]
      (transact conn txs))                                  ;TODO: check if conversions exist for this date, and fetch if not.
    {:text "Missing required fields"}))                     ;TODO: fix this to pass proper error back to client.

(defn post-currencies
  " Fetch currencies (codes and names) from open exchange rates and put into datomic."
  [conn curs]
  (transact conn (f/curs->db-txs curs)))

(defn post-currency-rates [conn date-str rates]
  (transact conn (f/cur-rates->db-txs (assoc rates :date date-str))))

(defn current-user-data
  "Create request response based on params."
  [session db params]
  (let [user-id (get-in session [::friend/identity :current])
        db-data (pull dt/all-data db (assoc params :user-id user-id))
        db-schema (pull dt/schema db db-data)]
    {:schema   (vec (filter dt/schema-required? db-schema))
     :entities db-data}))

; Auth stuff

(defn user-creds [email]
  (let [db-user (pull dt/user (d/db conn) email)]
    (when db-user
      {:identity (:db/id db-user)
       :username (:user/email db-user)
       :password (:user/enc-password db-user)
       :roles #{::user}})))

; App stuff
(defroutes user-routes
           (GET "/txs" {params :params
                        session :session} (str (current-user-data session (d/db conn) params)))
           (POST "/txs" {body :body
                         session :session} (str (post-user-txs conn session body)))
           (GET "/test" {session :session} (str session)))

(defroutes app-routes
           ; Anonymous
           (GET "/login" [] (str "<h2>Login</h2>\n \n<form action=\"/login\" method=\"POST\">\n
            Username: <input type=\"text\" name=\"username\" value=\"\" /><br />\n
            Password: <input type=\"password\" name=\"password\" value=\"\" /><br />\n
            <input type=\"submit\" name=\"submit\" value=\"submit\" /><br />"))

           ; Requires user login
           (context "/user" [] (friend/wrap-authorize user-routes #{::user}))

           ; Not found
           (route/not-found "Not Found"))

(def app
  (-> app-routes
      (friend/authenticate {:credential-fn (partial creds/bcrypt-credential-fn user-creds)
                            :workflows     [(workflows/interactive-form)]})
      (wrap-defaults (-> site-defaults
                         (assoc-in [:security :anti-forgery] false)
                         (assoc-in [:session :store] (cookie/cookie-store {:key (config :session-cookie-key)}))
                         (assoc-in [:session :cookie-name] "cookie-name")))))

(defn -main [& args]
  )
