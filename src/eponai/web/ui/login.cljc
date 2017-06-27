(ns eponai.web.ui.login
  (:require
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
    [eponai.client.routes :as routes]))

(def form-inputs
  {::email "sulo.login.email"
   ::code  "sulo.login.code"})

(defui Login
  static om/IQuery
  (query [this]
    [:query/current-route])

  Object
  (authorize-social [this provider]
    #?(:cljs
       (when-let [auth0 (:auth0 (om/get-state this))]
         (.authorize auth0 #js {:connection   (name provider)}))))
  (authorize-email [this]
    #?(:cljs
       (do
         (om/update-state! this dissoc :error-message)
         (when-let [auth0 (:auth0 (om/get-state this))]
           (when-let [email (web-utils/input-value-by-id (::email form-inputs))]
             (.passwordlessStart auth0 #js {:connection "email" :send "code" :email email}
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
       (let [{:keys [auth0 input-email]} (om/get-state this)]
         (when auth0
           (let [code (web-utils/input-value-by-id (::code form-inputs))]
             (.passwordlessVerify auth0 #js {:connection       "email"
                                             :email            input-email
                                             :verificationCode code}
                                  (fn [err res]
                                    (debug "Verified response: " res)
                                    (debug "Verified error: " err))))))))

  (componentDidMount [this]
    #?(:cljs
       (let [{:query/keys [current-route]} (om/props this)]
         (debug "Current route: " current-route)
         (let [web-auth (new js/auth0.WebAuth #js {:domain       "sulo.auth0.com"
                                                   :clientID     "olXSYZ7HDqCif7GtNEaxi6jKX9Lk72OR"
                                                   :redirectUri  "http://localhost:3000/login"
                                                   :responseType "code"
                                                   :scope        "openid email"
                                                   })]
           (om/update-state! this assoc :auth0 web-auth)))))
  ;auth0 = new auth0.WebAuth ({
  ;                            domain:       'sulo.auth0.com',
  ;                            clientID:     'JMqCBngHgOcSYBwlVCG2htrKxQFldzDh',
  ;                            redirectUri:  'http:// localhost:3000',
  ;                                          audience: 'https:// sulo.auth0.com/userinfo',
  ;                            responseType: 'token id_token',
  ;                                          scope: 'openid'
  ;                            })                            ;
  ;
  ;login () {
  ;          this.auth0.authorize ()                         ;
  ;          }

  (initLocalState [this]
    {:login-state :login})
  (render [this]
    (let [{:keys [login-state error-message]} (om/get-state this)]

      (dom/div
        (css/text-align :center {:id "sulo-login"})

        (cond
          (= login-state :verify-email)
          [(dom/p nil (dom/span nil "We sent you a code to sign in. Please check your inbox and provide the code below."))
           (dom/div
             (css/add-class :email-signin)
             (dom/label nil "Code")
             (dom/input {:id           (::code form-inputs)
                         :type         "number"
                         :placeholder "000000"
                         :maxLength    6
                         :defaultValue ""})
             (button/default-hollow
               (css/add-class :sulo-dark {:onClick #(.verify-email this)})
               (dom/span nil "Sign me in"))
             )]

          (= login-state :login-email)
          [(dom/p nil (dom/span nil "Enter your email address to sign in or create an account on SULO Live"))
           (dom/div
             (css/add-class :email-signin)
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
             (css/add-class :signin-options)
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
                  (dom/small nil "By signing up you accept our ")
                  (dom/a nil (dom/small nil "Terms of Service"))
                  (dom/small nil " and ")
                  (dom/a {:href      "//www.iubenda.com/privacy-policy/8010910"
                          :className "iubenda-nostyle no-brand iubenda-embed"
                          :title     "Privacy Policy"
                          :target "_blank"} (dom/small nil "Privacy Policy"))
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
          (dom/h4 nil "Sign up or sign in")
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
        {:id       "sulo-login-modal"
         :size     "tiny"
         :on-close #(.close-modal this)
         :require-close? true}
        (dom/div
          (css/add-class :header-container)
          (photo/circle {:src "assets/img/auth0-icon.png"})
          (dom/h1 nil "SULO Live"))
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