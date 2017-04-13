(ns eponai.common.ui.checkout.shipping
  (:require
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.elements.css :as css]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    #?(:cljs [cljs.spec :as s]
       :clj [clojure.spec :as s])
    #?(:cljs
       [eponai.web.utils :as web-utils])
    #?(:cljs
       [eponai.common.ui.checkout.google-places :as places])
    [taoensso.timbre :refer [debug]]))

(def shipping-opts
  {:address/full-name {:id           "sulo-shipping-full-name"
                       :type         "text"
                       :name         "name"
                       :autocomplete "name"}
   :address/street1   {:id           "sulo-shipping-street-address-1"
                       :type         "text"
                       :name         "ship-address"
                       :autocomplete "shipping address-line1"}
   :address/street2   {:id           "sulo-shipping-street-address-2"
                       :type         "text"
                       :name         "ship-address"
                       :autocomplete "shipping address-line2"}
   :address/postal    {:id           "sulo-shipping-postal-code"
                       :type         "text"
                       :name         "ship-zip"
                       :autocomplete "shipping postal-code"}
   :address/locality  {:id           "sulo-shipping-locality"
                       :type         "text"
                       :name         "ship-city"
                       :autocomplete "shipping locality"}
   :address/region    {:id           "sulo-shipping-region"
                       :name         "ship-state"
                       :autocomplete "shipping region"}
   :address/country   {:id           "sulo-shipping-country"
                       :name         "ship-country"
                       :autocomplete "shipping country"}})

