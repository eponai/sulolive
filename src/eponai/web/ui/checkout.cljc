(ns eponai.web.ui.checkout
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.router :as router]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.dom :as dom]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.web.ui.photo :as photo]
    [eponai.common.ui.utils :as utils]
    [eponai.common.ui.product :as product]
    [eponai.web.ui.button :as button]
    #?(:cljs [eponai.web.utils :as web-utils])
    #?(:cljs [eponai.web.ui.stripe :as stripe])
    #?(:cljs [eponai.common.ui.checkout.google-places :as places])
    [eponai.common.ui.elements.input-validate :as validate]
    [eponai.common.shared :as shared]
    [eponai.web.ui.checkout.shipping :as ship]
    [eponai.common.ui.script-loader :as script-loader]
    [eponai.client.parser.message :as msg]
    [eponai.client.routes :as routes]
    [eponai.common.analytics.google :as ga]
    [eponai.common.mixpanel :as mixpanel]))

(def form-inputs
  {:shipping/name             "sulo-shipping-full-name"
   :shipping.address/street   "sulo-shipping-street-address-1"
   :shipping.address/street2  "sulo-shipping-street-address-2"
   :shipping.address/postal   "sulo-shipping-postal-code"
   :shipping.address/locality "sulo-shipping-locality"
   :shipping.address/region   "sulo-shipping-region"
   :shipping.address/country  "sulo-shipping-country"

   :payment.stripe/card       "sulo-card-element"})

