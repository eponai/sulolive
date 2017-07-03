(ns eponai.web.ui.checkout.shipping
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
    [eponai.web.ui.button :as button]))

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
;(s/def ::shipping (s/keys :req [:shipping/address
;                                :shipping/name]))

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

(defn google-place->shipping [place]
  (let [long-val (fn [k & [d]] (get-in place [k :long] d))
        short-val (fn [k & [d]] (get-in place [k :short] d))
        {:shipping.address/keys [street postal locality region country]} form-inputs
        country-code (short-val :country)
        address {:shipping.address/street   (long-val :address)
                 :shipping.address/postal   (long-val :postal_code)
                 :shipping.address/locality (or (long-val :locality) (long-val :postal_town) (long-val :sublocality_level_1))
                 :shipping.address/region   (short-val :administrative_area_level_1)
                 :shipping.address/country  {:country/code country-code}}]
    (debug "Google address: " address)
    address))

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