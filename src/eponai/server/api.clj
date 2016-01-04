(ns eponai.server.api
  (:require [cemerick.friend :as friend]
            [clojure.core.async :refer [go >! <! chan put!]]
            [compojure.core :refer :all]
            [datomic.api :as d]
            [eponai.server.auth.credentials :as a]
            [eponai.server.datomic.transact :as t]
            [eponai.server.datomic.pull :as p]
            [eponai.server.http :as h]
            [eponai.server.middleware :as m]
            [ring.util.response :as r]))

; Actions

(defn signup
  "Create a new user and transact into datomic.

  Returns channel with username and db-after user is added to use for email verification."
  [conn {:keys [username] :as user-params}]
  (if-not (p/user (d/db conn) username)
    (let [tx (t/new-user conn (a/add-bcrypt user-params :password))
          email-chan (chan)]
      (println "Will put email on chan: " email-chan " with data: " [(:db-after tx) username])

      (put! email-chan username)
      email-chan)
    (throw (ex-info "User already exists."
                    {:cause   ::signup-error
                     :status  ::h/unathorized
                     :data    {:username username}
                     :message "User already exists."}))))

(defn verify [conn uuid]
  (let [db (d/db conn)
        verification (p/verification db uuid)]
    (if (= (:db/id (verification :verification/status))
           (d/entid db :verification.status/pending))
      (do
        (println "Successful verify")
        (t/add conn (:db/id verification) :verification/status :verification.status/verified))

      (throw (ex-info "Trying to activate invalid verification."
                      {:cause   ::verification-error
                       :status  ::h/unathorized
                       :data    {:uuid uuid}
                       :message "The verification link is no longer valid."})))))

(defn post-currencies [conn curs]
  (t/currencies conn curs))

(defn post-currency-info [conn cur-infos]
  (t/currency-infos conn cur-infos))

(defn post-currency-rates [conn rates-fn dates]
  (let [unconverted (clojure.set/difference (set dates)
                                            (p/converted-dates (d/db conn) dates))]
    (when (some identity unconverted)
      (t/currency-rates conn (map rates-fn (filter identity dates))))))

(defn handle-parser-request [conn {:keys [::m/parser ::m/currency-chan]
                                   :as request}]
  (let [ret (parser
              {:state         conn
               :auth          (friend/current-authentication request)
               :currency-chan currency-chan}
              (:body request))]
    ret))

;----------Routes

(defroutes
  user-routes
  (POST "/" {:keys [::m/conn] :as req}
    (r/response
      (handle-parser-request conn req))))

(defroutes
  api-routes
  (context "/api" []
    (POST "/signup" {:keys [::m/send-email-fn
                            params
                            ::m/conn]}
      (go (send-email-fn (<! (signup conn params))))
      (r/redirect "/login.html"))

    ; Requires user login
    (context "/user" _
      (friend/wrap-authorize user-routes #{::a/user}))

    (POST "/verify" {:keys [::m/conn] :as req}
      (r/response
        (handle-parser-request conn req)))

    (friend/logout (ANY "/logout" [] (r/redirect "/")))))
