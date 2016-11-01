(ns eponai.web.signup
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.next :as om :refer-macros [defui]]
            [eponai.client.ui :refer-macros [opts]]
            [cemerick.url :as url]
            [clojure.walk :refer [keywordize-keys]]
            [sablono.core :refer-macros [html]]
            [goog.object :as gobj]
            [goog.dom :as gdom]
    ;; To initialize ReactDOM:
            [cljsjs.react.dom]
            [eponai.web.homeless :as homeless]
            [eponai.client.backend :as backend]
            [eponai.client.remotes :as remotes]
            [eponai.client.utils :as utils]
            [eponai.web.ui.utils :as web-utils]
            [eponai.client.parser.merge :as merge]
            [eponai.web.parser.merge :as web.merge]
            [eponai.common.parser :as parser]
            [taoensso.timbre :refer-macros [debug]]))

(enable-console-print!)

(defn on-input-change [this k]
  #(om/update-state! this assoc k (.-value (.-target %))))

(defui LogIn
  static om/IQuery
  (query [_])
  Object
  (on-email-login [this]
    (let [{:keys [input-email] :as st} (om/get-state this)]
      (if (some? input-email)
        (om/update-state! this assoc :status :pending-verification)
        (om/update-state! this assoc :status :invalid-email))
      (om/transact! this `[(session.signin/email ~(assoc st :device :web))])))

  (on-facebook-login [this]
    (.login js/FB
            (fn [response]
              (let [status (gobj/get response "status")]
                (debug "Facebook login status: " status)
                (debug "Faceboko respose: " response)
                (cond
                  (= status "connected")
                  (let [auth-response (gobj/get response "authResponse")
                        user-id (gobj/get auth-response "userID")
                        access-token (gobj/get auth-response "accessToken")]
                    (om/transact! this `[(session.signin/facebook ~{:user-id      user-id
                                                                    :access-token access-token})
                                         :user/current
                                         :query/auth]))
                  (= status "not_authorized")
                  (debug "User logged in on Facebook but not authorized on JourMoney.")

                  :else
                  (debug "User not authorized on Facebook"))))))
  (initLocalState [this]
    (let [{:keys [email]} (om/props this)]
      {:input-email email
       :verification-sent false}))
  (render [this]
    (let [{:keys [input-email status]} (om/get-state this)]
      (html
        [:div.content-section
         [:h1.title "Start tracking today!"]
         [:hr.intro-divider-landing]
         [:a.button.facebook
          {:on-click #(.on-facebook-login this)}
          [:i
           {:class "fa fa-facebook fa-fw"}]
          "Continue with Facebook"]
         [:p.divider "or"]
         ;[:h2 "Sign in with email"]

         [:div.input-section
          (cond (= status :pending-verification)
                [:small.message
                 "Check your inbox for a fancy sign in link!"]
                (= status :invalid-email)
                [:small.message
                 "Please provide an email address so we can send you a sign in link."]
                :else [:small.message])
          [:input
           (opts {:value       (or input-email "")
                  :on-change   (on-input-change this :input-email)
                  :type        :email
                  :placeholder "youremail@example.com"
                  :tab-index   1})]

          ;[:div
          ; (opts {:style {:height "2em"}})
          ; (when verification-sent
          ;   [:small.text-success "Check your inbox for a fancy sign in link!"])]
          ]


         [:a.button
          {:on-click  #(.on-email-login this)
           :tab-index 2}
          "Sign in with Email"]

         ;[:br]
         ;; --------- Social Buttons
         ;[:hr.intro-divider-landing]

         [:p.accept-terms
          "By signing in, you accept JourMoney's"
          [:a.link {:class "btn btn-link btn-xs"} "Terms of Service"]
          " and "
          [:a.link {:class "btn btn-link btn-xs"} "Privacy Policy"]]
         ]))))

(def ->log-in (om/factory LogIn))

