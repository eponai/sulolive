(ns eponai.server.email
  (:require [eponai.server.http :as h]
            [postal.core :as email]
            [datomic.api :as d]
            [environ.core :refer [env]]
            [taoensso.timbre :refer [debug error info]]
            [eponai.server.datomic.pull :as p]
            [hiccup.page :refer [xhtml]]))

(declare html-content)
(declare text-content)

(defn- smtp []
  {:host (env :smtp-host)
   :user (env :smtp-user)
   :pass (env :smtp-pass)
   :tls  (env :smtp-tls)
   :port (env :smtp-port)})

(defn- send-email
  "Send a verification email to the provided address with the given uuid.
  Will send a link with the path /verify/:uuid that will verify when the user opens the link."
  [smtp address uuid user-status]
  (let [link (str "http://localhost:3000/verify/" uuid)
        body {:from    "info@gmail.com"
              :to      "dianagren@gmail.com"
              :subject (if (= user-status :user.status/new)
                         "Create your account on JourMoney"
                         "Sign in to JourMoney")
              :body    [:alternative
                        {:type    "text/plain"
                         :content (text-content link user-status)}
                        {:type    "text/html"
                         :content (html-content link user-status)}
                        ]}
        status (email/send-message smtp body)]

    (debug "Sent verification email to uuid: " uuid "with status:" status)
    (if (= 0 (:code status))
      status
      (throw (ex-info (:message status) {:cause   ::email-error
                                         :status  ::h/service-unavailable
                                         :message (:message status)
                                         :data    {:email address
                                                   :uuid  uuid
                                                   :error (:error status)}})))))
(defn send-email-fn
  "Function that checks if there's any pending verification for the provided user emal,
  and sends a verification email if so.

  Conn is the connection to the database."
  [conn]
  (fn [verification user-status]
    (let [db (d/db conn)
          uuid (:verification/uuid verification)]

      (cond (p/verification db '[:verification/value] (str uuid))
            (send-email (smtp)
                        (:verification/value verification)
                        uuid
                        user-status)))))

(defn text-content [link user-status]
  (if (= user-status :user.status/new)
    (str "Click and confirm that you want to create an account on JourMoney. This link will expire in 15 minutes and can only be used once.\n" link)
    (str "Sign in to JourMoney. This link will expire in 15 minutes and can only be used once" link)))

(defn html-content [link user-status]
  (xhtml
    [:head
     [:meta
      {:content "text/html; charset=UTF-8", :http-equiv "Content-Type"}]
     [:meta
      {:content "width=device-width, initial-scale=1.0",
       :name    "viewport"}]]
    [:body
     {:style "margin: 0; padding: 0;"
      :bgcolor "#FDFFFC"}
     [:table
      {:align   "center",
       :style   "padding: 40px 30px 40px 30px;color:#01213d;"}
      [:tr
       [:td
        {:align "center", :style "padding:1em;color:#fff"}
        [:p
         {:style
          "background:#01213d;border-radius:50%;height:30px;width:30px;display:inline-block;margin:1em;"}]
        [:p
         {:style
          "background:#01213d;border-radius:50%;height:30px;width:30px;display:inline-block;margin:1em;"}]
        [:p
         {:style
          "background:#01213d;border-radius:50%;height:30px;width:30px;display:inline-block;margin:1em;"}]]]
      [:tr
       [:td
        {:align "center",
         :style
                "font-size: 18px; padding-top: 2em;border-top:1px solid #e5e5e5;"}
        (if (= user-status :user.status/new)
          "Click and confirm that you want to create an account on JourMoney."
          "Sign in to JourMoney.")
        [:br]]]
      [:tr
       [:td
        {:align "center", :style "font-size:16px;color:gray;padding-top:0.5em;"}
        "This link will expire in 15 minutes and can only be used once."]]
      [:tr
       [:td
        {:align "center", :style "padding: 2em;"}
        [:a
         {:href link
          :style
                "text-decoration:none;display:inline-block; border-radius:10px; padding:16px 20px;font-size:16px;border:1px solid transparent;background:#2EC4B6;color:#fff;font-weight:bold;"}
         (if (= user-status :user.status/new)
           "Create account"
           "Sign in")]]]
      [:tr
       [:td
        {:align "center", :style "padding-bottom:4em;"}
        (if (= user-status :user.status/new)
          "Or create an account using this link:"
          "Or sign in using this link:")
        [:br]
        [:a {:href link} link]]]
      [:tr
       [:td
        {:align "center",
         :style
                "color:gray;padding-top:2em;border-top:1px solid #e5e5e5;"}
        "This email was sent by JourMoney."
        [:br]
        "eponai hb"
        [:br]
        "Stockholm, Sweden"]]]]))