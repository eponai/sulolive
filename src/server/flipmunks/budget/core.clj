(ns flipmunks.budget.core
  (:gen-class)
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [flipmunks.budget.datomic.pull :as p]
            [flipmunks.budget.datomic.transact :as t]
            [flipmunks.budget.auth :as a]
            [flipmunks.budget.http :as h]
            [datomic.api :only [q db] :as d]
            [cemerick.friend :as friend]
            [flipmunks.budget.openexchangerates :as exch]))

(def ^:dynamic conn)

;; test currency rates
(defn test-currency-rates [date-str]                        ;TODO remove this test map and use the OER API.
  {:date date-str
   :rates {:SEK 8.333
           :USD 1
           :THB 35.555}})
; Pull

(defn fetch [fn db & args]
  (let [data (apply (partial fn db) args)]
    (h/body (p/schema db data) data)))

; Transact data to datomic

(defn post-currencies [conn curs]
  (t/currencies conn curs))

(defn post-user-data
  "Post new transactions for the user in the session. If there's no currency rates
  for the date of the transactions, they will be fetched from OER."
  [conn request rates-fn]                ;TODO fix this to not fetch currency rates synchronously
  (let [user-data (:body request)
        dates (map :transaction/date user-data)
        unconverted-dates (clojure.set/difference (set dates)
                                                  (p/converted-dates (d/db conn) dates))]
    (when (some identity unconverted-dates)
      (t/currency-rates conn (map rates-fn (filter identity unconverted-dates))))
    (t/user-txs conn (h/email request) user-data)))

(defn signup
  "Create a new user and transact into datomic."
  [conn request]
  (if-let [new-user (a/signup request)]
     (t/new-user conn new-user)))

(defn signup-redirect
  "Create a new user and redirect to the login page."
  [conn request]
  (signup conn request)
  (h/redirect "/login" request))

; Auth stuff

(defn user-creds
  "Get user credentials for the specified email in the db."
  [db email]
  (when-let [db-user (p/user db email)]
    (a/user->creds db-user)))

; App stuff
(defroutes user-routes
           (GET "/txs" request (h/response (fetch p/all-data
                                                  (d/db conn)
                                                  (h/email request)
                                                  (:params request))))
           (POST "/txs" request (h/response (post-user-data conn request test-currency-rates)))
           (GET "/test" {session :session} (h/response session))
           (GET "/curs" [] (h/response (fetch p/currencies
                                              (d/db conn)))))

(defroutes app-routes

           (POST "/signup" request (signup-redirect conn request))

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
      h/wrap))

(defn -main [& args])
