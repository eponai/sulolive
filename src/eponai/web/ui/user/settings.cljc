(ns eponai.web.ui.user.settings
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.router :as router]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.elements.input-validate :as v]
    [eponai.common.mixpanel :as mixpanel]
    [eponai.web.ui.checkout.shipping :as shipping]
    [eponai.web.ui.photo :as photo]
    #?(:cljs [cljs.spec.alpha :as s]
       :clj
    [clojure.spec.alpha :as s])
    #?(:cljs
       [eponai.web.utils :as utils])
    #?(:cljs
       [eponai.client.ui.photo-uploader :as pu])
    [eponai.client.parser.message :as msg]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.web.ui.button :as button]
    [eponai.common.shared :as shared]
    #?(:cljs
       [eponai.web.auth0 :as auth0])
    [eponai.client.routes :as routes]
    [eponai.common.ui.loading-bar :as loading-bar]))

(def form-inputs
  {:user.info/name            "user.info.name"
   :shipping/name             "sulo-shipping-full-name"
   :shipping.address/street   "sulo-shipping-street-address-1"
   :shipping.address/street2  "sulo-shipping-street-address-2"
   :shipping.address/postal   "sulo-shipping-postal-code"
   :shipping.address/locality "sulo-shipping-locality"
   :shipping.address/region   "sulo-shipping-region"
   :shipping.address/country  "sulo-shipping-country"})

(s/def ::shipping (s/keys :req [:shipping/address
                                :shipping/name]))

