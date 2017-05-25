(ns eponai.common.ui.checkout.shipping
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.input-validate :as validate]
    [om.next :as om :refer [defui]]
    #?(:cljs [cljs.spec :as s]
       :clj
    [clojure.spec :as s])
    #?(:cljs
       [eponai.web.utils :as web-utils])
    #?(:cljs
       [eponai.common.ui.checkout.google-places :as places])
    [eponai.common.ui.script-loader :as script-loader]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.web.ui.button :as button]))

(def form-inputs
  {:shipping/name             "sulo-shipping-full-name"
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

(defn validate
  [spec m & [prefix]]
  (when-let [err (s/explain-data spec m)]
    (let [problems (::s/problems err)
          invalid-paths (map (fn [p]
                               (str prefix (some #(get form-inputs %) p)))
                             (map :path problems))]
      {:explain-data  err
       :invalid-paths invalid-paths})))

#?(:cljs
   (defn prefill-address-form [place]
     (let [long-val (fn [k & [d]] (get-in place [k :long] d))
           short-val (fn [k & [d]] (get-in place [k :short] d))
           {:shipping.address/keys [street postal locality region country]} form-inputs]
       (set! (.-value (web-utils/element-by-id street)) (long-val :address))
       (set! (.-value (web-utils/element-by-id postal)) (long-val :postal_code))
       (set! (.-value (web-utils/element-by-id locality)) (long-val :locality))
       (set! (.-value (web-utils/element-by-id country)) (short-val :country))
       (set! (.-value (web-utils/element-by-id region)) (short-val :administrative_area_level_1)))))

(defn render-checkout-shipping [this props computed state]
  (let [{:keys [input-validation]} state
        {:keys [collapse? shipping]} props
        {:keys [on-open]} computed]
    (callout/callout
      nil
      (dom/div
        (css/add-class :section-title)
        (dom/p nil "1. Ship to"))
      (dom/div
        (when-not collapse?
          (css/add-class :hide))
        (let [{:shipping/keys [address]} shipping]
          (dom/div
            (css/add-classes [:section-form :section-form--address])
            (dom/div
              nil
              (dom/p nil (:shipping/name shipping))
              (dom/div nil (dom/span nil (:shipping.address/street address)))
              (dom/div nil (dom/span nil (:shipping.address/street2 address)))
              (dom/div nil
                       (dom/span nil
                                 (str
                                   (:shipping.address/locality address)
                                   ", "
                                   (:shipping.address/postal address)
                                   " "
                                   (:shipping.address/region address)
                                   )))
              (dom/div nil (dom/span nil (:shipping.address/country address))))))
        (button/user-setting-default
          {:onClick #(when on-open (on-open))}
          (dom/span nil "Edit address")))
      (dom/div
        (when collapse?
          (css/add-class :hide))
        (grid/row
          nil
          (grid/column
            nil
            (dom/label nil "Full name")
            (validate/input
              {:id           (:shipping/name form-inputs)
               :type         "text"
               :name         "name"
               :autoComplete "name"}
              input-validation)))
        (grid/row
          nil
          (grid/column
            nil
            ;(dom/label nil "Search address")
            (dom/input {:id          "auto-complete"
                        :placeholder "Enter location..."
                        :type        "text"
                        :onFocus     #(geo-locate this)})))
        (dom/hr nil)
        (dom/div
          nil
          (grid/row
            nil
            (grid/column
              nil
              (dom/label nil "Country")
              (dom/select
                {:id           (:shipping.address/country form-inputs)
                 :name         "ship-country"
                 :autoComplete "shipping country"}
                ;input-validation
                (dom/option {:value "CA"} "Canada")
                (dom/option {:value "SE"} "Sweden")
                (dom/option {:value "US"} "United States"))))


          (grid/row
            nil
            (grid/column
              (grid/column-size {:small 12 :medium 8})
              (dom/label nil "Address")
              (validate/input
                {:id           (:shipping.address/street form-inputs)
                 :type         "text"
                 :name         "ship-address"
                 :autoComplete "shipping address-line1"}
                input-validation))
            (grid/column
              (grid/column-size {:small 12 :medium 4})
              (dom/label nil "Apt/Suite/Other (optional)")
              (validate/input
                {:type "text"
                 :id   (:shipping.address/street2 form-inputs)}
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
                 :autoComplete "shipping postal-code"}
                input-validation))
            (grid/column
              (grid/column-size {:small 12 :large 4})
              (dom/label nil "City")
              (validate/input
                {:type         "text"
                 :id           (:shipping.address/locality form-inputs)
                 :name         "ship-city"
                 :autoComplete "shipping locality"}
                input-validation))
            (grid/column
              nil
              (dom/label nil "State/Province (optional)")
              (validate/input
                {:id           (:shipping.address/region form-inputs)
                 :name         "ship-state"
                 :type         "text"
                 :autoComplete "shipping region"}
                input-validation
                ;input-validation
                ;(dom/option {} "Select Province")
                ;(dom/option {:value "bc"} "British Columbia")
                ))
            ))

        (dom/div (css/text-align :right)
                 (when input-validation
                   (dom/p
                     nil
                     (dom/small (css/add-class :text-alert) "You have errors that need to be fixed before continuing")))
                 (dom/a
                   (css/button {:onClick #(.save-shipping this)})
                   "Next")
                 )))))

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
             {:keys [on-change]} (om/get-computed this)
             shipping {:shipping/name    (web-utils/input-value-or-nil-by-id name)
                       :shipping/address {:shipping.address/street   (web-utils/input-value-or-nil-by-id street)
                                          :shipping.address/street2  (web-utils/input-value-or-nil-by-id street2)
                                          :shipping.address/locality (web-utils/input-value-or-nil-by-id locality)
                                          :shipping.address/country  (web-utils/input-value-or-nil-by-id country)
                                          :shipping.address/region   (web-utils/input-value-or-nil-by-id region)
                                          :shipping.address/postal   (web-utils/input-value-or-nil-by-id postal)}}
             validation (validate ::shipping shipping)]
         (debug "Validation: " validate)
         (when (nil? validation)
           (when on-change
             (on-change shipping)))
         (om/update-state! this assoc :input-validation validation))))

  (componentDidMount [this]
    #?(:cljs
       (let [autocomplete (places/mount-places-address-autocomplete {:element-id "auto-complete"
                                                                     :on-change  (fn [place]
                                                                                   (prefill-address-form place))})]
         (om/update-state! this assoc :autocomplete autocomplete))))
  (componentWillReceiveProps [this next-props]
    #?(:cljs
       (let [{:keys [shipping]} next-props]
         (debug "Getting props with shipping: " shipping)
         (when (some? shipping)
           (let [address (:shipping/address shipping)
                 {:shipping.address/keys [street street2 postal locality region country]
                  :shipping/keys         [name]} form-inputs]
             (set! (.-value (web-utils/element-by-id name)) (:shipping/name shipping))
             (set! (.-value (web-utils/element-by-id street)) (:shipping.address/street address))
             (set! (.-value (web-utils/element-by-id street2)) (:shipping.address/street2 address))
             (set! (.-value (web-utils/element-by-id postal)) (:shipping.address/postal address))
             (set! (.-value (web-utils/element-by-id locality)) (:shipping.address/locality address))
             (set! (.-value (web-utils/element-by-id country)) (:shipping.address/country address))
             (set! (.-value (web-utils/element-by-id region)) (:shipping.address/region address)))))))
  (render [this]
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
