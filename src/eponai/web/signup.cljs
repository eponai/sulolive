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
            [eponai.common.parser :as parser]))

(enable-console-print!)

(defn on-input-change [this k]
  #(om/update-state! this assoc k (.-value (.-target %))))

(defui LogIn
  static om/IQuery
  (query [_])
  Object
  (initLocalState [this]
    (let [{:keys [email]} (om/props this)]
      {:input-email email
       :verification-sent false}))
  (render [this]
    (let [{:keys [input-email
                  verification-sent]:as st} (om/get-state this)]
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
           [:a
            {:class       "button"
             :on-click    #(do
                            (om/update-state! this assoc :verification-sent true)
                            (om/transact! this `[(signup/email ~(assoc st :device :web))]))
             :on-key-down #(do (prn "Pressed key") (utils/on-enter-down % (fn [_]
                                                                            (prn "Pressed enter: ")
                                                                            (om/update-state! this assoc :verification-sent true)
                                                                            (om/transact! this `[(signup/email ~(assoc st :device :web))]))))
             :tab-index   2}
            "Sign In"]]

          [:br]
          ;; --------- Social Buttons
          [:h4 "or"]
          [:hr.intro-divider-landing]
          [:form
           {:action "/api/login/fb"
            :method "POST"}
           [:a
            {:class "button btn-facebook"
             :type  "submit"
             :tab-index 3}
            [:i
             {:class "fa fa-facebook fa-fw"}]
            [:span
             {:class "network-text"}
             "Sign in with Facebook"]]]]]))))

(def ->log-in (om/factory LogIn))

(defui CreateAccount
  Object
  (initLocalState [this]
    (let [{:keys [full-name email]} (om/props this)]
      {:input-email email
       :input-name full-name
       :status nil
       :message nil}))
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
           (opts {:value       input-email
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
            :on-click (fn [e]
                        (go
                          (let [response (async/<! (http/post
                                                     "/api/login/create"
                                                     {:transit-params {:user-uuid  (str user-uuid)
                                                                       :user-email input-email}}))
                                status (get-in response [:body :status])
                                message (get-in response [:body :message])]
                            (if-not (= (:status response) 200)
                              (om/update-state! this assoc :message message :status status)
                              (set! js/document.location.href (routes/key->route :route/home))))))}
           "Create account"]

          [:p.small
           "By creating an account, you accept JourMoney's"
           [:a {:class "btn btn-link btn-xs"} "Terms of Service"]
           " and "
           [:a {:class "btn btn-link btn-xs"} "Privacy Policy"]]]]))))

(def ->create-account (om/factory CreateAccount))

(defui Signup
  static om/IQueryParams
  (params [_]
    (let [url (url/url (-> js/window .-location .-href))]
      (keywordize-keys (:query url))))
  static om/IQuery
  (query [_]
    '[:datascript/schema
      {(:query/user {:uuid ?uuid}) [:user/uuid
                                    :user/email]}])

  Object
  (render [this]
    (let [{:keys [query/fb-user query/user]} (om/props this)
          {:keys [fb fail new uuid]} (om/get-params this)]
      (cond
        fail
        (html [:div "Could not sign in with email."])

        new
        (cond
          fb
          (->create-account {:type         :fb
                             :user-uuid    (:user/uuid (:fb-user/user fb-user))
                             :full-name    (:fb-user/name fb-user)
                             :email        (:user/email (:fb-user/user fb-user))
                             ::fixed-email false})

          uuid
          (->create-account {:type         :email
                             :user-uuid    (:user/uuid user)
                             :email        (:user/email user)
                             ::fixed-email true}))
        true
        (->log-in)))))

(defn run []
 (let [conn       (utils/init-conn)
       reconciler (om/reconciler {:state   conn
                                  :parser  (parser/parser)
                                  :remotes [:remote]
                                  :send    (backend/send! {:remote (backend/post-to-url homeless/om-next-endpoint-public)})
                                  :merge   (merge/merge! web.merge/web-merge)
                                  :migrate nil})]

  (om/add-root! reconciler Signup (gdom/getElement "my-signup"))))