(s/def :user.info/name (s/and string? #(not-empty %)))

(defn validate
  [spec m & [prefix]]
  (when-let [err (s/explain-data spec m)]
    (let [problems (::s/problems err)
          invalid-paths (map (fn [p]
                               (str prefix (some #(get form-inputs %) p)))
                             (map :via problems))]
      {:explain-data  err
       :invalid-paths invalid-paths})))

(defn social-identity [component platform]
  (let [{:query/keys [auth0-info]} (om/props component)
        connection (name platform)]
    (some #(when (= connection (:auth0.identity/connection %)) %) (:auth0/identities auth0-info))))

(defn edit-profile-modal [component]
  (let [{:query/keys [auth]} (om/props component)
        {:keys         [photo-upload queue-photo error-message]
         :profile/keys [input-validation ]} (om/get-state component)
        user-profile (:user/profile auth)
        is-loading? (.is-loading? component)
        on-close #(om/update-state! component dissoc :modal :photo-upload :queue-photo :error-message)]
    (common/modal
      {:on-close on-close
       :size     "full"}
      (grid/row-column
        (->> (css/text-align :center)
             (css/add-class :edit-profile-modal))
        (dom/p
          (css/add-class :text-alert)
          (dom/strong nil "Note: ")
          (dom/i nil "photo uploads do not work properly in this demo version of SULO Live."))
        (dom/h2 nil "Edit profile")
        (dom/p nil (dom/small nil "Update your information seen by other users on SULO Live."))
        (menu/vertical
          (css/add-class :section-list)
          (menu/item
            nil
            (grid/row
              (css/align :center)
              (grid/column
                (grid/column-size {:small 12 :medium 8})
                (if (some? queue-photo)
                  (photo/circle {:src    (:src queue-photo)
                                 :status :loading})
                  (dom/label
                    {:htmlFor "profile-photo-upload"}
                    (if (some? photo-upload)
                      (photo/circle {:photo-id       (:public_id photo-upload)
                                     :transformation :transformation/thumbnail
                                     :status         :edit})
                      (photo/user-photo auth {:transformation :transformation/thumbnail
                                              :status         :edit}))
                    #?(:cljs
                       (pu/->PhotoUploader
                         (om/computed
                           {:id "profile-photo-upload"}
                           {:on-photo-queue  (fn [img-result]
                                               (om/update-state! component assoc :queue-photo {:src img-result}))
                            :on-photo-upload (fn [photo]
                                               (mixpanel/track-key ::mixpanel/upload-photo (assoc photo :type "User profile photo"))
                                               (om/update-state! component (fn [s]
                                                                             (-> s
                                                                                 (assoc :photo-upload photo)
                                                                                 (dissoc :queue-photo))))
                                               )})))))
                (v/input {:type         "text"
                          :placeholder  "Username"
                          :id           (:user.info/name form-inputs)
                          :defaultValue (:user.profile/name user-profile)}
                         input-validation)
                (dom/p nil (dom/small nil "Your username will be visible to other users in chats.")))))
          )
        (dom/p (css/add-class :text-alert) (dom/small nil (str error-message)))
        (dom/div
          (css/add-class :action-buttons)
          (button/user-setting-default {:onClick on-close} (dom/span nil "Close"))
          (button/user-setting-cta
            {:onClick (when-not is-loading? #(.save-user-info component))}
            (if is-loading?
              (dom/i {:classes ["fa fa-spinner fa-spin"]})
              (dom/span nil "Save"))))))))

(defn shipping-info-modal [component]
  (let [{:query/keys [stripe-customer countries]} (om/props component)
        shipping (:stripe/shipping stripe-customer)         ;[{:brand "American Express" :last4 1234 :exp-year 2018 :exp-month 4}]
        address (:shipping/address shipping)
        {:shipping/keys [input-validation error-message selected-country]} (om/get-state component)
        selected-country (if selected-country selected-country (or (:country/code (:shipping.address/country address))
                                                                   "CA"))
        on-close #(do (mixpanel/track "Close shipping info")
                      (om/update-state! component dissoc :modal))]
    (debug "Selected-country: " selected-country)
    (debug "Address: " address)
    (common/modal
      {:on-close on-close
       :size     :full}
      (grid/row-column
        (css/text-align :center)

        (dom/p
          (css/add-class :text-alert)
          (dom/strong nil "Note: ")
          (dom/i nil "Shipping address cannot be saved in this demo version of SULO Live. "))
        (dom/h2 nil "Shipping address")
        (dom/p nil
               (dom/small nil "Shipping address to use at checkout."))

        (dom/form
          (css/add-classes [:section-form :section-form--address])
          (dom/div
            nil
            (grid/row nil
                      (grid/column (css/add-class :no-margin) (dom/label nil "Name")))
            (grid/row
              nil
              (grid/column
                nil
                (v/input {:id           (:shipping/name form-inputs)
                          :type         "text"
                          :defaultValue (:shipping/name shipping)
                          :name         "name"
                          :autoComplete "name"
                          :placeholder  "Full name"}
                         input-validation))))

          (dom/div
            nil
            (grid/row nil
                      (grid/column (css/add-class :no-margin) (dom/label nil "Country")))
            (grid/row
              nil
              (grid/column
                nil
                (dom/select
                  {:id           (:shipping.address/country form-inputs)
                   :name         "ship-country"
                   :autoComplete "shipping country"
                   :defaultValue selected-country
                   :onChange     #(om/update-state! component assoc :shipping/selected-country (.-value (.-target %)))}
                  ;input-validation
                  (map (fn [c]
                         (dom/option {:value (:country/code c)} (:country/name c)))
                       (sort-by :country/name countries)))))
            (grid/row nil
                      (grid/column (css/add-class :no-margin) (dom/label nil "Address")))
            (grid/row
              nil
              (grid/column
                (grid/column-size {:small 12 :medium 8})
                (v/input {:id           (:shipping.address/street form-inputs)
                          :type         "text"
                          :defaultValue (:shipping.address/street address)
                          :name         "ship-address"
                          :autoComplete "shipping address-line1"
                          :placeholder  "Street"}
                         input-validation))
              (grid/column
                nil
                (v/input {:id           (:shipping.address/street2 form-inputs)
                          :type         "text"
                          :defaultValue (:shipping.address/street2 address)
                          :placeholder  "Apt/Suite/Other (optional)"}
                         input-validation)))
            (grid/row
              nil
              (grid/column
                (grid/column-size {:small 12 :medium 4})
                (v/input {:id           (:shipping.address/postal form-inputs)
                          :type         "text"
                          :defaultValue (:shipping.address/postal address)
                          :name         "ship-zip"
                          :autoComplete "shipping postal-code"
                          :placeholder  "Zip/Postal code"}
                         input-validation))
              (grid/column
                (grid/column-size {:small 12 :medium 4})
                (v/input {:id           (:shipping.address/locality form-inputs)
                          :type         "text"
                          :defaultValue (:shipping.address/locality address)
                          :name         "ship-city"
                          :autoComplete "shipping locality"
                          :placeholder  "City"}
                         input-validation))
              (grid/column
                (grid/column-size {:small 12 :medium 4})
                (if-let [regions (not-empty (sort (get shipping/regions selected-country)))]
                  (v/select
                    {:id           (:shipping.address/region form-inputs)
                     :defaultValue (or (:shipping.address/region address) "default")
                     :name         "ship-state"
                     :autoComplete "shipping region"}
                    input-validation
                    (dom/option {:value "default" :disabled true} "--- Province/State ---")
                    (map (fn [r]
                           (dom/option {:value r} (str r)))
                         regions))
                  (dom/input
                    {:id           (:shipping.address/region form-inputs)
                     :defaultValue (or (:shipping.address/region address) "")
                     :name         "ship-state"
                     :autoComplete "shipping region"
                     :type         "text"
                     :placeholder  "Province/State (optional)"}))))))
        (dom/p (css/add-class :text-alert) (dom/small nil (str error-message)))

        (dom/div
          (css/add-class :action-buttons)
          (button/user-setting-default {:onClick on-close} (dom/span nil "Close"))
          (button/user-setting-cta
            {:onClick #(.save-shipping-info component)}
            (dom/span nil "Save")))))))

(defn payment-info-modal [component]
  (let [{:query/keys [stripe-customer]} (om/props component)
        {:payment/keys [selected-card]} (om/get-state component)
        cards (:stripe/sources stripe-customer)             ;[{:brand "American Express" :last4 1234 :exp-year 2018 :exp-month 4}]
        on-close #(do (mixpanel/track "Close payment info")
                      (om/update-state! component dissoc :modal))]
    (debug "Selected card: " selected-card)
    (common/modal
      {:on-close on-close
       :size     :full}
      (grid/row-column
        (css/text-align :center {:id "payment-info"})
        (dom/h2 nil "Credit cards")
        (if (empty? cards)
          [(dom/p nil
                  (dom/span nil "You don't have any saved credit cards.")
                  (dom/br nil)
                  (dom/small nil "New cards are saved at checkout."))
           (button/user-setting-default {:onClick on-close} (dom/span nil "Close"))]

          [(dom/div
             nil
             (dom/div (css/add-classes [:section-title :text-left])
                      (dom/small nil "Default"))
             (let [default-card (some #(when (= (:stripe.card/id %) (:stripe/default-source stripe-customer)) %) (:stripe/sources stripe-customer))]
               (debug "Default card: " default-card)
               (menu/vertical
                 (css/add-classes [:section-list :section-list--cards])

                 (map-indexed (fn [i c]
                                (let [{:stripe.card/keys [brand last4 id]} c]
                                  (menu/item
                                    (css/add-class :section-list-item--card)
                                    (dom/a
                                      {:onClick #(.save-payment-info component id)}
                                      (dom/input {:type    "radio"
                                                  :name    "sulo-select-cc"
                                                  :checked (if (some? selected-card)
                                                             (= id selected-card)
                                                             (= id (:stripe.card/id default-card)))})
                                      (dom/div
                                        (css/add-class :payment-card)
                                        (dom/div {:classes ["icon" (get {"Visa"             "icon-cc-visa"
                                                                         "American Express" "icon-cc-amex"
                                                                         "MasterCard"       "icon-cc-mastercard"
                                                                         "Discover"         "icon-cc-discover"
                                                                         "JCB"              "icon-cc-jcb"
                                                                         "Diners Club"      "icon-cc-diners"
                                                                         "Unknown"          "icon-cc-unknown"} brand "icon-cc-unknown ")]})
                                        (dom/p nil
                                               (dom/span (css/add-class :payment-brand) brand)
                                               (dom/small (css/add-class :payment-last4) (str "ending in " last4))
                                               )))
                                    (dom/div
                                      nil
                                      (button/user-setting-default
                                        {:onClick #(.remove-payment component id)}
                                        (dom/span nil "Remove"))))))
                              cards)))
             (dom/p nil (dom/small nil "New cards are saved at checkout.")))
           (dom/div
             (css/add-class :action-buttons)
             (button/user-setting-default {:onClick on-close} (dom/span nil "Close")))])))))

(defn render-error-message [component]
  (let [{:keys [error/show-social-error?]} (om/get-state component)
        {:query/keys [current-route]} (om/props component)
        error-message (get-in current-route [:query-params :message])]
    (when show-social-error?
      (callout/callout
        (css/add-classes [:sulo-popup-message :alert])
        (dom/p nil (dom/span nil "Something went wrong and we couldn't connect your social account. Please try again later."))))))

(defui UserSettings
  static om/IQuery
  (query [_]
    [{:query/auth [:user/email
                   {:user/profile [:user.profile/name
                                   {:user.profile/photo [:photo/id]}]}
                   :user/stripe]}
     {:query/stripe-customer [:db/id
                              :stripe/sources
                              :stripe/default-source
                              :stripe/shipping]}
     {:query/countries [:country/name :country/code]}
     :query/auth0-info
     :query/current-route
     :query/messages])
  Object
  (authorize-social [this provider]
    (let [{:query/keys [current-route]} (om/props this)
          {:keys [route route-params query-params]} current-route]
      #?(:cljs
         (do
           (auth0/authorize-social (shared/by-key this :shared/auth0) {:connection  (name provider)
                                                                       :redirectUri (auth0/redirect-to (routes/url :link-social))})
           (loading-bar/start-loading! (shared/by-key this :shared/loading-bar) (om/get-reconciler this))))))

  (unlink-account [this social-identity]
    (msg/om-transact! this [(list 'user/unlink-account {:user-id  (:auth0.identity/id social-identity)
                                                        :provider (:auth0.identity/provider social-identity)})
                            :query/auth0-info]))

  (save-shipping-info [this]
    #?(:cljs
       (let [{:shipping.address/keys [street street2 locality postal region country]} form-inputs
             shipping-map {:shipping/name    (utils/input-value-or-nil-by-id (:shipping/name form-inputs))
                           :shipping/address {:shipping.address/street   (utils/input-value-or-nil-by-id street)
                                              :shipping.address/street2  (utils/input-value-or-nil-by-id street2)
                                              :shipping.address/locality (utils/input-value-or-nil-by-id locality)
                                              :shipping.address/country  {:country/code (utils/input-value-or-nil-by-id country)}
                                              :shipping.address/region   (utils/input-value-or-nil-by-id region)
                                              :shipping.address/postal   (utils/input-value-or-nil-by-id postal)}}
             validation (validate ::shipping shipping-map)]
         (debug "Validation:  " validation)
         (when (nil? validation)
           (mixpanel/track "Save shipping info")
           (msg/om-transact! this [(list 'stripe/update-customer {:shipping shipping-map})
                                   :query/stripe-customer]))
         (om/update-state! this assoc :shipping/input-validation validation))))
  (save-payment-info [this selected-card]
    #?(:cljs
       (let [{:query/keys [stripe-customer]} (om/props this)
             default-card (some #(when (= (:stripe.card/id %) (:stripe/default-source stripe-customer)) %) (:stripe/sources stripe-customer))]

         (mixpanel/track "Save payment info")
         (when-not (= selected-card (:stripe.card/id default-card))
           (msg/om-transact! this [(list 'stripe/update-customer {:default-source selected-card})
                                   :query/stripe-customer])))))

  (remove-payment [this selected-card]
    (let [{:query/keys [stripe-customer]} (om/props this)]
      (msg/om-transact! this [(list 'stripe/update-customer {:remove-source selected-card})
                              :query/stripe-customer])))

  (save-user-info [this]
    #?(:cljs
       (let [input-name (utils/input-value-by-id (:user.info/name form-inputs))
             {:keys [photo-upload]} (om/get-state this)
             validation (validate :user.info/name input-name)]
         (debug "validation: " validation)
         (when (nil? validation)
           (mixpanel/track "Save public profile")
           (mixpanel/people-set {:$first_name input-name})
           (msg/om-transact! this (cond-> [(list 'user.info/update {:user/name input-name})]
                                          (some? photo-upload)
                                          (conj (list 'photo/upload {:photo photo-upload}))
                                          :else
                                          (conj :query/auth :query/stripe-customer))))
         (om/update-state! this assoc :profile/input-validation validation))))
  (componentDidUpdate [this _ _]
    (let [info-msg (msg/last-message this 'user.info/update)
          photo-msg (msg/last-message this 'photo/upload)

          shipping-msg (msg/last-message this 'stripe/update-customer)]
      (when (and (msg/final? info-msg)
                 (or (nil? photo-msg) (msg/final? photo-msg)))
        (cond (not (msg/success? info-msg))
              (om/update-state! this assoc :error-message (msg/message info-msg))

              (and (msg/success? info-msg)
                   (or (nil? photo-msg) (msg/success? photo-msg)))
              (do
                (msg/clear-messages! this 'user.info/update)
                (msg/clear-messages! this 'photo/upload)
                (om/update-state! this dissoc :modal :error-message))))

      (when (msg/final? shipping-msg)
        (msg/clear-messages! this 'stripe/update-customer)
        (if (msg/success? shipping-msg)
          (let [message (msg/message shipping-msg)]
            ;; If we were updating shipping addres, we should close the modal.
            (om/update-state! this merge (cond-> {:error-message nil}
                                                 (some? (:shipping message))
                                                 (assoc :modal nil))))
          (om/update-state! this assoc :error-message (msg/message shipping-msg))))))

  (componentDidMount [this]
    #?(:cljs
       (let [{:query/keys [current-route]} (om/props this)]
         (debug "Component did mount")
         (when (get-in current-route [:query-params :error])
           (debug "Set social error")
           (js/setTimeout (fn [] (om/update-state! this assoc :error/show-social-error? false)) 5000)
           (om/update-state! this assoc :error/show-social-error? true)))))

  (initLocalState [this]
    (let [{:query/keys [current-route]} (om/props this)]
      {:error/show-social-error? (boolean (get-in current-route [:query-params :error]))}))

  (is-loading? [this]
    (let [info-msg (msg/last-message this 'user.info/update)
          photo-msg (msg/last-message this 'photo/upload)
          stripe-msg (msg/last-message this 'stripe/update-customer)]
      (debug "Is loading: " stripe-msg)
      (or (msg/pending? info-msg)
          (msg/pending? photo-msg)
          (msg/pending? stripe-msg))))
  (render [this]
    (let [{:query/keys [auth current-route stripe-customer auth0-info]} (om/props this)
          {:keys [modal photo-upload queue-photo]} (om/get-state this)
          {user-profile :user/profile} auth
          {:keys [route-params]} current-route
          is-loading? (.is-loading? this)]
      (debug "Got auth0 " auth0-info)
      (dom/div
        nil
        (when is-loading?
          (common/loading-spinner nil))
        (render-error-message this)
        (grid/row-column
          nil
          (dom/h1 nil "Settings")
          (dom/p
            (css/add-class :text-alert)
            (dom/strong nil "Note: ")
            (dom/i nil "some settings cannot be updated properly in this demo version of SULO Live."))
          (dom/div
            (css/add-class :section-title)
            (dom/span nil "Account"))
          (callout/callout
            nil
            (menu/vertical
              (css/add-class :section-list)
              (menu/item
                nil
                (grid/row
                  (->> (css/align :middle)
                       (css/add-class :collapse))
                  (grid/column
                    (grid/column-size {:small 12 :medium 6})
                    (dom/label nil "Email")
                    (dom/span nil (:user/email auth)))
                  (grid/column
                    (grid/column-size {:small 12 :medium 6})
                    )
                  ))
              (cond (= modal :modal/edit-profile)
                    (edit-profile-modal this)
                    (= modal :modal/payment-info)
                    (payment-info-modal this)
                    (= modal :modal/shipping-info)
                    (shipping-info-modal this))
              (menu/item
                nil
                (grid/row
                  (->> (css/align :middle)
                       (css/add-class :collapse))
                  (grid/column
                    (grid/column-size {:small 12 :medium 6})
                    (dom/label nil "Public profile")
                    (dom/p nil (dom/small nil "This is how other users on SULO will see you when you interact and hang out in stores."))
                    )
                  (grid/column
                    (->> (grid/column-size {:small 12 :medium 6})
                         (css/text-align :right))
                    (dom/div
                      (css/add-class :user-profile)
                      (dom/span nil (:user.profile/name user-profile))
                      (photo/user-photo auth {:transformation :transformation/thumbnail}))
                    (button/user-setting-default
                      {:onClick #(do
                                  (mixpanel/track "Edit profile")
                                  (om/update-state! this assoc :modal :modal/edit-profile))}
                      (dom/span nil "Edit profile")))))))
          (dom/div
            (css/add-class :section-title)
            (dom/span nil "Shopper details"))
          (callout/callout
            nil
            (menu/vertical
              (css/add-class :section-list)

              (menu/item
                nil
                (grid/row
                  (->> (css/align :middle)
                       (css/add-class :collapse))
                  (grid/column
                    (grid/column-size {:small 12 :medium 6})
                    (dom/label nil "Payment info")
                    (dom/p nil (dom/small nil "Manage your saved credit cards. Change your default card or remove old ones.")))
                  (grid/column
                    (->> (grid/column-size {:small 12 :medium 6})
                         (css/text-align :right))

                    (if-let [default-card (some #(when (= (:stripe/default-source stripe-customer) (:stripe.card/id %)) %)
                                                (:stripe/sources stripe-customer))]
                      (let [{:stripe.card/keys [brand last4]} default-card]
                        (dom/div
                          (css/add-classes [:payment-card :default-card])
                          (dom/p nil
                                 (dom/span (css/add-class :payment-brand) brand)
                                 (dom/br nil)
                                 (dom/small (css/add-class :payment-last4) (str "ending in " last4)))
                          ))
                      (dom/p nil (dom/small nil (dom/i nil "No default card")))
                      )
                    (button/user-setting-default
                      {:onClick #(do
                                  (mixpanel/track "Manage payment info")
                                  (om/update-state! this assoc :modal :modal/payment-info))}
                      (dom/span nil "Manage payment info")))))
              (menu/item
                nil
                (grid/row
                  (->> (css/align :middle)
                       (css/add-class :collapse))
                  (grid/column
                    (grid/column-size {:small 12 :medium 6})
                    (dom/label nil "Shipping")
                    (dom/p nil (dom/small nil "Manage your saved shipping address. This address will be pre-filled for you at checkout.")))
                  (grid/column
                    (->> (grid/column-size {:small 12 :medium 6})
                         (css/text-align :right))
                    (if-let [shipping (:stripe/shipping stripe-customer)]
                      (let [{:shipping/keys [address name]} shipping
                            {:shipping.address/keys [street street2 locality postal region country]} address]
                        (dom/div
                          (css/add-classes [:shipping :default-shipping])
                          (common/render-shipping shipping nil)
                          ))
                      (dom/p nil (dom/small nil (dom/i nil "No saved address")))
                      )
                    (button/user-setting-default
                      {:onClick #(do
                                  (mixpanel/track "Manage shipping")
                                  (om/update-state! this assoc :modal :modal/shipping-info))}
                      (dom/span nil "Manage shipping info")))))))

          (dom/div
            (css/add-class :section-title)
            (dom/span nil "Connections"))
          (callout/callout
            nil
            (menu/vertical
              (css/add-class :section-list)
              (menu/item
                nil
                (if-let [facebook-identity (social-identity this :social/facebook)]
                  (grid/row
                    (->> (css/align :middle)
                         (css/add-class :collapse))
                    (grid/column
                      (grid/column-size {:small 12 :medium 6})
                      (dom/label nil "You are connected to Facebook")
                      (dom/p nil (dom/small nil "You can now sign in to SULO Live using your Facebook account. We will never post to Facebook or message your friends without your permission.")))
                    (grid/column
                      (->> (grid/column-size {:small 12 :medium 6})
                           (css/text-align :right))

                      (dom/div
                        (css/add-class :user-profile)
                        (dom/div nil (dom/span nil (:auth0.identity/name facebook-identity))
                                 (dom/br nil)
                                 (dom/a {:onClick #(.unlink-account this facebook-identity)} (dom/small nil "disconnect")))
                        (photo/circle {:src (:auth0.identity/picture facebook-identity)}))))
                  (grid/row
                    (->> (css/align :middle)
                         (css/add-class :collapse))
                    (grid/column
                      (grid/column-size {:small 12 :medium 6})
                      (dom/label nil "Facebook")
                      (dom/p nil (dom/small nil "Connect to Facebook to sign in with your account. We will never post to Facebook or message your friends without your permission")))
                    (grid/column
                      (->> (grid/column-size {:small 12 :medium 6})
                           (css/text-align :right))
                      (button/user-setting-cta
                        (css/add-classes [:facebook :disabled] {:onClick #(.authorize-social this :social/facebook)})
                        (dom/i {:classes ["fa fa-facebook fa-fw"]})
                        (dom/span nil "Connect to Facebook"))))))

              (menu/item
                nil
                (if-let [twitter-identity (social-identity this :social/twitter)]
                  (grid/row
                    (->> (css/align :middle)
                         (css/add-class :collapse))
                    (grid/column
                      (grid/column-size {:small 12 :medium 6})
                      (dom/label nil "You are connected to Twitter")
                      (dom/p nil (dom/small nil "You can now sign in to SULO Live using your Twitter account. We will never post to Twitter or message your followers without your permission.")))
                    (grid/column
                      (->> (grid/column-size {:small 12 :medium 6})
                           (css/text-align :right))
                      (dom/div
                        (css/add-class :user-profile)
                        (dom/div nil (dom/span nil (:auth0.identity/screen-name twitter-identity))
                                 (dom/br nil)
                                 (dom/a {:onClick #(.unlink-account this twitter-identity)} (dom/small nil "disconnect")))
                        (photo/circle {:src (:auth0.identity/picture twitter-identity)}))))
                  (grid/row
                    (->> (css/align :middle)
                         (css/add-class :collapse))
                    (grid/column
                      (grid/column-size {:small 12 :medium 6})
                      (dom/label nil "Twitter")
                      (dom/p nil (dom/small nil "Connect to Twitter to sign in with your account. We will never post to Twitter or message your followers without your permission.")))
                    (grid/column
                      (->> (grid/column-size {:small 12 :medium 6})
                           (css/text-align :right))
                      (button/user-setting-cta
                        (css/add-classes [:twitter :disabled] {:onClick #(.authorize-social this :social/twitter)})
                        (dom/i {:classes ["fa fa-twitter fa-fw"]})
                        (dom/span nil "Connect to Twitter")))))))))))))

(router/register-component :user-settings UserSettings)