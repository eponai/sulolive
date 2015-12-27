(ns eponai.server.api
  (:require [cemerick.friend :as friend]
            [clojure.core.async :refer [go >!]]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [datomic.api :as d]
            [eponai.server.auth :as a]
            [eponai.server.datomic.transact :as t]
            [eponai.server.datomic.pull :as p]
            [eponai.server.http :as h]
            [eponai.server.middleware.api :as m]
            [ring.middleware.defaults :as r]
            [ring.middleware.gzip :as gzip]))

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

; Actions

(defn signup
  "Create a new user and transact into datomic."
  [conn {:keys [params email-chan] :as request}]
  (if-not (p/user conn (params :username))
    (let [tx (t/new-user conn (a/new-signup request))]
      (go (>! email-chan [(:db-after tx) (params :username)]))
      tx)
    (throw (ex-info "User already exists."
                    {:cause   ::a/authentication-error
                     :status  ::h/unathorized
                     :data    {:username (params :username)}
                     :message "User already exists."}))))


(defn verify [conn uuid]
  (let [db (d/db conn)
        verification (p/verification db uuid)]
    (if (= (:db/id (verification :verification/status))
           (d/entid db :verification.status/pending))
      (t/add conn (:db/id verification) :verification/status :verification.status/verified)
      (throw (ex-info "Trying to activate invalid verification."
                      {:cause   ::a/verification-error
                       :status  ::h/unathorized
                       :data    {:uuid uuid}
                       :message "The verification link is no longer valid."})))))

;----------Routes

(defroutes user-routes
           (POST "/" {:keys [body
                             ::m/conn
                             currency-chan
                             ::m/parser] :as req}
             (h/response
               (parser
                 {:state conn
                  :auth (friend/current-authentication req)
                  :currency-chan currency-chan}
                 body))))

(defroutes
  api-routes
  (context "/api" []
    (POST "/signup" request (do
                              (h/response (signup (::m/conn request) request))))
    (GET "/schema" request
      (let [db (d/db (::m/conn request))]
        (h/response (p/inline-value db
                                    (p/schema db)
                                    [[:db/valueType :db/ident]
                                     [:db/unique :db/ident]
                                     [:db/cardinality :db/ident]]))))
    ; Anonymous
    (GET "/login" request (if (friend/identity request)
                            (h/redirect "user/txs")
                            (str "<h2>Login</h2>\n \n<form action=\"/login\" method=\"POST\">\n
            Username: <input type=\"text\" name=\"username\" value=\"\" /><br />\n
            Password: <input type=\"password\" name=\"password\" value=\"\" /><br />\n
            <input type=\"submit\" name=\"submit\" value=\"submit\" /><br />")))

    (GET "/signup" request (if (friend/identity request)
                             (h/redirect "user/txs")
                             (str "<h2>Signup</h2>\n \n<form action=\"/signup\" method=\"POST\">\n
            Username: <input type=\"text\" name=\"username\" value=\"\" /><br />\n
            Password: <input type=\"password\" name=\"password\" value=\"\" /><br />\n
            <input type=\"submit\" name=\"submit\" value=\"submit\" /><br />")))

    ; Requires user login
    (context "/user" [] (friend/wrap-authorize user-routes #{::a/user}))
    (GET "/verify/:uuid" [uuid :as request]
      (do
        (verify (::m/conn request) uuid)
        (h/response {:message "Your email is verified, you can now login."})))
    (friend/logout (ANY "/logout" [] (h/redirect "/")))))