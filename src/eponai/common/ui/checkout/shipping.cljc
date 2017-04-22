(ns eponai.common.ui.checkout.shipping
  (:require
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.input-validate :as validate]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    #?(:cljs [cljs.spec :as s]
       :clj
    [clojure.spec :as s])
    #?(:cljs
       [eponai.web.utils :as web-utils])
    #?(:cljs
       [eponai.common.ui.checkout.google-places :as places])
    [taoensso.timbre :refer [debug]]))

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
(s/def :shipping.address/region (s/and string? #(re-matches #"\w{2}" %)))
(s/def :shipping.address/country (s/and string? #(re-matches #"\w{2}" %)))

(s/def :shipping/address (s/keys :req [:shipping.address/street
                                       :shipping.address/postal
                                       :shipping.address/locality
                                       :shipping.address/region]
                                 :opt [:shipping.address/street2]))
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

(defui CheckoutShipping
  Object
  #?(:cljs
     (save-shipping
       [this]
       (let [{:shipping.address/keys [street street2 postal locality region country]
              :shipping/keys         [name]} form-inputs
             {:keys [on-change]} (om/get-computed this)
             shipping {:shipping/name    (web-utils/input-value-or-nil-by-id name)
                       :shipping/address {:shipping.address/street   (web-utils/input-value-or-nil-by-id street)
                                          ;:shipping.address/street2  (web-utils/input-value-or-nil-by-id street2)
                                          :shipping.address/locality (web-utils/input-value-or-nil-by-id locality)
                                          :shipping.address/country  (web-utils/input-value-or-nil-by-id country)
                                          :shipping.address/region   (web-utils/input-value-or-nil-by-id region)
                                          :shipping.address/postal   (web-utils/input-value-or-nil-by-id postal)}}
             validation (validate ::shipping shipping)
             ]
         (debug "INPUT validation: " validation)
         (when (nil? validation)
           (when on-change
             (debug "ON CHANGE WITH SHIPPING: " shipping)
             (on-change shipping)))
         (om/update-state! this assoc :input-validation validation)
         )))

  (componentDidMount [this]
    #?(:cljs
       (let [autocomplete (places/mount-places-address-autocomplete {:element-id "auto-complete"
                                                                     :on-change  (fn [place]
                                                                                   (prefill-address-form place))})]
         (om/update-state! this assoc :autocomplete autocomplete))))
  (render [this]
    (let [{:keys [input-validation]} (om/get-state this)
          validation-problems (::s/problems input-validation)]
      (dom/div nil
        (dom/h3 nil "Shipping")
        (my-dom/div
          (->> (css/add-class ::css/callout))
          (my-dom/div
            (css/grid-row)
            (my-dom/div
              (->> (css/grid-column))
              (my-dom/label nil "Full name")
              (validate/input
                {:id           (:shipping/name form-inputs)
                 :type         "text"
                 :name         "name"
                 :autocomplete "name"}
                input-validation)))
          (my-dom/div
            (css/grid-row)
            (my-dom/div
              (->> (css/grid-column))
              (my-dom/label nil "Search address")
              (dom/input #js {:id      "auto-complete"
                              :type    "text"
                              :onFocus #(geo-locate this)})))
          (dom/hr nil)
          (dom/div nil
            (my-dom/div
              (css/grid-row)
              (my-dom/div
                (->> (css/grid-column))
                (my-dom/label nil "Country")
                (my-dom/select
                  {:id           (:shipping.address/country form-inputs)
                   :name         "ship-country"
                   :autocomplete "shipping country"}
                  ;input-validation
                  (dom/option #js {:value "CA"} "Canada")
                  (dom/option #js {:value "SE"} "Sweden")
                  (dom/option #js {:value "US"} "United States"))))


            (my-dom/div
              (css/grid-row)
              (my-dom/div
                (->> (css/grid-column)
                     (css/grid-column-size {:small 12 :medium 8}))
                (my-dom/label nil "Address")
                (validate/input
                  {:id           (:shipping.address/street form-inputs)
                   :type         "text"
                   :name         "ship-address"
                   :autocomplete "shipping address-line1"}
                  input-validation))
              (my-dom/div
                (->> (css/grid-column)
                     (css/grid-column-size {:small 12 :medium 4}))
                (my-dom/label nil "Apt/Suite/Other")
                (validate/input
                  {:type         "text"
                   :id           (:shipping.address/street2 form-inputs)
                   :name         "ship-address"
                   :autocomplete "shipping address-line2"}
                  input-validation)))
            (my-dom/div
              (css/grid-row)
              (my-dom/div
                (->> (css/grid-column)
                     (css/grid-column-size {:small 12 :large 4}))
                (my-dom/label nil "City")
                (validate/input
                  {:type         "text"
                   :id           (:shipping.address/locality form-inputs)
                   :name         "ship-city"
                   :autocomplete "shipping locality"}
                  input-validation))
              (my-dom/div
                (->> (css/grid-column))
                (my-dom/label nil "Province")
                (my-dom/select
                  {:id           (:shipping.address/region form-inputs)
                   :name         "ship-state"
                   :autocomplete "shipping region"}
                  ;input-validation
                  (dom/option #js {} "Select Province")
                  (dom/option #js {:value "bc"} "British Columbia")))
              (my-dom/div
                (css/grid-column)
                (my-dom/label nil "Postal code")
                (validate/input
                  {:id           (:shipping.address/postal form-inputs)
                   :type         "text"
                   :name         "ship-zip"
                   :autocomplete "shipping postal-code"}
                  input-validation)))))

        (my-dom/div (css/text-align :right)
                    (dom/a #js {:className "button"
                                :onClick   #(.save-shipping this)}
                           "Next"))))))

(def ->CheckoutShipping (om/factory CheckoutShipping))
