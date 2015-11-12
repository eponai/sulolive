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
            [flipmunks.budget.config :as c]))

(def ^:dynamic conn)

(def currency-chan (chan))
(def email-chan (chan))

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

(defn send-email-verification [email-fn [db email]]
  (when-let [verification (first (p/verifications db (p/user db email) :user/email :verification.status/pending))]
    (email-fn email (verification :verification/uuid))))

(defn signup
  "Create a new user and transact into datomic."
  [conn {:keys [params] :as request}]
  (if-not (p/user (d/db conn) (params :username))
    (let [tx (t/new-user conn (a/new-signup request))]
      (go (>! email-chan [(:db-after tx) (params :username)]))
      tx)
    (throw (ex-info "User already exists." {:cause ::a/authentication-error
                                            :status ::h/unathorized
                                            :data {:username (params :username)}
                                            :message "User already exists."}))))

(defn verify [uuid]
  (let [verification (p/verification (d/db conn) uuid)]
    (if (= (:db/id (verification :verification/status)) (d/entid (d/db conn) :verification.status/pending))
      (t/add conn (:db/id verification) :verification/status :verification.status/activated)
      (throw (ex-info "Trying to activate invalid verification." {:cause ::a/verification-error
                                                                  :status ::h/unathorized
                                                                  :data {:uuid uuid}
                                                                  :message "The verification link is no longer valid."})))))

; Auth stuff

(defn user-creds
  "Get user credentials for the specified email in the db. Returns nil if user does not exist.

  Throws ExceptionInfo if the user has not verified their email."
  [db email]
  (if-let [db-user (p/user db email)]
    (a/user->creds db-user (p/verifications db db-user :user/email :verification.status/activated))
    (throw (a/not-found email))))

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
           (GET "/verify/:uuid" [uuid] (verify uuid)
                                        (h/response {:message "Your email is verified, you can now login."}))

           (friend/logout (ANY "/logout" request (ring.util.response/redirect "/")))
           ; Not found
           (route/not-found "Not Found"))

(def app
  (-> app-routes
      (friend/authenticate {:credential-fn (partial a/cred-fn #(user-creds (d/db conn) %))
                            :workflows     [(a/form)]})
      h/wrap))

(defn init
  ([]
    (init exch/local-currency-rates a/send-email-verification))
  ([cur-fn email-fn]
   (println "Initializing server...")
   (go (while true (try
                     (post-currency-rates conn cur-fn (<! currency-chan))
                     (catch Exception e))))
   (go (while true (try
                     (send-email-verification email-fn (<! email-chan))
                     (catch Exception e))))
   (println "Done.")))