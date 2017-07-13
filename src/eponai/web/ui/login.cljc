(ns eponai.web.ui.login
  (:require
    [clojure.spec.alpha :as s]
    [eponai.common.ui.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.web.ui.button :as button]
    [eponai.common.ui.elements.css :as css]
    [eponai.web.ui.modal :as modal]
    #?(:cljs
       [eponai.web.auth0 :as auth0])
    #?(:cljs
       [eponai.web.utils :as web-utils])
    [taoensso.timbre :refer [debug]]
    [eponai.web.ui.photo :as photo]
    [eponai.client.routes :as routes]
    [eponai.client.utils :as client-utils]
    [eponai.common :as c]
    [eponai.common.ui.elements.input-validate :as v]
    [eponai.client.parser.message :as msg]
    [eponai.common.shared :as shared]
    [eponai.common.mixpanel :as mixpanel]
    [clojure.string :as string]))

(def form-inputs
  {::email    "sulo.login.email"
   ::username "sulo.login.name"
   ::code     "sulo.login.code"

   ::password "sulo.login.password"})

(s/def ::email #(client-utils/valid-email? %))
(s/def ::username (s/and #(string? (not-empty %))
                         #(<= 3 (count %))))
(s/def ::code (s/and #(number? (c/parse-long-safe %))
                     #(= 6 (count %))))

(s/def ::create-account (s/keys :req [::username ::email]))

(defn redirect-to [path]
  #?(:cljs
     (str js/window.location.origin path)))

(defn render-accept-terms []
  (dom/p (css/add-class :info)
         (dom/small nil "By signing in you accept our ")
         (dom/a {:href   (routes/url :tos)
                 :target "_blank"} (dom/small nil "Terms of Service"))
         (dom/small nil " and ")
         (dom/a {:href      "//www.iubenda.com/privacy-policy/8010910"
                 :className "iubenda-nostyle no-brand iubenda-embed"
                 :title     "Privacy Policy"
                 :target    "_blank"} (dom/small nil "Privacy Policy"))
         (dom/small nil ". To use SULO Live you must have cookies enabled.")
         ;(dom/small nil " We’ll never post to Twitter or Facebook without your permission.")
         ))

(defn render-user-password [component]
  (let [{:keys [login-error input-validation]} (om/get-state component)]
    [(dom/p nil (dom/span nil "Sign in to SULO Live to connect with brands and other shoppers in your favourite city."))
     (dom/form
       (css/add-class :login-content)
       (dom/label nil "Email")
       (v/input {:type        "email"
                 :id          (::username form-inputs)
                 :placeholder "youremail@example.com"}
                input-validation)
       (dom/label nil "Password")
       (dom/input {:type "password"
                   :id   (::password form-inputs)})

       (when login-error
         (dom/p (css/add-class :text-alert) (dom/small nil "Wrong email or password")))
       (button/submit-button
         (css/add-classes [:sulo-dark :expanded] {:onClick #(.login component)})
         ;(dom/i {:classes ["fa fa-envelope-o fa-fw"]})
         ;; TODO @diana Enable this when allowing sign ups
         ;(dom/span nil "Sign up or sign in with email")
         (dom/span nil "Sign in")
         )
       (dom/p (css/add-class :info)
              (dom/small nil "By signing in you accept our ")
              (dom/a {:href   (routes/url :tos)
                      :target "_blank"} (dom/small nil "Terms of Service"))
              (dom/small nil " and ")
              (dom/a {:href      "//www.iubenda.com/privacy-policy/8010910"
                      :className "iubenda-nostyle no-brand iubenda-embed"
                      :title     "Privacy Policy"
                      :target    "_blank"} (dom/small nil "Privacy Policy"))
              (dom/small nil ". To use SULO Live you must have cookies enabled.")
              ;(dom/small nil " We’ll never post to Twitter or Facebook without your permission.")
              ))]))

(defn render-create-account [component]
  (let [{:keys             [user token-error input-validation] :as state
         :create-user/keys [input-email input-name]} (om/get-state component)
        {:query/keys [current-route]} (om/props component)
        {:keys [auth0-info]} (om/get-computed component)
        {:keys [query-params]} current-route
        auth-identity (first (:auth0/identities auth0-info))
        create-message (msg/last-message component 'user/create)
        is-loading? (or (nil? auth-identity) (msg/pending? create-message))
        ]
    (if (some? (:verified query-params))
      [(dom/p nil (dom/span nil "Verify your email address"))
       (dom/form
         (css/add-class :login-content)
         (dom/label nil "Email")
         (dom/p (css/add-class :email) (dom/strong nil (str (:auth0/email auth0-info))))
         (button/submit-button
           (css/add-classes [:expanded :sulo-dark] {:onClick (when-not is-loading?
                                                               #(.authorize-email component))})
           (dom/i {:classes ["fa fa-envelope-o fa-fw"]})
           (dom/span nil "Email me a verification code"))
         (render-accept-terms))]
      [(dom/p nil (dom/span nil "Finish creating your SULO Live account"))
       (dom/p nil (dom/a {:href (routes/url :login)} (dom/span nil "I already have an account")))

       (dom/form
         (css/add-class :login-content)
         (if (nil? auth-identity)
           ;; Show loading spinner before we got the user info
           (dom/p nil (dom/i {:classes ["fa fa-spinner fa-pulse"]}))
           [(dom/label nil "Email")

            (if (not-empty (:auth0/email auth0-info))
              (dom/p (css/add-class :email) (dom/strong nil (str (:auth0/email auth0-info))))
              (v/input
                {:type        "email"
                 :id          (::email form-inputs)
                 :placeholder "youremail@example.com"
                 :value       (or input-email (:auth0/email auth0-info))
                 :onChange    #(when-not (not-empty (:auth0/email auth0-info))
                                (om/update-state! component assoc :create-user/input-email (.-value (.-target %))))}
                input-validation))

            (dom/label nil "Name")
            (v/input {:type        "text"
                      :id          (::username form-inputs)
                      :placeholder "Your name"
                      :value       (or input-name (:auth0/nickname auth0-info))
                      :onChange    #(om/update-state! component assoc :create-user/input-name (.-value (.-target %)))}
                     input-validation)])

         (dom/p (css/add-class :info)
                (dom/small nil "By creating an account you accept our ")
                (dom/a {:href   (routes/url :tos)
                        :target "_blank"} (dom/small nil "Terms of Service"))
                (dom/small nil " and ")
                (dom/a {:href      "//www.iubenda.com/privacy-policy/8010910"
                        :className "iubenda-nostyle no-brand iubenda-embed"
                        :title     "Privacy Policy"
                        :target    "_blank"} (dom/small nil "Privacy Policy")))
         (when-let [err (:error/create-user state)]
           (dom/p (css/add-class :text-alert) (dom/small nil (str (:message err)))))
         (button/submit-button
           (css/add-classes [:expanded :sulo-dark] {:onClick (when-not is-loading?
                                                               #(.create-account component))})
           (dom/span nil "Create account")))])))

(defn render-enter-code [component]
  (let [state (om/get-state component)]
    [(dom/p nil (dom/span nil "We sent you a code to sign in. Please check your inbox and provide the code below."))
     (dom/form
       (css/add-class :login-content)
       (dom/label nil "Code")
       (dom/input {:id           (::code form-inputs)
                   :type         "number"
                   :placeholder  "000000"
                   :maxLength    6
                   :defaultValue ""})
       (when-let [err (:error/verify-email state)]
         (dom/p (css/add-class :text-alert) "Ooops, something went wrong."))
       (button/submit-button
         (css/add-class :sulo-dark {:onClick #(.verify-email component)})
         (dom/span nil "Sign me in")))
     (render-accept-terms)]))

(defn render-send-email-code [component]
  (let [{:keys [auth0error]} (om/get-state component)
        error-message (when auth0error (if (= (:code auth0error) "bad.email")
                                         "Please provide a valid email."
                                         "Sorry, couldn't send email. Try again later."))]
    [(dom/p nil (dom/span nil "Enter your email address to sign in or create an account on SULO Live"))
     (dom/form
       (css/add-class :login-content)
       (dom/label nil "Email")
       (dom/input
         (cond->> {:id           (::email form-inputs)
                   :type         "email"
                   :placeholder  "youremail@example.com"
                   :defaultValue ""}
                  (some? error-message)
                  (css/add-class :is-invalid-input)))
       (when error-message
         (dom/p (css/add-class :text-alert) (dom/small nil error-message)))
       (dom/p (css/add-class :go-back) (dom/a {:onClick #(om/update-state! component assoc :login-state :login)}
                                              (dom/span nil "Or sign in with Facebook or Twitter")))
       (button/submit-button
         (css/add-classes [:expanded :sulo-dark] {:onClick #(.authorize-email component)})
         (dom/i {:classes ["fa fa-envelope-o fa-fw"]})
         (dom/span nil "Email me a code to sign in")))
     (render-accept-terms)]))

(defn render-select-login-type [component]
  [(dom/p nil (dom/span nil "Sign in to SULO Live to connect with brands and other shoppers in your favourite city."))
   (dom/div
     (css/add-class :login-content)
     (button/button
       (css/add-classes [:expanded :facebook] {:onClick #(.authorize-social component :social/facebook)})
       (dom/i {:classes ["fa fa-facebook fa-fw"]})
       (dom/span nil "Continue with Facebook"))
     (button/button
       (css/add-classes [:expanded :twitter] {:onClick #(.authorize-social component :social/twitter)})
       (dom/i {:classes ["fa fa-twitter fa-fw"]})
       (dom/span nil "Continue with Twitter"))
     (button/default-hollow
       (css/add-classes [:sulo-dark :expanded] {:onClick #(om/update-state! component assoc :login-state :login-email)})
       (dom/i {:classes ["fa fa-envelope-o fa-fw"]})
       (dom/span nil "Sign up or sign in with email")))
   (render-accept-terms)])

(defui Login
  static om/IQuery
  (query [this]
    [:query/current-route
     :query/messages])

  Object
  (login [this]
    #?(:cljs
       (let [username (web-utils/input-value-by-id (::username form-inputs))
             pass (web-utils/input-value-by-id (::password form-inputs))
             validation (v/validate ::email username form-inputs)]
         (when (nil? validation)
           (auth0/login-with-credentials (shared/by-key this :shared/auth0) username pass (fn [res err]
                                                                                            (om/update-state! this assoc :login-error err))))
         (om/update-state! this assoc :input-validation validation))))
  (authorize-social [this provider]
    #?(:cljs
       (auth0/authorize-social (shared/by-key this :shared/auth0) {:connection (name provider)})))
  (authorize-email [this]
    #?(:cljs
       (let [{:keys [auth0-info]} (om/get-computed this)
             email (or (web-utils/input-value-by-id (::email form-inputs))
                       (:auth0/email auth0-info))
             {:keys [login-state user]} (om/get-state this)]
         (om/update-state! this dissoc :auth0error)
         (auth0/passwordless-start (shared/by-key this :shared/auth0)
                                   email
                                   (fn [res err]
                                     (debug "Got response: res")
                                     (debug "Got error: " err)
                                     (om/update-state! this (fn [st]
                                                              (cond-> (assoc st :input-email email)
                                                                      (some? err)
                                                                      (assoc :auth0error err)
                                                                      (nil? err)
                                                                      (assoc :login-state :verify-email)))))))))
  (verify-email [this]
    #?(:cljs
       (let [{:keys [input-email]} (om/get-state this)]
         (let [code (web-utils/input-value-by-id (::code form-inputs))]
           (auth0/passwordless-verify (shared/by-key this :shared/auth0) input-email code
                                      (fn [res err]
                                        (om/update-state! this assoc :error/verify-email err)))))))

  (create-account [this]
    #?(:cljs
       (let [{:query/keys [current-route]} (om/props this)
             {:keys [auth0-info]} (om/get-computed this)
             {:create-user/keys [input-email input-name]} (om/get-state this)
             email (or (:auth0/email auth0-info)
                       input-email)
             username (or input-name (:auth0/nickname auth0-info))
             validation (v/validate ::create-account {::email    email
                                                      ::username username} form-inputs)]
         (debug "Validation: " validation)
         (when (nil? validation)
           (msg/om-transact! this [(list 'user/create {:user     {:user/email    email
                                                                  :user/profile  {:user.profile/name username}
                                                                  :user/verified (and (:auth0/email auth0-info) (:auth0/email-verified auth0-info))}
                                                       :id-token (get-in current-route [:query-params :token])})]))
         (om/update-state! this assoc :input-validation validation :error/create-user nil))))

  (componentDidUpdate [this _ _]
    (let [{:query/keys [current-route]} (om/props this)
          create-msg (msg/last-message this 'user/create)]
      (debug "Got create message : " create-msg)
      (when (msg/final? create-msg)
        (let [message (msg/message create-msg)]
          (debug "Got message: " message)
          (msg/clear-messages! this 'user/create)
          (if (msg/success? create-msg)
            (let [new-user (:user message)
                  user-id (:user-id message)]
              (mixpanel/set-alias (:db/id new-user))
              (if (:user/verified new-user)
                (do
                  (debug "User created routing to: " (routes/url :auth nil (:query-params current-route)))
                  #?(:cljs
                     (js/window.location.replace (routes/url :auth nil (:query-params current-route)))))
                (do
                  #?(:cljs
                     (let [email (web-utils/input-value-by-id (::email form-inputs))]
                       (om/update-state! this assoc :login-state :verify-email :input-email email))))))
            (om/update-state! this assoc :error/create-user message))))))

  (componentDidMount [this]
    #?(:cljs
       (let [{:query/keys [current-route]} (om/props this)
             {:keys [query-params]} current-route]
         (when (:access_token query-params)
           (debug "Found access token, getting user info....")
           ;(auth0/user-info (shared/by-key this :shared/auth0)
           ;                 (:access_token query-params)
           ;                 (fn [user err]
           ;                   (debug "Got user info: " user)
           ;                   (debug "Got error: " err)
           ;                   (om/update-state! this assoc :user user)))
           ))))

  (initLocalState [this]
    {:login-state :login})
  (render [this]
    (let [{:query/keys [current-route]} (om/props this)
          {:keys [route query-params]} current-route
          {:keys [access_token]} query-params
          {:keys [login-state]} (om/get-state this)]
      (debug "State " (om/get-state this))
      (debug "props: " (om/props this))

      (dom/div
        (css/text-align :center {:id "sulo-login-modal-content"})

        (if (= route :login)
          (dom/div
            (css/add-class :header-container)
            (photo/circle {:src "assets/img/auth0-icon.png"})
            (if (some? access_token)
              (dom/h4 nil "Almost there")
              (dom/h1 nil "SULO Live")))
          ;; TODO @diana Enable this when allowing sign ups
          ;(dom/h4 nil "Sign up or sign in")
          (dom/h4 nil "Sign in")
          )

        (cond
          (and (= route :login) (= login-state :login) access_token)
          (render-create-account this)

          (= login-state :verify-email)
          (render-enter-code this)


          (= login-state :login-email)
          (render-send-email-code this)

          :else
          ;(render-user-password this)
          (render-select-login-type this)
          )))))

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
      (debug "Login modal")
      (when (:ui.singleton.login-modal/show? login-modal)
        (modal/modal
          {:id       "sulo-login-modal"
           :size     "tiny"
           :on-close #(.close-modal this)}
          (photo/circle {:src "assets/img/auth0-icon.png"})

          (->Login login))))))

(def ->LoginModal (om/factory LoginModal))
