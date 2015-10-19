(ns flipmunks.budget.core
  (:gen-class)
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer :all]
            [ring.middleware.session.cookie :as cookie]
            [flipmunks.budget.datomic.pull :as p]
            [flipmunks.budget.datomic.transact :as t]
            [datomic.api :only [q db] :as d]
            [cemerick.friend :as friend]
            [cemerick.friend.credentials :as creds]
            [cemerick.friend.workflows :as workflows])
  (:import (clojure.lang ExceptionInfo)))

(def ^:dynamic conn)

(def config
  (read-string (slurp "budget-private/config.edn")))        ;TODO use edn/read

(defn safe
  "Tries to call fn with the given args, and catches and returns any ExceptionInfo that might be thrown by fn."
  [fn & args]
  (try
    (apply fn args)
    (catch ExceptionInfo e
      e)))

(defn current-user [session]
  (let [user-id (get-in session [::friend/identity :current])]
    (get-in session [::friend/identity :authentications user-id])))

(defn cur-usr-email [session]
  (:username (current-user session)))

; Transact data to datomic

(defn post-currencies [conn curs]
  (safe t/currencies conn curs))

(defn post-currency-rates [conn rates]
  (safe t/currency-rates conn rates))

(defn post-user-txs [conn user-email user-txs]
  (safe t/user-txs conn user-email user-txs))

(defn current-user-data
  "Fetch data for the user with user-email, and the schema for the found data."
  [user-email db params]
  (let [db-data (safe p/all-data db user-email params)
        db-schema (safe p/schema db p/schema-required? db-data)]
    {:schema   (vec db-schema)
     :entities db-data}))

; Auth stuff

(defn user-creds [email]
  (let [db-user (safe p/user (d/db conn) email)]
    (when db-user
      {:identity (:db/id db-user)
       :username (:user/email db-user)
       :password (:user/enc-password db-user)
       :roles #{::user}})))

; App stuff
(defroutes user-routes
           (GET "/txs" {params :params
                        session :session} (str (current-user-data (cur-usr-email session) (d/db conn) params)))
           (POST "/txs" {body :body
                         session :session} (str (post-user-txs conn (cur-usr-email session) body)))
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

(defn -main [& args])
