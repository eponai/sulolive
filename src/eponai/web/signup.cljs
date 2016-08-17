(ns eponai.web.signup
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs-http.client :as http]
            [cljs.core.async :as async]
            [om.next :as om :refer-macros [defui]]
            [eponai.client.ui :refer-macros [opts]]
            [eponai.web.routes :as routes]
            [cemerick.url :as url]
            [clojure.walk :refer [keywordize-keys]]
            [sablono.core :refer-macros [html]]
            [datascript.core :as d]
            [goog.dom :as gdom]
    ;; To initialize ReactDOM:
            [cljsjs.react.dom]
            [eponai.web.ui.utils :as utils]
            [eponai.web.homeless :as homeless]
            [eponai.client.backend :as backend]
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
    (let [st (om/get-state this)]
      (om/update-state! this assoc :verification-sent true)
      (om/transact! this `[(session.signin/email ~(assoc st :device :web))])))

  (on-facebook-login [this]
    (.login js/FB
            (fn [response]
              (let [status (.-status response)]
                (cond
                  (= status "connected")
                  (let [auth-response (.-authResponse response)
                        user-id (.-userID auth-response)
                        access-token (.-accessToken auth-response)]
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
    (let [{:keys [input-email
                  verification-sent]} (om/get-state this)]
      (html
        [:div.row
         [:div
          {:class "columns small-12 medium-10 medium-offset-1 large-6 large-offset-3"}
          [:h2 "Welcome!"]
          [:p "Sign in with email"]

          [:input
           (opts {:value       (or input-email "")
                  :on-change   (on-input-change this :input-email)
                  :type        :email
                  :placeholder "youremail@example.com"
                  :tab-index   1})]

          [:div
           (opts {:style {:height "2em"}})
           (when verification-sent
             [:small.text-success "Check your inbox for a fancy sign in link!"])]

          [:div
           [:button
            {:class       "button"
             :on-click    #(.on-email-login this)
             :tab-index   2}
            "Sign In"]]

          [:br]
          ;; --------- Social Buttons
          [:h4 "or"]
          [:hr.intro-divider-landing]
          [:a.button.btn-facebook
           {:on-click #(.on-facebook-login this)}
           [:i
            {:class "fa fa-facebook fa-fw"}]
           "Continue with Facebook"]]]))))

(def ->log-in (om/factory LogIn))

(defui CreateAccount
  static om/IQuery
  (query [_])
  Object
  (componentWillReceiveProps [this next-props]
    (let [{:keys [full-name email]} next-props]
      (om/update-state! this assoc
                        :input-email email
                        :input-name full-name)))
  (render [this]
    (let [{:keys [::fixed-email user-uuid]} (om/props this)
          {:keys [input-name input-email message status]} (om/get-state this)]
      (html
        [:div.row
         [:div
          {:class "columns small-12 medium-10 medium-offset-1 large-6 large-offset-3"}
          [:h2 "Welcome!"]
          [:label
           "Email:"]
          [:input
           (opts {:value       (or input-email "")
                  :on-change   (on-input-change this :input-email)
                  :placeholder "youremail@example.com"
                  :name        "user-email"
                  :type        :email})]

          [:br]
          [:label
           "Name:"]

          [:input
           (opts {:value     input-name
                  :on-change (on-input-change this :input-name)
                  :name      "user-name"
                  :type      :text})]
          [:p
           (opts {:class (str "small "
                              (cond (= status :login-failed)
                                    "text-danger"))
                  :style {:height "1em"}})
           message]


          [:button
           {:class    "button primary"
            :on-click (fn []
                        (om/transact! this `[(session.signin/activate ~{:user-uuid (str user-uuid)
                                                                        :user-email input-email})
                                             :user/current
                                             :query/auth]))}
           "Create account"]

          [:p.small
           "By creating an account, you accept JourMoney's"
           [:a {:class "btn btn-link btn-xs"} "Terms of Service"]
           " and "
           [:a {:class "btn btn-link btn-xs"} "Privacy Policy"]]]]))))

(def ->create-account (om/factory CreateAccount))

(defui Signup
  static om/IQuery
  (query [_]
    '[:datascript/schema
      :user/current
      {:query/auth [{:ui.singleton.auth/user [{:user/status [:db/ident]}
                                              :user/email
                                              :user/uuid]}]}])

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
    (let [{:keys [query/auth]} (om/props this)
          {:keys [signup-path activate-path]} (om/get-state this)
          current-user (:ui.singleton.auth/user auth)
          current-path (:path (url/url (-> js/window .-location .-href)))]
      (cond (= current-path signup-path)
            (->log-in)
            (= current-path activate-path)
            (->create-account {:type      :fb
                               :user-uuid (:user/uuid current-user)
                               :email     (:user/email current-user)})))))

(defn run []
 (let [conn (utils/init-conn)
       reconciler (om/reconciler {:state   conn
                                  :parser  (parser/parser)
                                  :remotes [:remote]
                                  :send    (backend/send!
                                             utils/reconciler-atom
                                             {:remote (backend/post-to-url homeless/om-next-endpoint-public)})
                                  :merge   (merge/merge! web.merge/web-merge)
                                  :migrate nil})]
   (reset! utils/reconciler-atom reconciler)
   (om/add-root! reconciler Signup (gdom/getElement "my-signup"))))