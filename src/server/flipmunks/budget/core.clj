(ns flipmunks.budget.core
  (:gen-class)
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer :all]
            [ring.middleware.session.cookie :as cookie]
            [flipmunks.budget.datomic.pull :as p]
            [flipmunks.budget.datomic.transact :as t]
            [flipmunks.budget.auth :as a]
            [flipmunks.budget.http :as http]
            [datomic.api :only [q db] :as d]
            [cemerick.friend :as friend]
            [flipmunks.budget.openexchangerates :as exch]))

(def ^:dynamic conn)

(def config
  (clojure.edn/read-string (slurp "budget-private/config.edn")))

(defn current-user
  "Current session user information."
  [session]
  (let [user-id (get-in session [::friend/identity :current])]
    (get-in session [::friend/identity :authentications user-id])))

(defn cur-usr-email
  "The user email of current session."
  [session]
  (:username (current-user session)))

;; test currency rates
(defn test-currency-rates [date-str]                        ;TODO remove this test map and use the OER API.
  {:date date-str
   :rates {:SEK 8.333
           :USD 1
           :THB 35.555}})
; Transact data to datomic

(defn- response [schema data]
  {:schema   schema
   :entities data})

(defn post-currencies [conn curs]
  (t/currencies conn curs))

(defn user-txs
  "Fetch response for user data with user-email."
  [user-email db params]
  (let [data (p/all-data db user-email params)
        schema (p/schema db data)]
    (response schema data)))

(defn currencies
  "Fetch response for requesting currencies."
  [db]
  (let [curs (p/currencies db)
        schema (p/schema db curs)]
    (response schema curs)))

(defn post-user-data
  "Post new transactions for the user in the session. If there's no currency rates
  for the date of the transactions, they will be fetched from OER."
  [conn request rates-fn]                ;TODO fix this to not fetch currency rates synchronously
  (let [user-data (:body request)
        dates (map :transaction/date user-data)
        unconverted-dates (clojure.set/difference (set dates)
                                                  (p/converted-dates (d/db conn) dates))]
    (when (not-empty unconverted-dates)
      (t/currency-rates conn (map rates-fn unconverted-dates)))
    (t/user-txs conn (cur-usr-email (:session request)) user-data)))

(defn signup
  "Create a new user and transact into datomic."
  [conn request]
  (if-let [new-user (a/signup request)]
     (t/new-user conn new-user)))

(defn signup-redirect
  "Create a new user and redirect to the login page."
  [conn request]
  (signup conn request)
  (http/redirect "/login" request))

; Auth stuff

(defn user-creds
  "Get user credentials for the specified email in the db."
  [db email]
  (when-let [db-user (p/user db email)]
    (a/user->creds db-user)))

; App stuff
(defroutes user-routes
           (GET "/txs" {params :params
                        session :session} (str (http/response (user-txs (cur-usr-email session) (d/db conn) params))))
           (POST "/txs" request (str (post-user-data conn request test-currency-rates)))
           (GET "/test" {session :session} (str session))
           (GET "/curs" [] (str (http/response (currencies (d/db conn))))))

(defroutes app-routes

           (POST "/signup" request (str (signup-redirect conn request)))

           ; Anonymous
           (GET "/login" [] (str "<h2>Login</h2>\n \n<form action=\"/login\" method=\"POST\">\n
            Username: <input type=\"text\" name=\"username\" value=\"\" /><br />\n
            Password: <input type=\"password\" name=\"password\" value=\"\" /><br />\n
            <input type=\"submit\" name=\"submit\" value=\"submit\" /><br />"))

           (GET "/signup" [] (str "<h2>Signup</h2>\n \n<form action=\"/signup\" method=\"POST\">\n
            Username: <input type=\"text\" name=\"username\" value=\"\" /><br />\n
            Password: <input type=\"password\" name=\"password\" value=\"\" /><br />\n
            Repeat: <input type=\"password\" name=\"repeat\" value=\"\" /><br />\n\n
            <input type=\"submit\" name=\"submit\" value=\"submit\" /><br />"))

           ; Requires user login
           (context "/user" [] (friend/wrap-authorize user-routes #{::a/user}))

           (friend/logout (ANY "/logout" request (ring.util.response/redirect "/")))
           ; Not found
           (route/not-found "Not Found"))

(def app
  (-> app-routes
      (friend/authenticate {:credential-fn (partial a/cred-fn #(user-creds (d/db conn) %))
                            :workflows     [(a/form)]})
      (wrap-defaults (-> site-defaults
                         (assoc-in [:security :anti-forgery] false)
                         (assoc-in [:session :store] (cookie/cookie-store {:key (config :session-cookie-key)}))
                         (assoc-in [:session :cookie-name] "cookie-name")))
      http/wrap-error))

(defn -main [& args])
