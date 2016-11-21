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
         "Create your account on Jourmoney"
         "Sign in to Jourmoney")))

(defn subject [user-status]
  (if (= user-status :user.status/new)
    "Create your account on Jourmoney"
    "Sign in to Jourmoney"))

(defn message [user-status]
  (if (= user-status :user.status/new)
    "Click and confirm that you want to create an account on Jourmoney."
    "Sign in to Jourmoney."))

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
                      :ios "jourmoney://login/verify/"}
        _ (when-not (contains? device->link device)
            (warn "Will create verify link for unknown device: " device
                  " defaulting device to :web"))
        device (or device :web)
        verify-link (str (get device->link device) uuid)]
    (info "Returning verify link: " verify-link)
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
  [in-prod? verification {:keys [user-status inviter]}]
  (let [{:keys   [verification/uuid]
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
                                      (link-message user-status))}]]
    (if in-prod?
      (let [subject-txt (if-let [invite-email (not-empty inviter)]
                          (str invite-email " has invited you to share project" (when (= user-status :user.status/new) " on Jourmoney") ".")
                          "You're invited to share project.")
            status (send-email address  subject-txt body)]
        (debug "Sent invitation email to uuid: " uuid "with status:" status))
      (debug "Dev mode. Did not send invitation email to uuid: " uuid))))

(defn send-verification-email
  "Send a verification email to the provided address with the given uuid.
  Will send a link with the path /verify/:uuid that will verify when the user opens the link."
  [in-prod? verification {:keys [user-status device]}]
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
                                        (link-message user-status))}]]
      (if in-prod?
        (let [status (send-email address (subject user-status) body)]
          (debug "Sent verification email to uuid: " uuid "with status:" status))
        (debug "Dev mode. Did not send verification email to uuid: " uuid)))))

(defn text-content [link user-status]
  (if (= user-status :user.status/new)
    (str "Click and confirm that you want to create an account on Jourmoney. This link will expire in 15 minutes and can only be used once.\n" link)
    (str "Sign in to Jourmoney. This link will expire in 15 minutes and can only be used once" link)))

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
      :bgcolor "white"}
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
                       "text-decoration:none;display:inline-block; border-radius:3px; padding:16px 20px;font-size:16px;border:1px solid transparent;border-radius:100px;background-color:#ED9C40;color:#fff;font-weight:500;"}
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
            "This email was sent by Jourmoney."
            [:br]
            "eponai hb"
            [:br]
            "Stockholm, Sweden"]]]]]]]]))