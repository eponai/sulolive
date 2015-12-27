(ns eponai.server.api
  (:require [cemerick.friend :as friend]
            [clojure.core.async :refer [go >!]]
            [compojure.core :refer :all]
            [datomic.api :as d]
            [eponai.server.auth :as a]
            [eponai.server.datomic.transact :as t]
            [eponai.server.datomic.pull :as p]
            [eponai.server.http :as h]
            [eponai.server.middleware :as m]
            [ring.util.response :as r]))

; Auth stuff

(defn user-creds
  "Get user credentials for the specified email in the db. Returns nil if user does not exist.

  Throws ExceptionInfo if the user has not verified their email."
  [db email]
  (if-let [db-user (p/user db email)]
    (a/user->creds db-user
                   (p/password db db-user)
                   (p/verifications db db-user :user/email :verification.status/verified))
    (throw (a/not-found email))))

; Actions

(defn signup
  "Create a new user and transact into datomic."
  [{:keys [params ::m/email-chan ::m/conn]
    :as request}]
  (if-not (p/user (d/db conn) (params :username))
    (let [tx (t/new-user conn (a/new-signup request))]
      (when email-chan
        (go (>! email-chan [(:db-after tx) (params :username)])))
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
    ;(go (>! currency-chan (map :transaction/date user-data)))
    (t/user-txs conn user-data)))

(defn send-email-verification [email-fn [db email]]
  (when-let [verification (first (p/verifications db (p/user db email) :user/email :verification.status/pending))]
    (email-fn email (verification :verification/uuid))))

;----------Routes

(defroutes user-routes
           (POST "/" {:keys [body ::m/conn ::m/currency-chan ::m/parser]
                      :as req}
             (r/response
               (parser
                 {:state conn
                  :auth (friend/current-authentication req)
                  :currency-chan currency-chan}
                 body))))

(defroutes
  api-routes
  (context "/api" []
    (POST "/signup" request
      (r/response (signup request)))
    (GET "/schema" request
      (let [db (d/db (::m/conn request))]
        (r/response (p/inline-value db
                                    (p/schema db)
                                    [[:db/valueType :db/ident]
                                     [:db/unique :db/ident]
                                     [:db/cardinality :db/ident]]))))
    ; Requires user login
    (context "/user" [] (friend/wrap-authorize user-routes #{::a/user}))
    (GET "/verify/:uuid" [uuid :as request]
      (do
        (verify (::m/conn request) uuid)
        (r/response {:message "Your email is verified, you can now login."})))
    (friend/logout (ANY "/logout" [] (r/redirect "/")))))