(defn compute-subtotal [skus]
  (or (reduce + (map #(get-in % [:store.item/_skus :store.item/price]) skus)) 0))

(defn compute-shipping-fee [rate items]
  (let [{:shipping.rate/keys [additional free-above]
         first-rate          :shipping.rate/first} rate
        subtotal (compute-subtotal items)
        item-count (count items)]
    (cond (and (some? free-above)
               (> subtotal free-above))
          0
          (<= 1 item-count)
          (apply + (or first-rate 0) (repeat (dec item-count) (or additional 0)))
          :else
          (or first-rate 0))))
(defn compute-taxes [taxes subtotal shipping-fee]
  (let [{tax-rate    :taxes/rate
         :taxes/keys [freight-taxable?]} taxes
        tax-rate (or tax-rate 0)]

    (if freight-taxable?
      (* tax-rate (+ subtotal shipping-fee))
      (* tax-rate subtotal))))

(defn compute-discount [price coupon]
  (if (:valid coupon)
    (let [percent-off (:percent_off coupon)]
      (* 0.01 percent-off price))
    0))

(defn checkout-store [skus]
  (-> skus first :store.item/_skus :store/_items))


(defn render-store-items [component]
  (let [{:query/keys [checkout taxes]} (om/props component)
        store (checkout-store checkout)
        store-profile (:store/profile store)

        {:shipping/keys [selected-rate]
         :coupon/keys   [apply-code input-code applied-coupon]} (om/get-state component)
        subtotal (compute-subtotal checkout)
        shipping (compute-shipping-fee selected-rate checkout)
        discount (compute-discount subtotal applied-coupon)
        tax-amount (compute-taxes taxes (- subtotal discount) shipping)
        grandtotal (- (+ subtotal shipping tax-amount) discount)]

    (callout/callout
      (css/add-classes [:section :section--item-list])
      (dom/div
        (css/add-classes [:section-title :store-header])
        (photo/store-photo store nil)
        (dom/h3 nil (:store.profile/name store-profile)))
      (menu/vertical
        (css/add-class :section-list)
        (map (fn [sku]
               (let [product (:store.item/_skus sku)]
                 (menu/item
                   nil
                   (grid/row
                     nil
                     (grid/column
                       (css/add-class :product-info)
                       (dom/a
                         {:href (product/product-url product)}
                         (photo/product-preview product nil)
                         (dom/div nil
                                  (dom/span nil (:store.item/name product))
                                  (dom/br nil)
                                  (dom/small nil "S"))))
                     (grid/column
                       (css/add-class :product-info)
                       (dom/span nil (utils/two-decimal-price (:store.item/price product)))
                       )))))
             checkout)
        (menu/item
          (css/add-class :additional-items)
          (dom/div
            (css/add-class :receipt)
            (map (fn [{:keys [title value neg?]}]
                   (grid/row
                     (css/text-align :right)
                     (grid/column
                       nil
                       (dom/small nil (str title)))
                     (grid/column
                       nil
                       (dom/small nil (if neg?
                                        (str "-" (utils/two-decimal-price value))
                                        (utils/two-decimal-price value))))))
                 [{:title "Subtotal" :value subtotal}
                  {:title "Discount" :value discount :neg? true}
                  {:title "Shipping" :value shipping}
                  {:title "Tax" :value tax-amount}])
            (dom/div
              (css/add-class :total)
              (grid/row
                (css/text-align :right)
                (grid/column
                  nil
                  (dom/strong nil "Total"))
                (grid/column
                  nil
                  (dom/strong nil (utils/two-decimal-price grandtotal))))))))
      (if applied-coupon
        (dom/div
          (css/text-align :right)
          (dom/p nil
                 (dom/strong nil (dom/small nil "Promo code: "))
                 (dom/small nil (str (:id applied-coupon)))
                 (dom/br nil)
                 (dom/a {:onClick #(om/update-state! component dissoc :coupon/applied-coupon)} (dom/small nil "Remove"))))
        (if apply-code
          (let [apply-coupon-msg (msg/last-message component 'checkout/apply-coupon)]
            (dom/form
              (css/add-class :promo-code-form)
              (dom/input {:type     "text"
                          :onChange #(om/update-state! component assoc :coupon/input-code (.-value (.-target %)))})
              (if (msg/pending? apply-coupon-msg)
                (button/submit-button-small
                  (dom/i {:classes ["fa fa-spinner fa-pulse"]}))
                (button/submit-button-small
                  {:onClick #(.apply-coupon component input-code)} "Apply code"))))
          (dom/div
            (css/text-align :right)
            (button/store-setting-default
              {:onClick #(om/update-state! component assoc :coupon/apply-code true)}
              (dom/span nil "Have a promo code?"))))))))

(defn available-rules [props shipping]
  (let [{:query/keys [checkout]} props
        store (checkout-store checkout)
        shipping-country-code (get-in shipping [:shipping/address :shipping.address/country :country/code])
        shipping-rules (get-in store [:store/shipping :shipping/rules])]
    (filter #(contains? (set (map :country/code (:shipping.rule/destinations %))) shipping-country-code)
            shipping-rules)))

(defn shipping-available? [rules]
  (or (some #(true? (:shipping.rule/pickup? %)) rules)
      (some #(pos? (count (:shipping.rule/rates %))) rules)))

(defn render-edit-address [component edit-shipping]
  (let [{:keys            [input-validation manual-shipping]
         current-shipping :checkout/shipping} (om/get-state component)
        {:query/keys [countries checkout stripe-customer]} (om/props component)
        store (checkout-store checkout)
        {:shipping/keys [address]
         shipping-name  :shipping/name} edit-shipping
        country-code (get-in address [:shipping.address/country :country/code])
        shipping-rules (available-rules (om/props component) edit-shipping)

        has-provided-address? (not-empty (filter #(not-empty (val %)) (dissoc (:shipping/address edit-shipping) :shipping.address/country :shipping.address/region)))]


    ;(debug "All shipping rules: " (get-in store [:store/shipping :shipping/rules]))
    ;(debug "Shipping rules: " shipping-rules)
    ;(if (nil? stripe-customer))
    ;(dom/div
    ;  nil
    ;  (dom/i {:classes ["fa fa-spinner fa-pulse"]}))
    (dom/form
      (css/add-classes [:edit-address])
      (grid/row
        nil
        (grid/column
          nil
          (dom/label nil "Country")
          (dom/select
            {:id           (:shipping.address/country form-inputs)
             :name         "ship-country"
             :autoComplete "shipping country"
             :value        (or country-code "CA")
             :onChange     #(.change-country component (.-value (.-target %)))}
            (map (fn [c]
                   (dom/option {:value (:country/code c)} (:country/name c)))
                 (sort-by :country/name countries)))
          (when-not (shipping-available? shipping-rules)
            (callout/callout-small
              (->> (css/add-class :sulo-dark)
                   (css/text-align :center))
              (dom/small nil (str "Sorry, " (get-in store [:store/profile :store.profile/name]) " doesn't ship to this country."))))))

      (grid/row
        nil
        (grid/column
          nil
          (dom/label nil "Full name")
          (validate/input
            {:id           (:shipping/name form-inputs)
             :type         "text"
             :name         "name"
             :autoComplete "name"
             :value        (or shipping-name "")
             :onChange     #(om/update-state! component update :shipping/edit-shipping assoc :shipping/name (.-value (.-target %)))
             }
            input-validation)))

      (dom/div
        nil
        (grid/row
          (when manual-shipping
            (css/add-class :hide))
          (grid/column
            nil
            (dom/label nil "Search address")
            (dom/input {:id           "sulo-auto-complete"
                        :placeholder  "Enter address..."
                        :type         "text"
                        :defaultValue ""})))
        (dom/div
          nil
          (cond manual-shipping
                (dom/p
                  (css/text-align :center)
                  (dom/a {:onClick #(om/update-state! component assoc :manual-shipping false)}
                         (dom/small nil "Search address")))
                (and (not manual-shipping)
                     (not has-provided-address?))
                (dom/p
                  (css/text-align :center)
                  (dom/a {:onClick #(om/update-state! component assoc :manual-shipping true)}
                         (dom/small nil "Fill out address manually"))))
          ;(when (or manual-shipping
          ;          has-provided-address?)
          ;  (css/add-class :hide))
          ;(dom/p
          ;  (css/text-align :center)
          ;  (dom/a {:onClick #(om/update-state! component assoc :manual-shipping true)}
          ;         (dom/small nil "Or enter address manually")))
          )

        (dom/hr nil)
        (dom/div
          (when (and (not manual-shipping)
                     (not has-provided-address?))
            (css/add-class :hide))


          (grid/row
            nil
            (grid/column
              (grid/column-size {:small 12 :medium 8})
              (dom/label nil "Address")
              (validate/input
                {:id           (:shipping.address/street form-inputs)
                 :type         "text"
                 :value        (:shipping.address/street address "")
                 :onChange     #(om/update-state! component update :shipping/edit-shipping assoc-in
                                                  [:shipping/address :shipping.address/street] (.-value (.-target %)))
                 :name         "ship-address"
                 :autoComplete "shipping address-line1"}
                input-validation))
            (grid/column
              (grid/column-size {:small 12 :medium 4})
              (dom/label nil "Apt/Suite/Other (optional)")
              (validate/input
                {:type     "text"
                 :id       (:shipping.address/street2 form-inputs)
                 :value    (:shipping.address/street2 address "")
                 :onChange #(om/update-state! component update :shipping/edit-shipping assoc-in
                                              [:shipping/address :shipping.address/street2] (.-value (.-target %)))
                 }
                input-validation)))
          (grid/row
            nil
            (grid/column
              nil
              (dom/label nil "Postal code")
              (validate/input
                {:id           (:shipping.address/postal form-inputs)
                 :type         "text"
                 :name         "ship-zip"
                 :autoComplete "shipping postal-code"
                 :value        (:shipping.address/postal address "")
                 :onChange     #(om/update-state! component update :shipping/edit-shipping assoc-in
                                                  [:shipping/address :shipping.address/postal] (.-value (.-target %)))
                 }
                input-validation))
            (grid/column
              (grid/column-size {:small 12 :large 4})
              (dom/label nil "City")
              (validate/input
                {:type         "text"
                 :id           (:shipping.address/locality form-inputs)
                 :value        (:shipping.address/locality address "")
                 :name         "ship-city"
                 :autoComplete "shipping locality"
                 :onChange     #(om/update-state! component update :shipping/edit-shipping assoc-in
                                                  [:shipping/address :shipping.address/locality] (.-value (.-target %)))}
                input-validation))
            (grid/column
              nil
              (dom/label nil "State/Province (optional)")
              (if-let [regions (not-empty (sort (get ship/regions (or country-code "CA"))))]
                (validate/select
                  {:id           (:shipping.address/region form-inputs)
                   :value        (or (:shipping.address/region address) "")
                   :name         "ship-state"
                   :autoComplete "shipping region"
                   :onChange     #(om/update-state! component update :shipping/edit-shipping assoc-in
                                                    [:shipping/address :shipping.address/region] (.-value (.-target %)))}
                  input-validation
                  (dom/option {:value "" :disabled true} "--- Province/State ---")
                  (map (fn [r]
                         (dom/option {:value r} (str r)))
                       regions))
                (dom/input
                  {:id           (:shipping.address/region form-inputs)
                   :value        (or (:shipping.address/region address) "")
                   :name         "ship-state"
                   :autoComplete "shipping region"
                   :type         "text"
                   :placeholder  "Province/State (optional)"
                   :onChange     #(om/update-state! component update :shipping/edit-shipping assoc-in
                                                    [:shipping/address :shipping.address/region] (.-value (.-target %)))})))))

        (dom/div
          nil
          (when input-validation
            (dom/p
              (css/text-align :center)
              (dom/small (css/add-class :text-alert) "You have errors that need to be fixed before continuing")))
          (dom/div
            (css/add-class :action-buttons)
            (when (not-empty current-shipping)
              (button/submit-cancel-button
                {:onClick #(om/update-state! component dissoc :shipping/edit-shipping)
                 :classes [:small]}
                (dom/span nil "Cancel")))
            (button/submit-button-small
              (cond-> {:onClick #(.save-shipping component edit-shipping)
                       :classes [:small]}
                      (or (not has-provided-address?) (not (shipping-available? shipping-rules)))
                      (assoc :disabled true))
              (if (not-empty current-shipping)
                (dom/span nil "Update address")
                (dom/span nil "Next")))))))))

(defn render-shipping-details [component]
  (let [{:query/keys [checkout stripe-customer]} (om/props component)
        {:shipping/keys   [edit-shipping selected-rate]
         current-shipping :checkout/shipping} (om/get-state component)

        subtotal (compute-subtotal checkout)
        shipping-rules (available-rules (om/props component) (or edit-shipping current-shipping))
        shipping-rates (reduce (fn [all-rates rule]
                                 (let [rates (:shipping.rule/rates rule)
                                       calc-rates (mapv #(assoc % :shipping.rate/total (compute-shipping-fee % checkout)) rates)]
                                   (if (:shipping.rule/pickup? rule)
                                     (into calc-rates all-rates)
                                     (into all-rates calc-rates)))) [] shipping-rules)]

    (callout/callout
      (css/add-classes [:section :section--shipping])
      (dom/h4 (css/text-align :center) "Ship to")

      (dom/div
        (css/add-class :subsection)
        (dom/div
          (when (nil? edit-shipping)
            (css/add-class :hide))
          (render-edit-address component edit-shipping))
        (dom/div
          (when (some? edit-shipping)
            (css/add-class :hide))
          (common/render-shipping current-shipping nil))
        (when (nil? edit-shipping)
          (button/store-setting-default
            {:onClick #(om/update-state! component assoc :shipping/edit-shipping current-shipping :error-message nil)}
            (dom/span nil "Edit address"))))

      (dom/div
        (css/add-class :subsection)
        (dom/p (css/add-class :subsection-title) "Delivery")

        (when (shipping-available? shipping-rules)
          (menu/vertical
            (css/add-classes [:section-list :section-list--delivery])
            (map (fn [r]
                   (let [{:shipping.rate/keys [free-above total info title]} r]
                     (menu/item
                       (css/add-class :section-list-item)
                       (dom/a
                         {:onClick #(do
                                     (om/update-state! component assoc :shipping/selected-rate r))}
                         (dom/div
                           (css/add-class :shipping-info)
                           (dom/input
                             {:type    "radio"
                              :name    "sulo-select-rate"
                              :checked (= selected-rate r)})
                           (dom/div
                             (css/add-class :shipping-rule)
                             (dom/p nil (dom/span nil (:shipping.rate/title r))
                                    (dom/br nil)
                                    (dom/small nil info))))
                         (dom/div
                           (css/add-class :shipping-cost)
                           (dom/p nil
                                  (dom/span nil (if (zero? total)
                                                  "Free"
                                                  (utils/two-decimal-price (:shipping.rate/total r))))
                                  (when (and (some? free-above)
                                             (< subtotal free-above))
                                    [(dom/br nil)
                                     (dom/small nil (str "Free for orders above " (utils/two-decimal-price free-above)))])))))))
                 (sort-by :shipping.rate/total shipping-rates))))))))


(defn render-payment-details [component]
  (let [{:query/keys [stripe-customer checkout taxes]} (om/props component)
        {:stripe/keys [sources]} stripe-customer
        {:shipping/keys [edit-shipping selected-rate] :as state
         :keys          [error-message]
         :coupon/keys   [applied-coupon]} (om/get-state component)
        {:payment/keys [payment-error card add-new-card? selected-card]} state
        ;{:keys [error amount sources]} props

        subtotal (compute-subtotal checkout)
        shipping (compute-shipping-fee selected-rate checkout)
        discount (compute-discount subtotal applied-coupon)
        tax-amount (compute-taxes taxes (- subtotal discount) shipping)
        grandtotal (- (+ subtotal shipping tax-amount) discount)
        ]

    (callout/callout
      (css/add-classes [:section :section--payment])
      (dom/h4 (css/text-align :center) "Secure payment")

      (dom/div
        (cond->> (css/add-class :checkout-payment)
                 (some? edit-shipping)
                 (css/add-class :hide))
        (menu/vertical
          (css/add-classes [:section-list :section-list--cards])
          (map-indexed (fn [i c]
                         (let [{:stripe.card/keys [brand last4 id]} c]
                           (menu/item
                             (css/add-class :section-list-item--card)
                             (dom/a
                               {:onClick #(do
                                           (ga/checkout-payment-option checkout brand)
                                           (om/update-state! component assoc :payment/selected-card id))}
                               (dom/input {:type    "radio"
                                           :name    "sulo-select-cc"
                                           :checked (= selected-card id)})
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
                             )))
                       sources)

          (menu/item
            (css/add-classes [:section-list-item--new-card])
            (dom/a
              (cond->> {:onClick #(do
                                   (ga/checkout-payment-option checkout "New card")
                                   (om/update-state! component assoc :payment/selected-card :new-card))}
                       (and (not-empty sources)
                            (not add-new-card?))
                       (css/add-class :hide))
              (dom/input
                (cond->> {:type    "radio"
                          :name    "sulo-select-cc"
                          :checked (= selected-card :new-card)}
                         (and (not-empty sources)
                              (not add-new-card?))
                         (css/add-class :hide)))
              (dom/div
                (cond->> {:id (:payment.stripe/card form-inputs)}
                         (and (not-empty sources)
                              (not add-new-card?))
                         (css/add-class :hide))))
            (if (and (not-empty sources)
                     (not add-new-card?))
              (button/store-setting-default
                {:onClick #(do
                            (ga/checkout-payment-option checkout "New card")
                            (om/update-state! component merge {:payment/add-new-card? true
                                                               :payment/selected-card :new-card}))}
                (dom/span nil "Add new card...")))))

        ))))

(defn read-taxes [component shipping subtotal]
  (om/transact! (om/get-reconciler component) [(list {:query/taxes
                                                      [:taxes/id :taxes/rate :taxes/freight-taxable?]}
                                                     {:destination shipping
                                                      :subtotal    subtotal
                                                      :shipping    10})]))

(defui Checkout-no-loader
  static om/IQuery
  (query [_]
    [
     {:query/checkout [:db/id
                       :store.item.sku/variation
                       :store.item.sku/inventory
                       {:store.item/_skus [:store.item/name
                                           :store.item/price
                                           {:store.item/photos [{:store.item.photo/photo [:photo/id]}
                                                                :store.item.photo/index]}
                                           {:store/_items [:db/id
                                                           {:store/shipping [{:shipping/rules [
                                                                                               ;:shipping.rule/pickup?
                                                                                               {:shipping.rule/destinations [:country/code]}
                                                                                               {:shipping.rule/rates [:shipping.rate/first
                                                                                                                      :shipping.rate/additional
                                                                                                                      :shipping.rate/free-above
                                                                                                                      :shipping.rate/title]}]}]}
                                                           {:store/profile [:store.profile/name
                                                                            {:store.profile/photo [:photo/id]}]}
                                                           {:store/status [:status/type]}]}]}]}

     {:query/stripe-customer [:stripe/id
                              :stripe/sources
                              :stripe/shipping
                              :stripe/default-source]}
     {:query/auth [:db/id
                   :user/email
                   :user/stripe]}
     {:query/countries [:country/code :country/name]}
     {:query/taxes [:taxes/id
                    :taxes/rate
                    :taxes/freight-taxable?]}
     :query/messages
     :query/locations])

  Object
  (apply-coupon [this input-code]
    (msg/om-transact! this [(list 'checkout/apply-coupon {:code input-code})])
    (debug "Apply code: " input-code))

  (change-country [this country-code]
    (let [{:keys [autocomplete]} (om/get-state this)
          {:query/keys [countries checkout]} (om/props this)
          country (some #(when (= (:country/code %) country-code) %) countries)]
      #?(:cljs
         (when autocomplete
           (places/set-country-restrictions autocomplete country-code)))
      (ga/checkout-shipping-option country-code)
      (om/update-state! this update
                        :shipping/edit-shipping (fn [s]
                                                  (cond-> (assoc-in s [:shipping/address :shipping.address/country] (or country {:country/code country-code}))
                                                          (not-empty (get ship/regions country-code))
                                                          (assoc-in [:shipping/address :shipping.address/region] "")
                                                          (empty? (get ship/regions country-code))
                                                          (assoc-in [:shipping/address :shipping.address/region] ""))))))
  (save-shipping [this shipping]
    (let [{:query/keys [checkout]} (om/props this)

          validation (validate/validate ::ship/shipping shipping form-inputs)
          shipping-rules (available-rules (om/props this) shipping)
          subtotal (compute-subtotal checkout)]

      (debug "validation: " validation)
      (when (shipping-available? shipping-rules)
        (when (nil? validation)
          (ga/checkout-payment-details checkout (:checkout/selected-card (om/get-state this)))
          (read-taxes this shipping subtotal))
        (om/update-state! this (fn [st]
                                 (cond-> (assoc st :input-validation validation)
                                         (nil? validation)
                                         (assoc :checkout/shipping shipping :shipping/edit-shipping nil)))))))
  (confirm-purchase [this]
    #?(:cljs
       (let [{:keys          [card]
              :payment/keys  [selected-card]
              :checkout/keys [shipping]
              :shipping/keys [selected-rate]} (om/get-state this)
             on-success (fn [token]
                          (.save-payment this (stripe/token->card token)))
             on-error (fn [error-message]
                        (debug "Payment error: " error-message)
                        (om/update-state! this assoc :error-message error-message))
             input-validation (validate/validate ::ship/shipping shipping form-inputs)]
         (debug "Input validation: " input-validation)
         (cond (some? input-validation)
               (om/update-state! this assoc :error-message "Oh no, the shipping address looks invalid, please make sure all required fields are filled out correctly.")
               (nil? selected-rate)
               (om/update-state! this assoc :error-message "Oops, no delivery option selected. Please select how to receive your items.")
               (nil? selected-card)
               (om/update-state! this assoc :error-message "Oops, no payment option selected. Please select how to pay.")
               :else
               (do
                 (mixpanel/track "Confirm purchase")
                 (if (= selected-card :new-card)
                   (stripe/source-card (shared/by-key this :shared/stripe)
                                       card
                                       {:on-success on-success
                                        :on-error   on-error})
                   (.save-payment this {:source selected-card})))))))


  (save-payment [this selected-card]
    #?(:cljs
       (let [{:keys [source is-new-source?]} selected-card]
         (if is-new-source?
           (do
             (msg/om-transact! this [(list 'stripe/update-customer {:source source})])
             (om/update-state! this assoc :loading/message "Validating card..."))
           (do
             (debug "USing old card: " selected-card)
             (.place-order this selected-card))))))

  (place-order [this payment]
    #?(:cljs
       (let [{:query/keys [taxes current-route checkout]} (om/props this)
             store (checkout-store checkout)
             {:checkout/keys [shipping]
              :shipping/keys [selected-rate]
              :coupon/keys   [applied-coupon]} (om/get-state this)
             subtotal (compute-subtotal checkout)
             shipping-fee (compute-shipping-fee selected-rate checkout)
             discount (compute-discount subtotal applied-coupon)
             tax-amount (compute-taxes taxes (- subtotal discount) shipping-fee)
             grandtotal (- (+ subtotal shipping-fee tax-amount) discount)
             {:keys [source]} payment]

         (debug "Place order with payment: " payment)
         (let [items checkout]
           (msg/om-transact! this [(list 'store/create-order {:order    {:source        source
                                                                         :shipping      shipping
                                                                         :items         items
                                                                         :tax-amount    tax-amount
                                                                         :grandtotal    grandtotal
                                                                         :coupon        applied-coupon
                                                                         :discount      discount
                                                                         :shipping-rate {:amount      shipping-fee
                                                                                         :title       (:shipping.rate/title selected-rate)
                                                                                         :description (:shipping.rate/info selected-rate)}
                                                                         :subtotal      subtotal}
                                                              :store-id (:db/id store)})]))
         (om/update-state! this assoc :loading/message "Placing your order..."))))

  (default-shipping [this]
    {:shipping/name    ""
     :shipping/address {:shipping.address/street   ""
                        :shipping.address/postal   ""
                        :shipping.address/locality ""
                        :shipping.address/country  {:country/code "CA"}}})
  (initial-state [this props]
    (let [{:query/keys [stripe-customer countries]} props
          current-shipping (:stripe/shipping stripe-customer)
          shipping-rules (available-rules props current-shipping)
          country-code (get-in current-shipping [:shipping/address :shipping.address/country :country/code])
          country (when country-code (some #(when (= (:country/code %) country-code) %) countries))
          updated-shipping (cond-> (assoc-in (merge (.default-shipping this) current-shipping) [:shipping/address :shipping.address/country] (or country
                                                                                                                                                 {:country/code "CA"}))
                                   (nil? country)
                                   (assoc-in [:shipping/address :shipping.address/region] ""))

          selected-card (or (:stripe/default-source stripe-customer) :new-card)]

      {:checkout/shipping      (when (some? current-shipping) updated-shipping)
       :shipping/edit-shipping (when-not (shipping-available? shipping-rules)
                                 updated-shipping)
       :payment/selected-card  selected-card
       :payment/add-new-card?  (= selected-card :new-card)}))

  (componentDidUpdate [this _ _]
    (let [{:query/keys [checkout taxes]} (om/props this)
          {:shipping/keys [selected-rate edit-shipping]
           :keys          [error-message]
           :coupon/keys   [applied-coupon]} (om/get-state this)
          subtotal (compute-subtotal checkout)
          shipping-fee (compute-shipping-fee selected-rate checkout)
          discount (compute-discount subtotal applied-coupon)
          tax-amount (compute-taxes taxes (- subtotal discount) shipping-fee)
          grandtotal (- (+ subtotal shipping-fee tax-amount) discount)]

      ;; Check response for creating a new card on the customer
      ;; (this will only happen if the user added a new card when checking out)
      (when-let [customer-response (msg/last-message this 'stripe/update-customer)]
        (when (msg/final? customer-response)
          (let [message (msg/message customer-response)]
            (msg/clear-messages! this 'stripe/update-customer)

            ;; Response success if the card is valid and added to the customer's list of cards
            (if (msg/success? customer-response)
              ;; If we had a pending payment already, that means we've already added a new card and tried
              ;; to charge it by placing an order. However, it was declined for some reason and has been
              ;; successfully removed from the customer again. (we do this so the user doesn't end up with
              ;; many added cards that didn't actually work)
              (if (some? (:checkout/pending-payment (om/get-state this)))
                (om/update-state! this dissoc :checkout/pending-payment :loading/message)
                ;; The newly created card was successfully created and added to the customer, let's place the order and charge the card.
                (let [pending-payment {:source (:id (:new-card message))}]
                  ;; We created a new card, so let's add it to pending to make sure it's successfully charged.
                  ;; If it's not, we'll remove it from the customer again.
                  (om/update-state! this assoc :checkout/pending-payment pending-payment)
                  (.place-order this pending-payment)))

              ;; If card couldn't be added, (invalid or other error) show user what's wrong.
              (om/update-state! this assoc :error-message message :loading/message nil)))))

      ;; Checked response from placing an order.
      (when-let [response (msg/last-message this 'store/create-order)]
        (when (msg/final? response)
          (let [message (msg/message response)]
            (msg/clear-messages! this 'store/create-order)
            (if (msg/success? response)
              ;; Successful order will re-route to user's orders.
              (let [{:query/keys [auth]} (om/props this)]
                ;; Successful order, send to Google analytics
                (ga/send-transaction checkout {:revenue grandtotal :tax tax-amount :shipping shipping-fee :id (:db/id message)})
                (routes/set-url! this :user/order {:order-id (:db/id message) :user-id (:db/id auth)}))
              ;; If order was unsuccessful, let's get the pending payment we added above and remove it from the customer.
              (let [{:checkout/keys [pending-payment]} (om/get-state this)]
                (when pending-payment
                  (msg/om-transact! this [(list 'stripe/update-customer {:remove-source (:source pending-payment)})]))
                (om/update-state! this assoc :error-message message :loading/message nil)))))))

    (let [coupon-msg (msg/last-message this 'checkout/apply-coupon)]
      (when (msg/final? coupon-msg)
        (msg/clear-messages! this 'checkout/apply-coupon)
        (if (msg/success? coupon-msg)
          (om/update-state! this assoc :coupon/applied-coupon (msg/message coupon-msg))
          (om/update-state! this assoc :coupon/error-message (msg/message coupon-msg))))))

  (componentDidMount [this]
    #?(:cljs
       (let [init-state (.initial-state this (om/props this))
             {:query/keys [checkout stripe-customer locations auth]} (om/props this)
             country (or (get-in init-state [:checkout/shipping :shipping/address :shipping.address/country :country/code])
                         (get-in init-state [:shipping/edit-shipping :shipping/address :shipping.address/country :country/code]))
             card (stripe/card-element (shared/by-key this :shared/stripe) (str "#" (:payment.stripe/card form-inputs)))
             autocomplete (places/mount-places-address-autocomplete
                            {:element-id "sulo-auto-complete"
                             :on-change  (fn [address]
                                           (debug "Autocomplete address: " address)
                                           (om/update-state! this assoc-in [:shipping/edit-shipping :shipping/address] address))})]

         (ga/checkout-shipping-address checkout country)
         (when (:checkout/shipping init-state)
           (ga/checkout-shipping-option (get-in init-state [:checkout/shipping :shipping/address :shipping.address/country :country/code])))
         (when (nil? auth)
           (routes/set-url! this :login))
         (when-not (= :status.type/open (-> (first checkout) :store.item/_skus :store/_items :store/status :status/type))
           (routes/set-url! this :shopping-bag))

         (places/set-country-restrictions autocomplete country)
         (om/update-state! this merge
                           init-state
                           {:card         card
                            :autocomplete autocomplete})

         (when (some? (-> stripe-customer :stripe/shipping :shipping/address :shipping.address/country :country/code))

           (read-taxes this (:checkout/shipping init-state) (compute-subtotal checkout))))))

  (initLocalState [this]
    (.initial-state this (om/props this)))

  (render [this]
    (let [{:query/keys [checkout taxes]} (om/props this)
          {:shipping/keys [selected-rate edit-shipping]
           :keys          [error-message]
           :coupon/keys [applied-coupon]} (om/get-state this)
          subtotal (compute-subtotal checkout)
          shipping-fee (compute-shipping-fee selected-rate checkout)
          discount (compute-discount subtotal applied-coupon)
          tax-amount (compute-taxes taxes (- subtotal discount) shipping-fee)
          grandtotal (- (+ subtotal shipping-fee tax-amount) discount)]


      (dom/div
        nil
        (when-let [loading-message (:loading/message (om/get-state this))]
          (common/loading-spinner nil (dom/span nil loading-message)))
        (grid/row-column
          nil
          (render-store-items this)

          (render-shipping-details this)
          (render-payment-details this)
          (when (not-empty error-message)
            (dom/p (->> (css/add-class :text-alert)
                        (css/text-align :right)) (dom/small nil error-message)))
          (when-not (some? edit-shipping)
            [
             (dom/div (css/text-align :right)
                      (button/store-navigation-cta
                        {:onClick #(.confirm-purchase this)}
                        (dom/span nil "Complete purchase"))
                      (dom/p nil
                             (dom/small nil "This sale will be processed as ")
                             (dom/small nil (utils/two-decimal-price grandtotal))
                             (dom/small nil " Canadian Dollars.")))
             (dom/div
               (css/text-align :center {:id "card-errors"})
               ;(dom/small nil (or error payment-error))
               )])
          ))))
  static script-loader/IRenderLoadingScripts
  (render-while-loading-scripts [this props]
    (dom/div
      {:id "sulo-checkout-loading"}
      (dom/i {:classes ["fa fa-spinner fa-pulse"]}))))

(def Checkout (script-loader/stripe-loader
                Checkout-no-loader
                #?(:cljs
                   [[#(and (exists? js/google)
                           (exists? js/google.maps)
                           (exists? js/google.maps.Circle))
                     "https://maps.googleapis.com/maps/api/js?key=AIzaSyB8bKA0NO74KlYr5dpoJgM_k6CvtjV8rFQ&libraries=places"]])))

(router/register-component :checkout Checkout)