(defui CreateAccount
  static om/IQuery
  (query [_]
    [
     {:query/fb-user [:fb-user/name
                      :fb-user/id]}])
  web-utils/ISyncStateWithProps
  (props->init-state [this props]
    (let [{:keys [user]} (::om/computed props)]
      (debug "User: " user)
      {:input-email (:user/email user)}))
  Object
  (initLocalState [this]
    (web-utils/props->init-state this (om/props this)))

  (componentWillReceiveProps [this new-props]
    (web-utils/sync-with-received-props this new-props))

  (fb-firstname [_ fb-user]
    (let [full-name (:fb-user/name fb-user)]
      (when full-name
        (first (clojure.string/split full-name #" ")))))

  (create-account [this user input-email]
    (om/update-state! this assoc :status :pending-verification)
    (om/transact! this `[(session.signin/activate ~{:user-uuid  (str (:user/uuid user))
                                                    :user-email input-email})
                         :user/current
                         :query/auth]))

  (render [this]
    (let [{:keys [query/fb-user]} (om/props this)
          {:keys [input-email status]} (om/get-state this)
          {:keys [user]} (om/get-computed this)
          fb-name (.fb-firstname this fb-user)]

      (html
        [:div.content-section
         [:h1 (str "Hi there" (when fb-name (str ", " fb-name)) "!")]
         [:p "We didn't find a verified email for your Facebook account, and we kinda need it to log you in. Please provide an email to associate with your JourMoney account."]

         [:p
          "You'll also be able to sign in with your email after it's been verified."]
         [:div.input-section

          [:input.email
           (opts {:value       (or input-email "")
                  :on-change   (on-input-change this :input-email)
                  :placeholder "youremail@example.com"
                  :name        "user-email"
                  :type        :email})]

          [:small.message
           (cond (= status :pending-verification)
                 "We've sent you an email verification, check your email to sign in!"

                 (= status :invalid-email)
                 "Please provide an email address."

                 (= status :invalid-user)
                 "Oops, we can't find your account, try to log in again."
                 :else
                 " ")]
          ;[:br]
          ;[:label
          ; "Name:"]
          ;
          ;[:input.username
          ; (opts {:value     input-name
          ;        :on-change (on-input-change this :input-name)
          ;        :name      "user-name"
          ;        :type      :text})]
          ]
         ;[:p
         ; (opts {:class (str "small "
         ;                    (cond (= status :login-failed)
         ;                          "text-danger"))
         ;        :style {:height "1em"}})
         ; message]


         [:a.button.action-button
          {:on-click (fn []
                       (cond (nil? (:user/uuid user))
                             (om/update-state! this assoc :status :invalid-user)
                             (nil? input-email)
                             (om/update-state! this assoc :status :invalid-email)
                             :else
                             (.create-account this user input-email)))}
          "Create account"]
         ;<form action="your_url" method="post">
         ;<button type="submit" name="your_name" value="your_value" class="btn-link">Go</button>
         ;</form>
         ;[:form
         ; {:action "/api/logout"
         ;  :method "post"}]
         [:a.button.hollow
          {:type "submit"
           :href "/api/logout"}
          "Sign Out"]

         ;[:p.accept-terms
         ; "By creating an account, you accept JourMoney's"
         ; [:a.link {:class "btn btn-link btn-xs"} "Terms of Service"]
         ; " and "
         ; [:a.link {:class "btn btn-link btn-xs"} "Privacy Policy"]]
         ]))))

(def ->create-account (om/factory CreateAccount))

(defui Signup
  static om/IQuery
  (query [_]
    [:datascript/schema
      :user/current
      {:query/auth [{:ui.singleton.auth/user [{:user/status [:db/ident]}
                                              :user/email
                                              :user/uuid]}]}
      {:proxy/create-account (om/get-query CreateAccount)}])

  Object
  (initLocalState [_]
    {:signup-path   "/signup"
     :activate-path "/activate"
     :app-path      "/app"})
  (navigate [this props]
    (let [{:keys [query/auth]} props
          {:keys [signup-path activate-path app-path]} (om/get-state this)
          user-status (get-in (:ui.singleton.auth/user auth) [:user/status :db/ident])
          current-path (:path (url/url (-> js/window .-location .-href)))]
      (cond
        (and
          (not= current-path signup-path)
          (nil? user-status))
        (set! js/window.location signup-path)

        (and (not= current-path activate-path)
             (= user-status :user.status/new))
        (set! js/window.location activate-path)

        (= user-status :user.status/active)
        (set! js/window.location app-path))))
  (componentWillReceiveProps [this next-props]
    (.navigate this next-props))
  (componentWillUpdate [this next-props _]
    (.navigate this next-props))
  (render [this]
    (let [{:keys [query/auth proxy/create-account]} (om/props this)
          {:keys [signup-path activate-path]} (om/get-state this)
          current-user (:ui.singleton.auth/user auth)
          current-path (:path (url/url (-> js/window .-location .-href)))]
      (debug "Create account props: " create-account)
      (html
        [:div.row.column#signup
         (cond (= current-path signup-path)
               (->log-in)
               (= current-path activate-path)
               (->create-account (om/computed create-account
                                              {:user current-user})))]))))

(defn run []
 (let [conn (utils/init-conn)
       reconciler (om/reconciler {:state   conn
                                  :parser  (parser/client-parser)
                                  :remotes [:remote]
                                  :send    (backend/send!
                                             utils/reconciler-atom
                                             {:remote (remotes/post-to-url homeless/om-next-endpoint-public)})
                                  :merge   (merge/merge! web.merge/web-merge)
                                  :migrate nil})]
   (reset! utils/reconciler-atom reconciler)
   (om/add-root! reconciler Signup (gdom/getElement "jm-signup"))))