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
            [flipmunks.budget.openexchangerates :as exch]
            [clojure.core.async :refer [>! <! go chan]]
            [postal.core :as email]))

(def ^:dynamic conn)

(def currency-chan (chan))

; Pull

(defn fetch [fn db & args]
  (let [data (apply (partial fn db) args)]
    {:schema   (p/schema db data)
     :entities data}))

; Transact data to datomic

(defn post-currencies [conn curs]
  (t/currencies conn curs))

(defn post-currency-rates [conn rates-fn dates]
  (let [unconverted (clojure.set/difference (set dates)
                                            (p/converted-dates (d/db conn) dates))]
    (when (some identity unconverted)
      (t/currency-rates conn (map rates-fn (filter identity dates))))))

(defn post-user-data
  "Post new transactions for the user in the session. If there's no currency rates
  for the date of the transactions, they will be fetched from OER."
  [conn request]
  (let [user-data (:body request)]
    (go (>! currency-chan (map :transaction/date user-data)))
    (t/user-txs conn (h/email request) user-data)))

(defn signup
  "Create a new user and transact into datomic."
  [conn request]
  (t/new-user conn (a/new-signup request)))

(comment
  (defn send-email-verification [uuid]
    (po/send-message (smtp) {:from "info@gmail.com" :to "dianagren@gmail.com" :body (str "Click this link: ")}))

  (defn verify-email [conn email]
    (if-let [ver (p/verification (d/db conn) :user/email email)]
      (send-email-verification (ver :verification/uuid))
      (do
        (let [user (p/user (d/db conn) email)]
          (t/new-verification conn user :user/email)
          (let [ver (p/verification (:db-after) :user/email email)]
            (send-email-verification (ver :verification/uuid))))))))

; Auth stuff

(defn user-creds
  "Get user credentials for the specified email in the db. Returns nil if user does not exist.

  Throws ExceptionInfo if the user has not verified their email."
  [db email]
  (when-let [db-user (p/user db email)]
    (let [verifications (p/verifications db db-user :user/email :verification.status/activated)]
      (if (seq verifications)
        (a/user->creds db-user)
        (throw (ex-info "Email verification pending." {:cause ::a/verification-error
                                                       :status ::h/unprocessable-entity
                                                       :message "Email verification pending"
                                                       :data {:email email}}))))))

; App stuff
(defroutes user-routes
           (GET "/txs" request (h/response (fetch p/all-data
                                                  (d/db conn)
                                                  (h/email request)
                                                  (:params request))))
           (POST "/txs" request (h/response (post-user-data conn request)))
           (GET "/test" {session :session} (h/response session))
           (GET "/curs" [] (h/response (fetch p/currencies
                                              (d/db conn)))))

(defroutes app-routes

           (POST "/signup" request (signup conn request)
                                   (h/redirect "/login" request))
           ; Anonymous
           (GET "/login" [] (str "<h2>Login</h2>\n \n<form action=\"/login\" method=\"POST\">\n
            Username: <input type=\"text\" name=\"username\" value=\"\" /><br />\n
            Password: <input type=\"password\" name=\"password\" value=\"\" /><br />\n
            <input type=\"submit\" name=\"submit\" value=\"submit\" /><br />"))

           (GET "/signup" [] (str "<h2>Signup</h2>\n \n<form action=\"/signup\" method=\"POST\">\n
            Username: <input type=\"text\" name=\"username\" value=\"\" /><br />\n
            Password: <input type=\"password\" name=\"password\" value=\"\" /><br />\n
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

(defn init []
  (go (while true (try
                    (post-currency-rates conn exch/local-currency-rates (<! currency-chan))
                    (catch Exception e)))))