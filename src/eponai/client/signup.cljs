(ns eponai.client.signup
 (:require [om.next :as om :refer-macros [defui]]
           [eponai.client.ui :refer [opts]]
           [cemerick.url :as url]
           [clojure.walk :refer [keywordize-keys]]
           [sablono.core :refer-macros [html]]
           [datascript.core :as d]
           [goog.dom :as gdom]
  ;; To initialize ReactDOM:
           [cljsjs.react.dom]
           [eponai.client.backend :as backend]
           [eponai.common.parser :as parser]))

(enable-console-print!)

(defn on-click-create-account []
  (fn []))

(defn on-input-change [this k]
  #(om/update-state! this assoc k (.-value (.-target %))))

(defui LogIn
  static om/IQuery
  (query [_])
  Object
  (initLocalState [this]
    (let [{:keys [email]} (om/props this)]
      {:input-email email}))
  (render [this]
    (let [{:keys [input-email]:as st} (om/get-state this)]
      (println "State " st)
      (html
        [:div
         [:label
          {:for "email-input"}
          "Email:"]
         [:input.form-control#email-input
          {:value     input-email
           :on-change (on-input-change this :input-email)}]
         [:br]

         [:div
          [:button
           {:class    "btn btn-info btn-lg"
            :on-click #(om/transact! this `[(signup/email ~st)])}
           "Sign In"]]

         ;; --------- Social Buttons
         [:h3 "or"]
         [:hr.intro-divider]
         [:form
          {:action "/api/login/fb"
           :method "POST"}
          [:button
           {:class "btn btn-facebook btn-lg"
            :type "submit"}
           [:i
            {:class "fa fa-facebook fa-fw"}]
           [:span
            {:class "network-text"}
            "Sign in with Facebook"]]]]))))

(def ->log-in (om/factory LogIn))

(defui CreateAccount
  Object
  (initLocalState [this]
    (let [{:keys [full-name email] :as props} (om/props this)]
      (println "Initing state: " props)
      {:input-email email
       :input-name full-name}))
  (render [this]
    (let [{:keys [::fixed-email user-uuid]} (om/props this)
          {:keys [input-name input-email]} (om/get-state this)]
      (println "User id: " user-uuid)
      (html
        [:form#sign-in-form
         {:action "/api/login/create"
          :method "POST"}
         [:label
          {:for "email-input"}
          "Email:"]

         [:input.form-control#email-input
          {:value     input-email
           :on-change (on-input-change this :input-email)
           :name      "user-email"}]

         [:br]
         ;[:label
         ; {:for "name-input"}
         ; "Name:"]
         ;
         ;[:input.form-control#name-input
         ; {:value input-name
         ;  :on-change (on-input-change this :input-name)
         ;  :name "name-input"}]
         ;[:br]

         [:input
          {:type "hidden"
           :value (str user-uuid)
           :name "user-uuid"}]

         [:input
          {:class "btn btn-info btn-lg"
           :type  "submit"
           :value "Create account"}]

         [:br]
         [:div
          "By creating an account, I accept JourMoney's"
          [:a {:class "btn btn-link btn-xs"} "Terms of Service"]
          " and "
          [:a {:class "btn btn-link btn-xs"} "Privacy Policy"]]]))))

(def ->create-account (om/factory CreateAccount))

(defui Signup
  static om/IQueryParams
  (params [_]
    (let [url (url/url (-> js/window .-location .-href))]
      (keywordize-keys (:query url))))
  static om/IQuery
  (query [_]
    '[:datascript/schema
      {(:query/fb-user {:fb ?fb}) [:fb-user/id
                                   :fb-user/name
                                   :fb-user/email
                                   :fb-user/user [:user/uuid]]}
      {(:query/user {:uuid ?uuid}) [:user/uuid
                                    :user/email]}])

  Object
  (render [this]
    (let [{:keys [query/fb-user query/user] :as props} (om/props this)
          {:keys [fb fail new uuid]} (om/get-params this)]
      (println "props: " props)
      (cond
        fail
        (html [:div "Could not sign in with email."])

        new
        (cond
          fb
          (->create-account {:type         :fb
                             :user-uuid    (:user/uuid (:fb-user/user fb-user))
                             :full-name    (:fb-user/name fb-user)
                             :email        (:fb-user/email fb-user)
                             ::fixed-email false})

          uuid
          (->create-account {:type         :email
                             :user-uuid    (:user/uuid user)
                             :email        (:user/email user)
                             ::fixed-email true}))
        true
        (->log-in)))))

(defonce conn-atom (atom nil))

(defn run []
 (let [conn (or @conn-atom (reset! conn-atom (d/create-conn)))
       reconciler (om/reconciler {:state conn
                                  :parser  (parser/parser)
                                  :remotes [:remote]
                                  :send    (backend/send! "/")
                                  :merge   (backend/merge! conn)})]

  (om/add-root! reconciler Signup (gdom/getElement "my-signup"))))