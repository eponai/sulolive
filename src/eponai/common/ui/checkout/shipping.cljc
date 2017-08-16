(ns eponai.common.ui.checkout.shipping
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.input-validate :as validate]
    [om.next :as om :refer [defui]]
    #?(:cljs [cljs.spec.alpha :as s]
       :clj
    [clojure.spec.alpha :as s])
    #?(:cljs
       [eponai.web.utils :as web-utils])
    #?(:cljs
       [eponai.common.ui.checkout.google-places :as places])
    [eponai.common.ui.script-loader :as script-loader]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.web.ui.button :as button]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.utils :as ui-utils]
    [eponai.common.ui.common :as common]))

(def form-inputs
  {:shipping/name             "sulo-shipping-full-name"
   :shipping.address/street   "sulo-shipping-street-address-1"
   :shipping.address/street2  "sulo-shipping-street-address-2"
   :shipping.address/postal   "sulo-shipping-postal-code"
   :shipping.address/locality "sulo-shipping-locality"
   :shipping.address/region   "sulo-shipping-region"
   :shipping.address/country  "sulo-shipping-country"})

;(s/def :country/code (s/and string? #(re-matches #"\w{2}" %)))
;(s/def :shipping/name (s/and string? #(not-empty %)))
;(s/def :shipping.address/street (s/and string? #(not-empty %)))
;(s/def :shipping.address/street2 (s/or :value string? :empty nil?))
;(s/def :shipping.address/postal (s/and string? #(not-empty %)))
;(s/def :shipping.address/locality (s/and string? #(not-empty %)))
;(s/def :shipping.address/region (s/or :value #(string? (not-empty %)) :empty nil?))
;(s/def :shipping.address/country (s/keys :req [:country/code]))
;
;(s/def :shipping/address (s/keys :req [:shipping.address/street
;                                       :shipping.address/postal
;                                       :shipping.address/locality]
;
;                                 :opt [:shipping.address/street2
;                                       :shipping.address/region]))
(s/def ::shipping (s/keys :req [:shipping/address
                                :shipping/name]))

(defn geo-locate [component]
  #?(:cljs
     (let [{:keys [autocomplete]} (om/get-state component)]
       (when autocomplete
         (if-let [geolocation (.-geolocation js/navigator)]
           (.getCurrentPosition geolocation
                                (fn [^js/Geoposition p]
                                  (debug "Position: " p)
                                  (let [geolocation #js {:lat (.. p -coords -latitude)
                                                         :lng (.. p -coords -longitude)}
                                        circle (js/google.maps.Circle. #js {:center geolocation
                                                                            :radius (.. p -coords -accuracy)})
                                        ^js/google.maps.places.Autocomplete autocomplete autocomplete]
                                    (.setBounds autocomplete (.getBounds circle))))))))))

;(defn validate
;  [spec m & [prefix]]
;  (when-let [err (s/explain-data spec m)]
;    (let [problems (::s/problems err)
;          invalid-paths (map (fn [p]
;                               (str prefix (some #(get form-inputs %) p)))
;                             (map :path problems))]
;      {:explain-data  err
;       :invalid-paths invalid-paths})))

;(defn google-place->shipping [place]
;  (let [long-val (fn [k & [d]] (get-in place [k :long] d))
;        short-val (fn [k & [d]] (get-in place [k :short] d))
;        {:shipping.address/keys [street postal locality region country]} form-inputs
;        country-code (short-val :country)
;        address {:shipping.address/street   (long-val :address)
;                 :shipping.address/postal   (long-val :postal_code)
;                 :shipping.address/locality (or (long-val :locality) (long-val :postal_town) (long-val :sublocality_level_1))
;                 :shipping.address/region   (short-val :administrative_area_level_1)
;                 :shipping.address/country  {:country/code country-code}}]
;    (debug "Google address: " address)
;    address))

(defn prefill-address-form [shipping]
  #?(:cljs
     (let [address (:shipping/address shipping)
           {:shipping.address/keys [street street2 postal locality region country]} form-inputs]
       (set! (.-value (web-utils/element-by-id street)) (:shipping.address/street address ""))
       (set! (.-value (web-utils/element-by-id street2)) (:shipping.address/street2 address ""))
       (set! (.-value (web-utils/element-by-id postal)) (:shipping.address/postal address ""))
       (set! (.-value (web-utils/element-by-id locality)) (:shipping.address/locality address ""))
       (set! (.-value (web-utils/element-by-id region)) (:shipping.address/region address ""))
       (set! (.-value (web-utils/element-by-id country)) (:country/code (:shipping.address/country address) "CA")))))

(defn prefill-shipping-name [shipping]
  #?(:cljs
     (set! (.-value (web-utils/element-by-id (:shipping/name form-inputs))) (:shipping/name shipping ""))))

(defn render-delivery [component props]
  (let [{:keys [selected-rate available-rates subtotal store]} props]
    (dom/div
      (css/add-class :subsection)

      (when (not-empty available-rates)
        [
         (dom/p (css/add-class :subsection-title) "Delivery options")
         (menu/vertical
           (css/add-classes [:section-list :section-list--shipping])
           (map (fn [r]
                  (let [{:shipping.rate/keys [free-above total info]} r]
                    (menu/item
                      (css/add-class :section-list-item)
                      (dom/a
                        {:onClick #(.save-rate component r)}
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
                                                 (ui-utils/two-decimal-price (:shipping.rate/total r))))
                                 (when (and (some? free-above)
                                            (< subtotal free-above))
                                   [(dom/br nil)
                                    (dom/small nil (str "Free for orders above " (ui-utils/two-decimal-price free-above)))])))))))
                (sort-by :shipping.rate/total available-rates)))]))))

(defn render-checkout-shipping [this props computed state]
  (let [{:keys [input-validation]} state
        {:keys [collapse? shipping countries store available-rates selected-rate]} props
        {:keys [on-open on-save-shipping]} computed
        {:shipping/keys [address]} shipping]
    (debug "Render shipping: " shipping)
    (dom/div
      nil
      (dom/div
        (cond->> (css/add-class :subsection)
                 (not collapse?)
                 (css/add-class :hide))
        (dom/div
          nil
          (dom/div
            nil
            (common/render-shipping shipping nil))
          (button/store-setting-default
            {:onClick #(when on-open (on-open))}
            (dom/span nil "Edit address"))))
      (dom/div
        (cond->> (css/add-class :subsection)
                 collapse?
                 (css/add-class :hide))
        (grid/row
          nil
          (grid/column
            nil
            (dom/label nil "Country")
            (dom/select
              {:id           (:shipping.address/country form-inputs)
               :name         "ship-country"
               :autoComplete "shipping country"
               :defaultValue (or (:shipping.address/country address) "CA")
               :onChange     #(.select-country this (.-value (.-target %)))}
              (map (fn [c]
                     (dom/option {:value (:country/code c)} (:country/name c)))
                   (sort-by :country/name countries)))
            (when (empty? available-rates)
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
               ;; :defaultValue (:shipping/name shipping)
               ;:value (:shipping/name shipping)
               }
              input-validation)))

        (dom/div
          (when (= (:shipping.rate/title selected-rate) "Free pickup")
            (css/add-class :hide))
          (grid/row
               nil
               (grid/column
                 nil
                 (dom/input {:id          "auto-complete"
                             :placeholder "Enter address..."
                             :type        "text"
                             :onFocus     #(geo-locate this)})))
          (dom/hr nil)
          (dom/div
            nil


            (grid/row
              nil
              (grid/column
                (grid/column-size {:small 12 :medium 8})
                (dom/label nil "Address")
                (validate/input
                  {:id           (:shipping.address/street form-inputs)
                   :type         "text"
                   :defaultValue (:shipping.address/street address)
                   :name         "ship-address"
                   :autoComplete "shipping address-line1"}
                  input-validation))
              (grid/column
                (grid/column-size {:small 12 :medium 4})
                (dom/label nil "Apt/Suite/Other (optional)")
                (validate/input
                  {:type         "text"
                   :id           (:shipping.address/street2 form-inputs)
                   :defaultValue (:shipping.address/street2 address)
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
                   :defaultValue (:shipping.address/postal address)
                   }
                  input-validation))
              (grid/column
                (grid/column-size {:small 12 :large 4})
                (dom/label nil "City")
                (validate/input
                  {:type         "text"
                   :id           (:shipping.address/locality form-inputs)
                   :defaultValue (:shipping.address/locality address)
                   :name         "ship-city"
                   :autoComplete "shipping locality"}
                  input-validation))
              (grid/column
                nil
                (dom/label nil "State/Province (optional)")
                (validate/input
                  {:id           (:shipping.address/region form-inputs)
                   :defaultValue (:shipping.address/region address)
                   :name         "ship-state"
                   :type         "text"
                   :autoComplete "shipping region"}
                  input-validation))))

          (dom/div (css/text-align :right)
                   (when input-validation
                     (dom/p
                       nil
                       (dom/small (css/add-class :text-alert) "You have errors that need to be fixed before continuing")))
                   (dom/div
                     (css/add-class :action-buttons)
                     (button/cancel
                       {:onClick #(when on-save-shipping
                                   (on-save-shipping shipping))}
                       (dom/span nil "Cancel"))
                     (button/save
                       {:onClick #(when (not-empty available-rates)
                                   (.save-shipping this))}
                       (dom/span nil "Save")))
                   )))

      (render-delivery this props)
      )))

(defui CheckoutShipping-no-loader
  static script-loader/IRenderLoadingScripts
  (render-while-loading-scripts [this props]
    (render-checkout-shipping this props (om/get-computed props) nil))
  Object
  #?(:cljs
     (save-shipping
       [this]
       (let [{:shipping.address/keys [street street2 postal locality region country]
              :shipping/keys         [name]} form-inputs
             {:keys [on-save-shipping]} (om/get-computed this)
             shipping {:shipping/name    (web-utils/input-value-or-nil-by-id name)
                       :shipping/address {:shipping.address/street   (web-utils/input-value-or-nil-by-id street)
                                          :shipping.address/street2  (web-utils/input-value-or-nil-by-id street2)
                                          :shipping.address/locality (web-utils/input-value-or-nil-by-id locality)
                                          :shipping.address/country  {:country/code (web-utils/input-value-or-nil-by-id country)}
                                          :shipping.address/region   (web-utils/input-value-or-nil-by-id region)
                                          :shipping.address/postal   (web-utils/input-value-or-nil-by-id postal)}}
             validation (validate/validate ::shipping shipping form-inputs)]
         (debug "Validation: " validation)
         (when (nil? validation)
           (when on-save-shipping
             (on-save-shipping shipping)))
         (om/update-state! this assoc :input-validation validation))))
  (save-rate [this rate]
    (let [{:keys [on-save-shipping-rate]} (om/get-computed this)]
      (when on-save-shipping-rate
        (on-save-shipping-rate rate))))

  (select-country [this c]
    (let [{:keys [shipping]} (om/props this)
          {:keys [autocomplete]} (om/get-state this)
          {:keys [on-save-country]} (om/get-computed this)]
      #?(:cljs
         (.setComponentRestrictions autocomplete #js {:country c}))
      (when on-save-country
        (on-save-country c))))

  (componentDidMount [this]
    #?(:cljs
       (let [autocomplete (places/mount-places-autocomplete {:element-id        "auto-complete"
                                                                     :on-change (fn [place]
                                                                                   )})]
         (om/update-state! this assoc :autocomplete autocomplete)
         (let [{:keys [shipping]} (om/props this)
               country (get-in shipping [:shipping/address :shipping.address/country :country/code])]
           (prefill-shipping-name shipping)
           (prefill-address-form shipping)
           (.setComponentRestrictions autocomplete (clj->js {:country (or country [])}))))))
  (componentDidUpdate [this prev-props _]
    #?(:cljs
       (let [{:keys [shipping]} (om/props this)
             {:keys [autocomplete]} (om/get-state this)
             country (get-in shipping [:shipping/address :shipping.address/country :country/code])]
         (prefill-shipping-name shipping)
         (prefill-address-form shipping)
         (when autocomplete
           (.setComponentRestrictions autocomplete (clj->js {:country (or country [])}))))))

  (render [this]
    (debug "Shipping props " (om/props this))
    (render-checkout-shipping this
                              (om/props this)
                              (om/get-computed this)
                              (om/get-state this))))

(def CheckoutShipping (script-loader/js-loader
                        {:component CheckoutShipping-no-loader
                         #?@(:cljs [:scripts
                                    [[#(and (exists? js/google)
                                            (exists? js/google.maps)
                                            (exists? js/google.maps.Circle))
                                      "https://maps.googleapis.com/maps/api/js?key=AIzaSyB8bKA0NO74KlYr5dpoJgM_k6CvtjV8rFQ&libraries=places"]]])}))

(def ->CheckoutShipping (om/factory CheckoutShipping))
