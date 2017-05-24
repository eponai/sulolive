(ns eponai.web.ui.user.dashboard
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.router :as router]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.elements.input-validate :as v]
    [eponai.web.ui.photo :as photo]
    #?(:cljs [cljs.spec :as s]
       :clj
    [clojure.spec :as s])
    #?(:cljs
       [eponai.web.utils :as utils])
    #?(:cljs
       [eponai.client.ui.photo-uploader :as pu])
    [eponai.client.parser.message :as msg]
    [clojure.string :as string]
    [eponai.common.ui.elements.callout :as callout]))

(def form-inputs
  {:user.info/name            "user.info.name"
   :shipping/name             "sulo-shipping-full-name"
   :shipping.address/street   "sulo-shipping-street-address-1"
   :shipping.address/street2  "sulo-shipping-street-address-2"
   :shipping.address/postal   "sulo-shipping-postal-code"
   :shipping.address/locality "sulo-shipping-locality"
   :shipping.address/region   "sulo-shipping-region"
   :shipping.address/country  "sulo-shipping-country"})
(s/def :shipping/name (s/and string? #(not-empty %)))
(s/def :shipping.address/street (s/and string? #(not-empty %)))
(s/def :shipping.address/street2 (s/or :value string? :empty nil?))
(s/def :shipping.address/postal (s/and string? #(not-empty %)))
(s/def :shipping.address/locality (s/and string? #(not-empty %)))
(s/def :shipping.address/region (s/or :value #(string? (not-empty %)) :empty nil?))
(s/def :shipping.address/country (s/and string? #(re-matches #"\w{2}" %)))

(s/def :shipping/address (s/keys :req [:shipping.address/street
                                       :shipping.address/postal
                                       :shipping.address/locality]
                                 :opt [:shipping.address/street2
                                       :shipping.address/region]))
(s/def ::shipping (s/keys :req [:shipping/address
                                :shipping/name]))

(defn validate
  [spec m & [prefix]]
  (when-let [err (s/explain-data spec m)]
    (let [problems (::s/problems err)
          invalid-paths (map (fn [p]
                               (str prefix (some #(get form-inputs %) p)))
                             (map :path problems))]
      {:explain-data  err
       :invalid-paths invalid-paths})))

(defn edit-profile-modal [component]
  (let [{:query/keys [auth]} (om/props component)
        {:keys [photo-upload queue-photo]} (om/get-state component)
        user-profile (:user/profile auth)
        on-close #(om/update-state! component dissoc :modal :photo-upload :queue-photo)]
    (common/modal
      {:on-close on-close
       :size     "full"}
      (grid/row-column
        (->> (css/text-align :center)
             (css/add-class :edit-profile-modal))
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
                  (dom/div
                    {:classes ["upload-photo circle loading user-profile-photo"]}
                    (photo/circle {:src (:src queue-photo)}
                                  (photo/overlay nil (dom/i {:classes ["fa fa-spinner fa-spin"]}))))
                  (dom/label {:htmlFor "file-profile"
                              :classes ["upload-photo circle"]}
                             (if (some? photo-upload)
                               (photo/circle {:photo-id       (:public_id photo-upload)
                                              :transformation :transformation/thumbnail})
                               (photo/user-photo auth {:transformation :transformation/thumbnail}))
                             #?(:cljs
                                (pu/->PhotoUploader
                                  (om/computed
                                    {}
                                    {:id              "profile"
                                     :on-photo-queue  (fn [img-result]
                                                        (om/update-state! component assoc :queue-photo {:src img-result}))
                                     :on-photo-upload (fn [photo]
                                                        (om/update-state! component (fn [s]
                                                                                      (-> s
                                                                                          (assoc :photo-upload photo)
                                                                                          (dissoc :queue-photo))))
                                                        ;(msg/om-transact! this [(list 'photo/upload {:photo photo})
                                                        ;                        :query/user])
                                                        )})))))
                (dom/input {:type         "text"
                            :placeholder "Name"
                            :id           (:user.info/name form-inputs)
                            :defaultValue (:user.profile/name user-profile)})
                (dom/p nil (dom/small nil "This name will be visible to other users in chats.")))))
          )
        (dom/div
          (css/add-class :action-buttons)
          (dom/a
            (->> (css/button-hollow {:onClick on-close})
                 (css/add-class :secondary)
                 (css/add-class :small))
            (dom/span nil "Close"))

          (dom/a
            (->> (css/button {:onClick #(.save-user-info component)})
                 (css/add-class :secondary)
                 (css/add-class :small))
            (dom/span nil "Save")))))))

(def payment-logos {"Visa"             "icon-cc-visa"
                    "American Express" "icon-cc-amex"
                    "MasterCard"       "icon-cc-mastercard"
                    "Discover"         "icon-cc-discover"
                    "JCB"              "icon-cc-jcb"
                    "Diners Club"      "icon-cc-diners"
                    "Unknown"          "icon-cc-unknown"})

(defn shipping-info-modal [component]
  (let [{:query/keys [stripe-customer]} (om/props component)
        shipping (:stripe/shipping stripe-customer)         ;[{:brand "American Express" :last4 1234 :exp-year 2018 :exp-month 4}]
        address (:stripe.shipping/address shipping)
        {:shipping/keys [input-validation]} (om/get-state component)
        on-close #(om/update-state! component dissoc :modal)]
    (common/modal
      {:on-close on-close
       :size     :full}
      (grid/row-column
        (css/text-align :center)
        (dom/h2 nil "Shipping address")
        (dom/p nil (dom/small nil "Shipping address to use at checkout."))
        (dom/form
          (css/add-classes [:section-form :section-form--address])
          (dom/div
            nil
            (grid/row
              nil
              (grid/column
                nil
                (v/input {:id           (:shipping/name form-inputs)
                            :type         "text"
                            :defaultValue (:stripe.shipping/name shipping)
                            :name         "name"
                            :autoComplete "name"
                            :placeholder  "Full name"}
                         input-validation))))
          (dom/div
            nil
            (grid/row
              nil
              (grid/column
                (grid/column-size {:small 12 :medium 8})
                (v/input {:id           (:shipping.address/street form-inputs)
                            :type         "text"
                            :defaultValue (:stripe.shipping.address/street address)
                            :name         "ship-address"
                            :autoComplete "shipping address-line1"
                            :placeholder  "Street"}
                         input-validation))
              (grid/column
                nil
                (v/input {:id           (:shipping.address/street2 form-inputs)
                            :type         "text"
                            :defaultValue (:stripe.shipping.address/street2 address)
                            :placeholder  "Apt/Suite/Other"}
                         input-validation)))
            (grid/row
              nil
              (grid/column
                (grid/column-size {:small 12 :medium 4})
                (v/input {:id           (:shipping.address/postal form-inputs)
                            :type         "text"
                            :defaultValue (:stripe.shipping.address/postal address)
                            :name         "ship-zip"
                            :autoComplete "shipping postal-code"
                            :placeholder  "Postal code"}
                         input-validation))
              (grid/column
                (grid/column-size {:small 12 :medium 4})
                (v/input {:id           (:shipping.address/locality form-inputs)
                            :type         "text"
                            :defaultValue (:stripe.shipping.address/city address)
                            :name         "ship-city"
                            :autoComplete "shipping locality"
                            :placeholder  "City"}
                         input-validation))
              (grid/column
                (grid/column-size {:small 12 :medium 4})
                (v/input {:id (:shipping.address/region form-inputs)
                            :type         "text"
                            :defaultValue (:stripe.shipping.address/state address)
                            :name         "ship-state"
                            :autoComplete "shipping region"
                            :placeholder  "Province/State"}
                         input-validation)))))
        ;(callout/callout-small
        ;  (css/add-class :warning))
        (dom/p nil (dom/small nil "Shipping address cannot be saved yet. We're working on this."))
        (dom/div
          (css/add-class :action-buttons)
          (dom/a
            (->> {:onClick on-close}
                 (css/button-hollow)
                 (css/add-class :secondary)
                 (css/add-class :small)) (dom/span nil "Close"))
          (dom/a
            (->> {:onClick #(do
                             ;(.save-shipping-info component)
                             )}
                 (css/button)
                 (css/add-class :secondary)
                 (css/add-class :small)
                 (css/add-class :disabled)) (dom/span nil "Save")))))))

(defn payment-info-modal [component]
  (let [{:query/keys [stripe-customer]} (om/props component)
        cards (:stripe/sources stripe-customer)             ;[{:brand "American Express" :last4 1234 :exp-year 2018 :exp-month 4}]
        on-close #(om/update-state! component dissoc :modal)]
    (common/modal
      {:on-close on-close
       :size     :full}
      (grid/row-column
        (css/text-align :center)
        (dom/h2 nil "Credit cards")
        (if (empty? cards)
          [(dom/p nil
                  (dom/span nil "You don't have any saved credit cards.")
                  (dom/br nil)
                  (dom/small nil "New cards are saved at checkout."))
           (dom/a
             (->> {:onClick on-close}
                  (css/button-hollow)
                  (css/add-class :secondary)
                  (css/add-class :small)) (dom/span nil "Close"))]
          [
           (dom/div
             nil
             (dom/div (css/add-classes [:section-title :text-left])
                      (dom/small nil "Default"))
             (let [default-card (some #(when (= (:stripe.card/id %) (:stripe/default-source stripe-customer)) %) (:stripe/sources stripe-customer))]
               (menu/vertical
                 (css/add-classes [:section-list :section-list--cards])
                 (map-indexed (fn [i c]
                                (let [{:stripe.card/keys [brand last4]} c]
                                  (let [{:stripe.card/keys [brand last4 id]} c]
                                    (menu/item
                                      (css/add-class :section-list-item--card)
                                      (dom/a
                                        nil                 ;{:onClick #(om/update-state! this assoc :selected-source id)}
                                        (dom/input {:type "radio"
                                                    :name "sulo-select-cc"
                                                    })
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
                                        (dom/a
                                          (->> (css/button-hollow)
                                               (css/add-class :secondary)
                                               (css/add-class :small))
                                          (dom/span nil "Remove")))
                                      ))))
                              cards)))
             (dom/p nil (dom/small nil "New cards are saved at checkout.")))
           (dom/div
             (css/add-class :action-buttons)
             (dom/a
               (->> {:onClick on-close}
                    (css/button-hollow)
                    (css/add-class :secondary)
                    (css/add-class :small)) (dom/span nil "Close"))
             (dom/a
               (->> {:onClick on-close}
                    (css/button)
                    (css/add-class :secondary)
                    (css/add-class :small)) (dom/span nil "Save")))])))))
(defui UserDashboard
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/auth [:user/email
                   {:user/profile [:user.profile/name
                                   {:user.profile/photo [:photo/id]}]}
                   :user/stripe]}
     {:query/stripe-customer [:db/id
                              :stripe/sources
                              :stripe/default-source
                              :stripe/shipping]}
     #?(:cljs
        {:proxy/uploader (om/get-query pu/PhotoUploader)})
     :query/current-route
     :query/messages])
  Object
  (save-shipping-info [this]
    #?(:cljs
       (let [{:shipping.address/keys [street street2 locality postal region country]} form-inputs
             shipping-map {:shipping/name    (utils/input-value-or-nil-by-id (:shipping/name form-inputs))
                           :shipping/address {:shipping.address/street   (utils/input-value-or-nil-by-id street)
                                              :shipping.address/street2  (utils/input-value-or-nil-by-id street2)
                                              :shipping.address/locality (utils/input-value-or-nil-by-id locality)
                                              ;:shipping.address/country  (utils/input-value-or-nil-by-id country)
                                              :shipping.address/region   (utils/input-value-or-nil-by-id region)
                                              :shipping.address/postal   (utils/input-value-or-nil-by-id postal)}}
             validation (validate ::shipping shipping-map)]
         (debug "Validation: " validation)
         (when (nil? validation)
           (msg/om-transact! this [(list 'stripe/update-customer {:shipping shipping-map})
                                   :query/stripe-customer]))
         (om/update-state! this assoc :shipping/input-validation validation))))
  (save-user-info [this]
    #?(:cljs
       (let [input-name (utils/input-value-by-id (:user.info/name form-inputs))
             {:keys [photo-upload]} (om/get-state this)]
         (when (not-empty input-name)
           (msg/om-transact! this (cond-> [(list 'user.info/update {:user/name input-name})]
                                          (some? photo-upload)
                                          (conj (list 'photo/upload {:photo photo-upload}))
                                          :else
                                          (conj :query/auth)))))))
  (componentDidUpdate [this _ _]
    (let [info-msg (msg/last-message this 'user.info/update)
          photo-msg (msg/last-message this 'photo/upload)

          shipping-msg (msg/last-message this 'stripe/update-customer)]
      (debug "Messages: " {:info  info-msg
                           :photo photo-msg})
      (when (and (msg/final? info-msg)
                 (or (nil? photo-msg) (msg/final? photo-msg)))
        (cond (and (msg/success? info-msg)
                   (or (nil? photo-msg) (msg/success? photo-msg)))
              (do
                (msg/clear-messages! this 'user.info/update)
                (msg/clear-messages! this 'photo/upload)
                (om/update-state! this dissoc :modal))))
      (when (and (msg/final? shipping-msg)
                 (msg/success? shipping-msg))
        (msg/clear-messages! this 'stripe/update-customer)
        (om/update-state! this dissoc :modal))))

  ;(initLocalState [_]
  ;  {:modal :modal/shipping-info})
  (render [this]
    (let [{:proxy/keys [navbar]
           :query/keys [auth current-route stripe-customer]} (om/props this)
          {:keys [modal photo-upload queue-photo]} (om/get-state this)
          {user-profile :user/profile} auth
          {:keys [route-params]} current-route]
      (debug "Props: " (om/props this))
      (common/page-container
        {:navbar navbar :id "sulo-user-dashboard"}
        (grid/row-column
          nil
          (dom/h1 nil "Settings")
          (dom/div
            (css/add-class :section-title)
            (dom/span nil "Account"))
          (menu/vertical
            (css/add-class :section-list)
            (menu/item
              nil
              (grid/row
                (->> (css/align :middle)
                     (css/add-class :collapse))
                (grid/column
                  nil
                  (dom/label nil "Email")
                  (dom/span nil (:user/email auth)))
                (grid/column
                  (grid/column-size {:small 12 :medium 6})
                  ;(dom/a
                  ;  (->> (css/button-hollow)
                  ;       (css/add-class :secondary)
                  ;       (css/add-class :small))
                  ;  (dom/span nil "Edit email"))
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
                  nil
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
                  (dom/a
                    (->> {:onClick #(om/update-state! this assoc :modal :modal/edit-profile)}
                         (css/button-hollow)
                         (css/add-class :secondary)
                         (css/add-class :small))
                    (dom/span nil "Edit profile"))))))
          (dom/div
            (css/add-class :section-title)
            (dom/span nil "Shopper details"))
          (menu/vertical
            (css/add-class :section-list)

            (menu/item
              nil
              (grid/row
                (->> (css/align :middle)
                     (css/add-class :collapse))
                (grid/column
                  nil
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
                        ;(dom/div {:classes ["icon" (get payment-logos brand "icon-cc-unknown")]})
                        (dom/p nil
                               (dom/span (css/add-class :payment-brand) brand)
                               (dom/br nil)
                               (dom/small (css/add-class :payment-last4) (str "ending in " last4)))
                        ))
                    (dom/p nil (dom/small nil (dom/i nil "No default card")))
                    )
                  (dom/a
                    (->> {:onClick #(om/update-state! this assoc :modal :modal/payment-info)}
                         (css/button-hollow)
                         (css/add-class :secondary)
                         (css/add-class :small))
                    (dom/span nil "Manage payment info")))))
            (menu/item
              nil
              (grid/row
                (->> (css/align :middle)
                     (css/add-class :collapse))
                (grid/column
                  nil
                  (dom/label nil "Shipping")
                  (dom/p nil (dom/small nil "Manage your saved shipping address. This address will be pre-filled for you at checkout.")))
                (grid/column
                  (->> (grid/column-size {:small 12 :medium 6})
                       (css/text-align :right))
                  (if-let [shipping (:stripe/shipping stripe-customer)]
                    (let [{:stripe.shipping/keys [address name]} shipping
                          {:stripe.shipping.address/keys [street street2 city postal state country]} address]
                      (dom/div
                        (css/add-classes [:shipping :default-shipping])
                        ;(dom/div {:classes ["icon" (get payment-logos brand "icon-cc-unknown")]})
                        (dom/p nil
                               (dom/span nil name)
                               (dom/br nil)
                               (dom/small nil (str street ", " city " " postal))
                               (dom/br nil )
                               (dom/small nil (string/join ", " [state country])))
                        ))
                    (dom/p nil (dom/small nil (dom/i nil "No saved address")))
                    )
                  (dom/a
                    (->> (css/button-hollow {:onClick #(om/update-state! this assoc :modal :modal/shipping-info)})
                         (css/add-class :secondary)
                         (css/add-class :small))
                    (dom/span nil "Manage shipping info"))))))

          (dom/div
            (css/add-class :section-title)
            (dom/span nil "Connections"))
          (menu/vertical
            (css/add-class :section-list)
            (menu/item
              nil
              (grid/row
                (->> (css/align :middle)
                     (css/add-class :collapse))
                (grid/column
                  nil
                  (dom/label nil "Facebook")
                  (dom/p nil (dom/small nil "Connect to Facebook to login with your account. We will never post to Facebook or message your friends without your permission")))
                (grid/column
                  (->> (grid/column-size {:small 12 :medium 6})
                       (css/text-align :right))
                  (dom/a
                    (->> (css/button)
                         (css/add-class :facebook)
                         (css/add-class :small)
                         (css/add-class :disabled))
                    (dom/i {:classes ["fa fa-facebook fa-fw"]})
                    (dom/span nil "Connect to Facebook")))))

            (menu/item
              nil
              (grid/row
                (->> (css/align :middle)
                     (css/add-class :collapse))
                (grid/column
                  nil
                  (dom/label nil "Twitter")
                  (dom/p nil (dom/small nil "Connect to Twitter to login with your account. We will never post to Twitter or message your followers without your permission.")))
                (grid/column
                  (->> (grid/column-size {:small 12 :medium 6})
                       (css/text-align :right))
                  (dom/a
                    (->> (css/button)
                         (css/add-class :twitter)
                         (css/add-class :small)
                         (css/add-class :disabled))
                    (dom/i {:classes ["fa fa-twitter fa-fw"]})
                    (dom/span nil "Connect to Twitter")))))))))))

;(def ->UserSettings (om/factory UserSettings))

(router/register-component :user-settings UserDashboard)