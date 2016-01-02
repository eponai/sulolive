(ns eponai.server.email
  (:require [cemerick.friend.credentials :as creds]
            [eponai.server.http :as h]
            [postal.core :as email]
            [environ.core :refer [env]]))

(defn smtp []
  {:host (env :smtp-host)
   :user (env :smtp-user)
   :pass (env :smtp-pass)
   :tls  (env :smtp-tls)
   :port (env :smtp-port)})

(defn send-email-verification [smtp email uuid]
  (let [link (str "http://localhost:3000/verify/" uuid)
        body {:from "info@gmail.com"
              :to "dianagren@gmail.com"
              :subject "Hi there, this is your confirmation email."
              :body (str "Email: " email ". Click this link: " link)}
        status (email/send-message smtp body)]
    (println "Sent verification email with status: " status)
    (if (= 0 (:code status))
      status
      (throw (ex-info (:message status) {:cause ::email-error
                                         :status ::h/service-unavailable
                                         :message (:message status)
                                         :data {:email email
                                                :uuid uuid
                                                :error (:error status)}})))))
