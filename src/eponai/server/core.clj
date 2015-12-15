(ns eponai.server.core
  (:gen-class)
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [eponai.server.datomic.pull :as p]
            [eponai.server.datomic.transact :as t]
            [eponai.server.auth :as a]
            [eponai.server.http :as h]
            [datomic.api :only [q db] :as d]
            [cemerick.friend :as friend]
            [eponai.server.openexchangerates :as exch]
            [clojure.core.async :refer [>! <! go chan]]
            [clojure.edn :as edn]
            [ring.adapter.jetty :as jetty]
            [eponai.server.datomic_dev :refer [connect!]]))

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
  (let [budget (p/budget (d/db conn) (:username (friend/current-authentication request)))
        user-data (map #(assoc % :transaction/budget (:budget/uuid budget))
                       (:body request))]
    (go (>! currency-chan (map :transaction/date user-data)))
    (t/user-txs conn user-data)))

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
    (throw (ex-info "User already exists." {:cause   ::a/authentication-error
                                            :status  ::h/unathorized
                                            :data    {:username (params :username)}
                                            :message "User already exists."}))))

(defn verify [conn uuid]
  (let [verification (p/verification (d/db conn) uuid)]
    (if (= (:db/id (verification :verification/status)) (d/entid (d/db conn) :verification.status/pending))
      (t/add conn (:db/id verification) :verification/status :verification.status/verified)
      (throw (ex-info "Trying to activate invalid verification." {:cause   ::a/verification-error
                                                                  :status  ::h/unathorized
                                                                  :data    {:uuid uuid}
                                                                  :message "The verification link is no longer valid."})))))

; Auth stuff

(defn user-creds
  "Get user credentials for the specified email in the db. Returns nil if user does not exist.

  Throws ExceptionInfo if the user has not verified their email."
  [db email]
  (if-let [db-user (p/user db email)]
    (let [password (p/password db db-user)
          verifications (p/verifications db db-user :user/email :verification.status/verified)]
      (a/user->creds db-user password verifications))
    (throw (a/not-found email))))

; App stuff
(defroutes user-routes
           (GET "/txs" request (h/response (fetch p/all-data
                                                  (d/db (::h/conn request))
                                                  (:username (friend/current-authentication request))
                                                  (:params request))))
           (POST "/txs" request (do
                                  (post-user-data (::h/conn request) request)
                                  (h/txs-created request)))
           (GET "/test" {session :session} (h/response session))
           (GET "/curs" request (h/response (fetch p/currencies
                                                   (d/db (::h/conn request)))))
           (GET "/info" request (h/response (p/user (d/db (::h/conn request))
                                                    (:username (friend/current-authentication request))))))

(defroutes app-routes

           (POST "/signup" request (do
                                     (signup (::h/conn request) request)
                                     (h/user-created request)))
           (GET "/schema" request
             (let [db (d/db (::h/conn request))]
               (h/response (p/inline-value db
                                           (p/schema db)
                                           [[:db/valueType :db/ident]
                                            [:db/unique :db/ident]
                                            [:db/cardinality :db/ident]]))))
           (GET "/session" {session :session} (h/response (str session)))
           ; Anonymous
           (GET "/login" request (if (friend/identity request)
                                   (h/redirect "user/txs" request)
                                   (str "<h2>Login</h2>\n \n<form action=\"/login\" method=\"POST\">\n
            Username: <input type=\"text\" name=\"username\" value=\"\" /><br />\n
            Password: <input type=\"password\" name=\"password\" value=\"\" /><br />\n
            <input type=\"submit\" name=\"submit\" value=\"submit\" /><br />")))

           (GET "/signup" request (if (friend/identity request)
                                    (h/redirect "user/txs" request)
                                    (str "<h2>Signup</h2>\n \n<form action=\"/signup\" method=\"POST\">\n
            Username: <input type=\"text\" name=\"username\" value=\"\" /><br />\n
            Password: <input type=\"password\" name=\"password\" value=\"\" /><br />\n
            <input type=\"submit\" name=\"submit\" value=\"submit\" /><br />")))

           ; Requires user login
           (context "/user" [] (friend/wrap-authorize user-routes #{::a/user}))
           (GET "/verify/:uuid" [uuid :as request]
             (do
               (verify (::h/conn request) uuid)
               (h/response {:message "Your email is verified, you can now login."})))

           (friend/logout (ANY "/logout" [] (ring.util.response/redirect "/")))
           ; Not found
           (route/not-found "Not Found"))

(defn init
  ([]
   (init (partial exch/currency-rates nil)
         (partial a/send-email-verification (a/smtp))))
  ([cur-fn email-fn]
   (println "Initializing server...")
   (let [conn (connect!)]
     ;; Defines the 'app var when init is run.
     (def app
       (-> app-routes
           (friend/authenticate {:credential-fn (partial a/cred-fn #(user-creds (d/db conn) %))
                                 :workflows     [(a/form)]})
           h/wrap-error
           h/wrap-transit
           h/wrap-defaults
           (h/wrap-db conn)
           h/wrap-log))
     (go (while true (try
                       (post-currency-rates conn cur-fn (<! currency-chan))
                       (catch Exception e
                         (println (.getMessage e)))))))
   (go (while true (try
                     (send-email-verification email-fn (<! email-chan))
                     (catch Exception e
                       (println (.getMessage e))))))
   (println "Done.")))

(defn -main [& args]
  (init)
  (let [default-port 3000
        port (try
               (Long/parseLong (first args))
               (catch Exception e
                 default-port))]
    ;; by passing (var app) to run-jetty, it'll be forced to
    ;; evaluate app as code changes.
    (jetty/run-jetty (var app) {:port port})))

(defn main-debug
  "For repl-debug use.
  Returns a future with the jetty-server.
  The jetty-server will block the current thread, so
  we just wrap it in something dereffable."
  []
  (future (-main)))