(s/def :address/full-name (s/and string? #(not-empty %)))
(s/def :address/street1 (s/and string? #(not-empty %)))
(s/def :address/street2 (s/or :value string? :empty nil?))
(s/def :address/postal (s/and string? #(not-empty %)))
(s/def :address/locality (s/and string? #(not-empty %)))
(s/def :address/region (s/and string? #(re-matches #"\w{2}" %)))
(s/def :address/country (s/and string? #(re-matches #"\w{2}" %)))

(s/def :shipping/address (s/keys :req [:address/full-name
                                       :address/street1
                                       :address/postal
                                       :address/locality
                                       :address/region]
                                 :opt [:address/street2]))

(defn geo-locate [component]
  #?(:cljs
     (let [{:keys [autocomplete]} (om/get-state component)]
       (when autocomplete
         (if-let [geolocation (.-geolocation js/navigator)]
           (.getCurrentPosition geolocation
                                (fn [p]
                                  (debug "Position: " p)
                                  (let [geolocation #js {:lat (.. p -coords -latitude)
                                                         :lng (.. p -coords -longitude)}
                                        circle (js/google.maps.Circle. #js {:center geolocation
                                                                            :radius (.. p -coords -accuracy)})]
                                    (.setBounds autocomplete (.getBounds circle))))))))))

(defn is-invalid-path? [valid-err id-key]
  (let [invalid-paths (map #(first (:path %)) valid-err)]
    (some #(= % id-key) invalid-paths)))

(defn input-element [id-key label valid-err]
  (let [is-invalid-input? (is-invalid-path? valid-err id-key)
        opts (get shipping-opts id-key)]
    (dom/div nil
      (dom/label
        #js {:className (when is-invalid-input? "is-invalid-label")}
        label)
      (my-dom/input (cond-> opts
                            is-invalid-input?
                            (update :classes conj "is-invalid-input"))))))

(defn select-element [id-key label valid-err & children]
  (let [is-invalid-input? (is-invalid-path? valid-err id-key)
        opts (get shipping-opts id-key)]
    (dom/div nil
      (dom/label
        #js {:className (when is-invalid-input? "is-invalid-label")}
        label)
      (my-dom/select
        (cond-> opts
                is-invalid-input?
                (update :classes conj "is-invalid-input"))
        children))))

#?(:cljs
   (defn prefill-address-form [place]
     (let [long-val (fn [k & [d]] (get-in place [k :long] d))
           short-val (fn [k & [d]] (get-in place [k :short] d))
           {:address/keys [street1 postal locality region country]} shipping-opts]
       (set! (.-value (web-utils/element-by-id (:id street1))) (long-val :address))
       (set! (.-value (web-utils/element-by-id (:id postal))) (long-val :postal_code))
       (set! (.-value (web-utils/element-by-id (:id locality))) (long-val :locality))
       (set! (.-value (web-utils/element-by-id (:id country))) (short-val :country))
       (set! (.-value (web-utils/element-by-id (:id region))) (short-val :administrative_area_level_1)))))

(defui CheckoutShipping
  Object
  #?(:cljs
     (save-shipping
       [this]
       (let [{:address/keys [street1 street2 postal locality region country full-name]} shipping-opts
             {:keys [on-change]} (om/get-computed this)
             shipping {:address/full-name (web-utils/input-value-or-nil-by-id (:id full-name))
                       :address/street1   (web-utils/input-value-or-nil-by-id (:id street1))
                       :address/street2   (web-utils/input-value-or-nil-by-id (:id street2))
                       :address/locality  (web-utils/input-value-or-nil-by-id (:id locality))
                       :address/country   (web-utils/input-value-or-nil-by-id (:id country))
                       :address/region    (web-utils/input-value-or-nil-by-id (:id region))
                       :address/postal    (web-utils/input-value-or-nil-by-id (:id postal))}
             validation-err (s/explain-data :shipping/address shipping)]
         (when (nil? validation-err)
           (when on-change
             (on-change shipping)))
         (om/update-state! this assoc :validation-err validation-err))))

  (componentDidMount [this]
    #?(:cljs
       (let [autocomplete (places/mount-places-address-autocomplete {:element-id "auto-complete"
                                                                     :on-change  (fn [place]
                                                                                   (prefill-address-form place))})]
         (om/update-state! this assoc :autocomplete autocomplete))))
  (render [this]
    (let [{:keys [validation-err]} (om/get-state this)
          validation-problems (::s/problems validation-err)]
      (dom/div nil
        (dom/h3 nil "Shipping")
        (my-dom/div
          (->> (css/add-class ::css/callout))
          (my-dom/div
            (css/grid-row)
            (my-dom/div
              (->> (css/grid-column))
              (input-element :address/full-name
                             "Full name"
                             validation-problems)))
          (my-dom/div
            (css/grid-row)
            (my-dom/div
              (->> (css/grid-column))
              (dom/label nil "Address")
              (dom/input #js {:id      "auto-complete"
                              :type    "text"
                              :onFocus #(geo-locate this)})))
          (dom/hr nil)
          (dom/div nil
            (my-dom/div
              (css/grid-row)
              (my-dom/div
                (->> (css/grid-column))
                (select-element :address/country "Country" validation-problems
                                (dom/option #js {:value "CA"} "Canada")
                                (dom/option #js {:value "SE"} "Sweden")
                                (dom/option #js {:value "US"} "United States"))))


            (my-dom/div
              (css/grid-row)
              (my-dom/div
                (->> (css/grid-column)
                     (css/grid-column-size {:small 12 :medium 8}))
                (input-element :address/street1
                               "Street Address"
                               validation-problems))
              (my-dom/div
                (->> (css/grid-column)
                     (css/grid-column-size {:small 12 :medium 4}))
                (input-element :address/street2
                               "Apt/Suite/Other"
                               validation-problems)))
            (my-dom/div
              (css/grid-row)
              (my-dom/div
                (->> (css/grid-column)
                     (css/grid-column-size {:small 12 :large 4}))
                (input-element :address/locality
                               "City"
                               validation-problems))
              (my-dom/div
                (->> (css/grid-column))
                (select-element :address/region
                                "Province"
                                validation-problems
                                (dom/option #js {} "Select Province")
                                (dom/option #js {:value "bc"} "British Columbia")))
              (my-dom/div
                (css/grid-column)
                (input-element :address/postal
                               "Postal code"
                               validation-problems)))))

        (my-dom/div (css/text-align :right)
                    (dom/a #js {:className "button"
                                :onClick   #(.save-shipping this)}
                           "Next"))))))

(def ->CheckoutShipping (om/factory CheckoutShipping))
