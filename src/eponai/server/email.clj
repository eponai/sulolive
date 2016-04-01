(ns eponai.server.email
  (:require [eponai.server.http :as h]
            [postal.core :as email]
            [datomic.api :as d]
            [environ.core :refer [env]]
            [taoensso.timbre :refer [debug error info]]
            [eponai.common.database.pull :as p]
            [hiccup.page :refer [xhtml]]
            [garden.core :refer [css]]))

(declare html-content)
(declare text-content)

(defn invite-subject [inviter user-status]
  (str inviter
       " has invited you to share project. "
       (if (= user-status :user.status/new)
         "Create your account on JourMoney"
         "Sign in to JourMoney")))

(defn subject [user-status]
  (if (= user-status :user.status/new)
    "Create your account on JourMoney"
    "Sign in to JourMoney"))

(defn message [user-status]
  (if (= user-status :user.status/new)
    "Click and confirm that you want to create an account on JourMoney."
    "Sign in to JourMoney."))

(defn link-message [user-status]
  (if (= user-status :user.status/new)
    "Or create an account using this link:"
    "Or sign in using this link:"))

(defn button-title [user-status]
  (if (= user-status :user.status/new)
    "Create account"
    "Sign in"))

(defn- smtp []
  ;;TODO: Add a warning when this is being used in development.
  {:host (env :smtp-host)
   :user (env :smtp-user)
   :pass (env :smtp-pass)
   :tls  (cond-> (env :smtp-tls) string? Boolean/parseBoolean)
   :port (cond-> (env :smtp-port) string? Integer/parseInt)})

(defn url []
  (let [schema (env :jourmoney-server-url-schema)
        host (env :jourmoney-server-url-host)]
    (assert (and schema host)
            (str "Did not have required schema: " schema " or host: " host
                 ". Please set these keys in your environment:"
                 :jourmoney-server-url-schema " and "
                 :jourmoney-server-url-host))
    {:schema schema :host host}))

(defn- verify-link-by-device [device uuid]
  (debug "Will return verify link by device: " device " uuid: " uuid)
  (let [{:keys [schema host]} (url)
        verify-link (str (condp = device
                           :web (str schema "://" host "/verify/")
                           :ios "jourmoney://ios/1/login/verify/")
                         uuid)]
    (debug "Returning verify link: " verify-link)
    verify-link))

(defn send-email
  "Send a verification email to the provided address with the given uuid.
  Will send a link with the path /verify/:uuid that will verify when the user opens the link."
  [address uuid {:keys [subject text-content html-content device]}]
  (debug "Sending email... ")
  (let [link (verify-link-by-device device uuid)
        body {:from    "info@gmail.com"
              :to      address
              :subject subject
              :body    [:alternative
                        {:type    "text/plain"
                         :content (text-content link)}
                        {:type    "text/html"
                         :content (html-content link)}]}
        status (email/send-message (smtp) body)]

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
  (fn [verification content]
    (let [db (d/db conn)
          {:keys [verification/uuid]} verification]

      (cond (p/lookup-entity db [:verification/uuid uuid])
            (send-email (:verification/value verification)
                        uuid
                        content)))))

(defn text-content [link user-status]
  (if (= user-status :user.status/new)
    (str "Click and confirm that you want to create an account on JourMoney. This link will expire in 15 minutes and can only be used once.\n" link)
    (str "Sign in to JourMoney. This link will expire in 15 minutes and can only be used once" link)))

(defn html-content [link subject message button-title link-message]
  (xhtml
    [:head
     [:meta
      {:content "text/html; charset=UTF-8", :http-equiv "Content-Type"}]
     [:meta
      {:content "width=device-width, initial-scale=1.0",
       :name    "viewport"}]
     [:title
      subject]]
    [:body
     {:style "margin: 0; padding: 0;"
      :bgcolor "#FDFFFC"}
     [:table
      {:align   "center",
       :style   "color:01213d;"}
      [:tr
       [:td {:align "center"}
        [:table
         [:tr
          [:td
           {:align "center"}
           [:p {:style "font-size:18px;border-top:1px solid #e5e5e5;padding-top:1em;"}
            message]]
          [:tr
           [:td
            {:align "center"}
            [:p {:style "font-size:16px;color:#b3b3b1;"}
             "This link will expire in 15 minutes and can only be used once."]]]]
         [:tr
          [:td
           {:align "center"}
           [:p
            [:a
                {:href link
                 :style
                       "text-decoration:none;display:inline-block; border-radius:3px; padding:16px 20px;font-size:16px;border:1px solid transparent;background-color:#044e8a;color:#fff;font-weight:bold;"}
                button-title]]]]
         [:tr
          [:td
           {:align "center"}
           [:p
            link-message
            [:br]
            [:a {:href link} link]]]]
         [:tr
          [:td
           {:align  "center"}
           [:p {:style "color:#b3b3b1;border-top:1px solid #e5e5e5;padding:1em;"}
            "This email was sent by JourMoney."
            [:br]
            "eponai hb"
            [:br]
            "Stockholm, Sweden"]]]]]]]]))