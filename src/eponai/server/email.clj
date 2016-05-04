(ns eponai.server.email
  (:require [eponai.server.http :as h]
            [postal.core :as email]
            [datomic.api :as d]
            [environ.core :refer [env]]
            [taoensso.timbre :refer [debug error info warn]]
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
  (let [port (env :smtp-port)
        tls (env :smtp-tls)]
    {:host (env :smtp-host)
     :user (env :smtp-user)
     :pass (env :smtp-pass)
     :tls  (cond-> tls (string? tls) Boolean/parseBoolean)
     :port (cond-> port (string? port) Integer/parseInt)}))

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
        device->link {:web (str schema "://" host "/verify/")
                      :ios "jourmoney://ios/1/login/verify/"}
        _ (when-not (contains? device->link device)
            (warn "Will create verify link for unknown device: " device
                  " defaulting device to :web"))
        device (or device :web)
        verify-link (str (get device->link device) uuid)]
    (debug "Returning verify link: " verify-link)
    verify-link))

(defn- send-email [address subject body]
  (debug "Sending email... ")
  (let [content {:from    "info@jourmoney.com"
                 :to      address
                 :subject subject
                 :body    body}
        status (email/send-message (smtp) content)]
    (if (= 0 (:code status))
      status
      (throw (ex-info (:message status) {:cause   ::email-error
                                         :status  ::h/service-unavailable
                                         :message (:message status)
                                         :content content
                                         :error   (:error status)})))))

(defn send-payment-reminder-email
  [address]
  (let [subject "Payment failed"
        body [:alternative
              {:type    "text/plain"
               :content "We tried to charge your card, but it failed. Check your payment settings. "}
              {:type    "text/html"
               :content (html-content ""
                                      "Payment failed"
                                      "We tried to charge your card, but it failed. Check your payment settings."
                                      "Update payment preferences"
                                      "link -message")}]
        status (send-email address subject body)]
    (debug "Payment reminder sent to: " address " with status: " status)))

(defn send-invitation-email
  [verification {:keys [user-status inviter]}]
  (let [{:keys [verification/uuid]
         address :verification/value} verification
        link (verify-link-by-device :web uuid)
        body [:alternative
              {:type    "text/plain"
               :content (text-content link user-status)}
              {:type    "text/html"
               :content (html-content link
                                      (invite-subject inviter user-status)
                                      (message user-status)
                                      (button-title user-status)
                                      (link-message user-status))}]
        status (send-email address "You're invited to share project." body)]
    (debug "Sent invitation email to uuid: " uuid "with status:" status)))

(defn send-verification-email
  "Send a verification email to the provided address with the given uuid.
  Will send a link with the path /verify/:uuid that will verify when the user opens the link."
  [verification {:keys [user-status device]}]
  (debug "Send email for verification: " verification)
  (when verification
    (let [{:keys   [verification/uuid]
           address :verification/value} verification
          link (verify-link-by-device device uuid)
          body [:alternative
                {:type    "text/plain"
                 :content (text-content link user-status)}
                {:type    "text/html"
                 :content (html-content link
                                        (subject user-status)
                                        (message user-status)
                                        (button-title user-status)
                                        (link-message user-status))}]
          status (send-email address (subject user-status) body)]
      (debug "Sent verification email to uuid: " uuid "with status:" status))))

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