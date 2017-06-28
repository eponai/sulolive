(ns eponai.web.ui.login
  (:require
    [clojure.spec :as s]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.router :as router]
    [om.next :as om :refer [defui]]
    [eponai.web.ui.button :as button]
    [eponai.common.ui.elements.css :as css]
    [eponai.web.ui.modal :as modal]
    [eponai.common.ui.elements.grid :as grid]
    #?(:cljs
       [eponai.web.utils :as web-utils])
    [taoensso.timbre :refer [debug]]
    [eponai.web.ui.photo :as photo]
    [eponai.client.routes :as routes]
    [eponai.client.utils :as client-utils]
    [eponai.common :as c]
    [eponai.common.ui.elements.input-validate :as v]
    [eponai.client.parser.message :as msg]))

(def form-inputs
  {::email    "sulo.login.email"
   ::username "sulo.login.name"
   ::code     "sulo.login.code"})

(s/def ::email #(client-utils/valid-email? %))
(s/def ::username (s/and #(string? (not-empty %))
                         #(< 3 (count %))))
(s/def ::code (s/and #(number? (c/parse-long-safe %))
                     #(= 6 (count %))))

(s/def ::create-account (s/keys :req [::username ::email]))

(defn redirect-to [path]
  #?(:cljs
     (str js/window.location.origin path)))

(defui Login
  static om/IQuery
  (query [this]
    [:query/current-route
     :query/messages])

  Object
  (authorize-social [this provider]
    #?(:cljs
       (when-let [auth0 (:auth0 (om/get-state this))]
         (.authorize auth0 #js {:connection (name provider)}))))
  (authorize-email [this]
    #?(:cljs
       (do
         (om/update-state! this dissoc :error-message)
         (when-let [auth0 (:auth0 (om/get-state this))]
           (let [email (web-utils/input-value-by-id (::email form-inputs))
                 {:keys [login-state user]} (om/get-state this)

                 params (cond-> {:connection "email" :send "code" :email email}
                                (some? (:user-id user))
                                (assoc :redirectUri (redirect-to (routes/url :auth nil {:user-id (:user-id user)}))))]
             (debug "Params: " params)
             (.passwordlessStart auth0 (clj->js params)
                                 (fn [err res]
                                   (let [error-code (when err (.-code err))
                                         error-message (cond (= error-code "bad.email")
                                                             "Please provide a valid email")]
                                     (om/update-state! this (fn [st]
                                                              (cond-> (assoc st :input-email email)
                                                                      (some? error-message)
                                                                      (assoc :error-message error-message)
                                                                      (nil? error-message)
                                                                      (assoc :login-state :verify-email))))
                                     ;(om/update-state! this assoc :input-email email :login-state :verify-email)
                                     )
                                   (debug "Response: " res)
                                   (debug "Error: " err))))))))
  (verify-email [this]
    #?(:cljs
       (let [{:keys [auth0 auth0-manage input-email user]} (om/get-state this)
             {:query/keys [current-route]} (om/props this)
             {:keys [query-params]} current-route]
         (when auth0
           (let [code (web-utils/input-value-by-id (::code form-inputs))]
             (when (:token query-params)
               (web-utils/set-cookie "sulo.primary" (:token query-params)))
             ;(.setItem js/localStorage "old.token" (:token query-params))
             (.passwordlessVerify auth0 #js {:connection       "email"
                                             :email            input-email
                                             :verificationCode code}
                                  (fn [err res]
                                    ;(when (and res auth0-manage)
                                    ;  (.linkUser auth0-manage (.-user_id res)))
                                    (debug "Verified response: " res)
                                    (debug "Verified error: " err))))))))

  (create-account [this]
    #?(:cljs
       (let [{:keys [user]} (om/get-state this)
             email (or (:email user)
                       (web-utils/input-value-by-id (::email form-inputs)))
             username (web-utils/input-value-by-id (::username form-inputs))
             validation (v/validate ::create-account {::email    email
                                                      ::username username} form-inputs)]
         (debug "Validation: " validation)
         (when (nil? validation)
           (msg/om-transact! this [(list 'user/create {:user {:user/email    email
                                                              :user/profile  {:user.profile/name  username}
                                                              :user/verified (:email-verified? user)}
                                                       :auth0-user user})]))
         (om/update-state! this assoc :input-validation validation))))

  (componentDidUpdate [this _ _]
    (let [{:query/keys [current-route]} (om/props this)
          create-msg (msg/last-message this 'user/create)]
      (debug "Got create message: " create-msg)
      (when (msg/final? create-msg)
        (let [message (msg/message create-msg)]
          (debug "Got message: " message)
          (msg/clear-messages! this 'user/create)
          (if (msg/success? create-msg)
            (let [new-user (:user message)]
              (if (:user/verified new-user)
                (do
                  (debug "User created routing to: " (routes/url :auth nil (:query-params current-route)))
                  #?(:cljs
                     (set! js/window.location (routes/url :auth nil (:query-params current-route))))
                  ;(routes/set-url! this :login nil (:query-params current-route))
                  )
                (.authorize-email this)))
            (om/update-state! this assoc :error/create-user message))))))

  (componentDidMount [this]
    #?(:cljs
       (let [{:query/keys [current-route]} (om/props this)
             {:keys [query-params]} current-route]
         (debug "Current route: " current-route)
         (let [web-auth (new js/auth0.WebAuth #js {:domain       "sulo.auth0.com"
                                                   :clientID     "olXSYZ7HDqCif7GtNEaxi6jKX9Lk72OR"
                                                   :redirectUri  (redirect-to (routes/url :auth))
                                                   :responseType "code"
                                                   :scope        "openid email"
                                                   })]
           (when (:access_token query-params)
             (.userInfo (.-client web-auth)
                        (:access_token query-params)
                        (fn [err user]
                          (cond
                            err
                            (om/update-state! this assoc :token-error {:code (.-code err)})
                            user
                            (om/update-state! this assoc :user {:user-id         (.-user_id user)
                                                                :email           (.-email user)
                                                                :email-verified? (boolean (.-email_verified user))
                                                                :nickname        (or (.-given_name user)
                                                                                     (.-screen_name user)
                                                                                     (.-nickname user))
                                                                :picture         (.-picture_large user)}))
                          (debug "User info: " user)
                          (debug "error " err))))
           (om/update-state! this assoc :auth0 web-auth)))))

  (initLocalState [this]
    {:login-state :login})
  (render [this]
    (let [{:query/keys [current-route]} (om/props this)
          {:keys [route route-params query-params]} current-route
          {:keys [access_token token]} query-params
          {:keys [login-state error-message input-validation user token-error]} (om/get-state this)]
      (debug "State " (om/get-state this))

      (dom/div
        (css/text-align :center {:id "sulo-login"})

        (if (= route :login)
          (if (some? access_token)
            (dom/h4 nil "Almost there")
            (dom/div
              (css/add-class :header-container)
              (photo/circle {:src "assets/img/auth0-icon.png"})
              (dom/h1 nil "SULO Live")))
          (dom/h4 nil "Sign up or sign in"))



        (cond
          (and (= route :login) (= login-state :login) access_token)
          [(dom/p nil (dom/span nil "Finish creating your SULO Live account"))
           (dom/p nil (dom/a {:href (routes/url :login)} (dom/span nil "I already have an account")))

           (dom/div
             (css/add-class :login-content)
             (if-not (or user token-error)
               (dom/p nil (dom/i {:classes ["fa fa-spinner fa-pulse"]}))
               [
                ;(when (:picture user)
                ;  (photo/circle {:src (:picture user)}))
                (dom/label nil "Email")

                (v/input
                  (cond-> {:type         "email"
                           :id           (::email form-inputs)
                           :placeholder  "youremail@example.com"
                           :defaultValue (:email user)}
                          (not-empty (:email query-params))
                          (assoc :disabled true))
                  input-validation)

                (dom/label nil "Name")
                (v/input {:type         "text"
                          :id           (::username form-inputs)
                          :placeholder  "Your name"
                          :defaultValue (:nickname user)}
                         input-validation)])

             (dom/p (css/add-class :info)
                    (dom/small nil "By creating an account you accept our ")
                    (dom/a nil (dom/small nil "Terms of Service"))
                    (dom/small nil " and ")
                    (dom/a {:href      "//www.iubenda.com/privacy-policy/8010910"
                            :className "iubenda-nostyle no-brand iubenda-embed"
                            :title     "Privacy Policy"
                            :target    "_blank"} (dom/small nil "Privacy Policy")))
             (button/default-hollow
               (css/add-classes [:expanded :sulo-dark] {:onClick #(.create-account this)})
               (dom/span nil "Create account")))]


          (= login-state :verify-email)
          [(dom/p nil (dom/span nil "We sent you a code to sign in. Please check your inbox and provide the code below."))
           (dom/div
             (css/add-class :login-content)
             (dom/label nil "Code")
             (dom/input {:id           (::code form-inputs)
                         :type         "number"
                         :placeholder  "000000"
                         :maxLength    6
                         :defaultValue ""})
             (button/default-hollow
               (css/add-class :sulo-dark {:onClick #(.verify-email this)})
               (dom/span nil "Sign me in"))
             )]

          (= login-state :login-email)
          [(dom/p nil (dom/span nil "Enter your email address to sign in or create an account on SULO Live"))
           (dom/div
             (css/add-class :login-content)
             ;(dom/label nil "Email")
             (dom/input
               (cond->> {:id           (::email form-inputs)
                         :type         "email"
                         :placeholder  "youremail@example.com"
                         :defaultValue ""}
                        (some? error-message)
                        (css/add-class :is-invalid-input)))
             (when error-message
               (dom/p (css/add-class :text-alert) (dom/small nil error-message)))
             (button/default-hollow
               (css/add-classes [:expanded :sulo-dark] {:onClick #(.authorize-email this)})
               (dom/i {:classes ["fa fa-envelope-o fa-fw"]})
               (dom/span nil "Email me a code to sign in"))
             (dom/p (css/add-class :go-back) (dom/a {:onClick #(om/update-state! this assoc :login-state :login)}
                                                    (dom/span nil "Or sign in with Facebook or Twitter")))
             ;(dom/p (css/add-class :info)
             ;       (dom/small nil "By signing up you accept our ")
             ;       (dom/a nil (dom/small nil "Terms of Service"))
             ;       (dom/small nil " and ")
             ;       (dom/a nil (dom/small nil "Privacy Policy"))
             ;       (dom/small nil "."))
             )]

          :else
          [(dom/p nil (dom/span nil "Sign in to SULO Live to connect with brands other shoppers in your favourite city."))
           (dom/div
             (css/add-class :login-content)
             (button/button
               (css/add-classes [:expanded :facebook] {:onClick #(.authorize-social this :social/facebook)})
               (dom/i {:classes ["fa fa-facebook fa-fw"]})
               (dom/span nil "Continue with Facebook"))
             (button/button
               (css/add-classes [:expanded :twitter] {:onClick #(.authorize-social this :social/twitter)})
               (dom/i {:classes ["fa fa-twitter fa-fw"]})
               (dom/span nil "Continue with Twitter"))
             (button/default-hollow
               (css/add-classes [:sulo-dark :expanded] {:onClick #(om/update-state! this assoc :login-state :login-email)})
               ;(dom/i {:classes ["fa fa-envelope-o fa-fw"]})
               (dom/span nil "Sign up or sign in with email")))
           (dom/p (css/add-class :info)
                  (dom/small nil "By signing in you accept our ")
                  (dom/a nil (dom/small nil "Terms of Service"))
                  (dom/small nil " and ")
                  (dom/a {:href      "//www.iubenda.com/privacy-policy/8010910"
                          :className "iubenda-nostyle no-brand iubenda-embed"
                          :title     "Privacy Policy"
                          :target    "_blank"} (dom/small nil "Privacy Policy"))
                  (dom/small nil ". To use SULO Live you must have cookies enabled. We’ll never post to Twitter or Facebook without your permission."))])
        ))))

(def ->Login (om/factory Login))

(defui LoginModal
  static om/IQuery
  (query [_]
    [{:proxy/login (om/get-query Login)}
     {:query/login-modal [:ui.singleton.login-modal/show?]}])
  Object
  (close-modal [this]
    (om/transact! this [(list 'login-modal/hide)
                        :query/login-modal]))
  (render [this]
    (let [{:query/keys [login-modal]
           :proxy/keys [login]} (om/props this)]
      (debug "Login modal props: " (om/props this))
      (when (:ui.singleton.login-modal/show? login-modal)
        (modal/modal
          {:id       "sulo-login-modal"
           :size     "tiny"
           :on-close #(.close-modal this)}
          (photo/circle {:src "assets/img/auth0-icon.png"})

          (->Login login))))))

(def ->LoginModal (om/factory LoginModal))

(defui LoginPage
  static om/IQuery
  (query [this]
    [:query/auth
     ;{:proxy/navbar (om/get-query nav/Navbar)}
     ;{:proxy/footer (om/get-query foot/Footer)}
     {:proxy/login (om/get-query Login)}])
  Object
  ;(componentDidMount [this]
  ;  (auth/show-lock (shared/by-key this :shared/auth-lock)))
  (close-modal [this]
    (routes/set-url! this :landing-page))
  (render [this]
    (let [{:proxy/keys [navbar footer login]} (om/props this)]
      ;(index/->Index (:proxy/index (om/props this)))
      (modal/modal
        {:id             "sulo-login-modal"
         :size           "tiny"
         :on-close       #(.close-modal this)
         :require-close? true}

        ;(photo/circle {:src "assets/img/auth0-icon.png"})
        ;(dom/h2 nil "Sign up  / Sign in")
        (->Login login))
      ;(debug "Props: ")
      ;(dom/div
      ;  {:id     "sulo-login-page"}
      ;  (grid/row-column
      ;    (css/text-align :center)
      ;    (dom/h1 nil "Sign up / Sign in")
      ;    (->Login login)))
      )))

(router/register-component :login LoginPage)