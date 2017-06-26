(ns eponai.web.ui.login
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.index :as index]
    [eponai.common.ui.router :as router]
    [eponai.common.shared :as shared]
    [eponai.client.auth :as auth]
    [om.next :as om :refer [defui]]
    [eponai.web.ui.button :as button]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.navbar :as nav]
    [eponai.web.ui.footer :as foot]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.elements.grid :as grid]
    #?(:cljs [cljs-http.client :as http]
       :clj
    [clj-http.client :as http])
    [taoensso.timbre :refer [debug]]))



(defui Login
  static om/IQuery
  (query [this]
    [:query/current-route])

  Object
  (authorize [this provider]
    (let [{:keys [auth0]} (om/get-state this)]
      (when auth0
        (.authorize auth0 #js {:connection "facebook"})))
    ;https://sulo.auth0.com/authorize?
    ;response_type=code|token&
    ;client_id=JMqCBngHgOcSYBwlVCG2htrKxQFldzDh&
    ;connection=CONNECTION&
    ;redirect_uri=http://localhost:3000/auth&
    ;state=STATE&
    ;additional-parameter=ADDITIONAL_PARAMETERS
    )

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
    {:show-email? false})
  (render [this]
    (let [{:keys [show-email?]} (om/get-state this)]

      (dom/div
        (css/text-align :center {:id "sulo-login"})

        (if show-email?
          [(dom/p (css/add-class :header) (dom/span nil "Enter your email address to sign in or create an account on SULO Live"))
           (dom/div
             (css/add-class :email-signin)
             (dom/label nil "Email")
             (dom/input {:type        "email"
                         :placeholder "youremail@example.com"})
             (button/default-hollow
               (css/add-class :sulo-dark)
               (dom/i {:classes ["fa fa-envelope-o fa-fw"]})
               (dom/span nil "Email me a sign in code"))
             (dom/p (css/add-class :go-back) (dom/a {:onClick #(om/update-state! this assoc :show-email? false)}
                               (dom/span nil "Or sign in with Facebook")))
             (dom/p (css/add-class :info)
                    (dom/small nil "By signing up you accept our ")
                    (dom/a nil (dom/small nil "Terms of Service"))
                    (dom/small nil " and ")
                    (dom/a nil (dom/small nil "Privacy Policy"))
                    (dom/small nil ".")))]
          [(dom/p nil (dom/span nil "Sign in to SULO Live to connect with brands other shoppers in your favourite city."))
           (dom/div
             (css/add-class :signin-options)
             (button/button
               (css/add-classes [:expanded :facebook] {:onClick #(.authorize this)})
               (dom/i {:classes ["fa fa-facebook fa-fw"]})
               (dom/span nil "Continue with Facebook"))
             (button/button
               (css/add-classes [:expanded :twitter])
               (dom/i {:classes ["fa fa-twitter fa-fw"]})
               (dom/span nil "Continue with Twitter"))
             (button/default-hollow
               (css/add-classes [:sulo-dark :expanded] {:onClick #(om/update-state! this assoc :show-email? true)})
               ;(dom/i {:classes ["fa fa-envelope-o fa-fw"]})
               (dom/span nil "Sign up or sign in with email")))
           (dom/p (css/add-class :info)
                  (dom/small nil "By signing up you accept our ")
                  (dom/a nil (dom/small nil "Terms of Service"))
                  (dom/small nil " and ")
                  (dom/a nil (dom/small nil "Privacy Policy"))
                  (dom/small nil ". To use SULO Live you must have cookies enabled. We’ll never post to Twitter or Facebook without your permission."))])
        ))))

(def ->Login (om/factory Login))

(defui LoginPage
  static om/IQuery
  (query [this]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:proxy/footer (om/get-query foot/Footer)}
     {:proxy/login (om/get-query Login)}])
  Object
  ;(componentDidMount [this]
  ;  (auth/show-lock (shared/by-key this :shared/auth-lock)))
  (render [this]
    (let [{:proxy/keys [navbar footer login]} (om/props this)]
      ;(index/->Index (:proxy/index (om/props this)))
      (common/page-container
        {:id     "sulo-login-page"
         :navbar navbar
         :footer footer}
        (grid/row-column
          (css/text-align :center)
          (dom/h1 nil "Sign up / Sign in")
          (->Login login))))))

(router/register-component :login LoginPage)