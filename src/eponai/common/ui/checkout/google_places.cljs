(ns eponai.common.ui.checkout.google-places
  (:require
    [eponai.web.utils :as web-utils]
    [taoensso.timbre :refer [debug]]))

(defn- place->map [^js/google.maps.places.PlaceResult place]
  (when place
    (let [address-comps (.-address_components place)
          address-name (.-name place)
          ret (reduce (fn [m ^js/google.maps.GeocoderAddressComponent c]
                        (let [k (keyword (first (.-types c)))
                              v {:short (.-short_name c)
                                 :long  (.-long_name c)}]
                          (assoc m k v)))
                      {:address {:short address-name :long address-name}}
                      address-comps)]
      (debug "Ret : " ret)
      ret)))

(defn place->address [^js/google.maps.places.PlaceResult place]
  (let [place (place->map place)
        long-val (fn [k & [d]] (get-in place [k :long] d))
        short-val (fn [k & [d]] (get-in place [k :short] d))
        country-code (short-val :country)
        country-name (long-val :country)
        address {:shipping.address/street   (long-val :address)
                 :shipping.address/postal   (long-val :postal_code)
                 :shipping.address/locality (or (long-val :locality) (long-val :postal_town) (long-val :sublocality_level_1))
                 :shipping.address/region   (short-val :administrative_area_level_1)
                 :shipping.address/country  {:country/code country-code
                                             :country/name country-name}}]
    address))

(defn set-country-restrictions [^js/google.maps.places.Autocomplete autocomplete country-code]
  (.setComponentRestrictions autocomplete (clj->js {:country (or country-code [])})))

(defn mount-places-address-autocomplete [{:keys [on-change element-id]}]
  (let [^js/google.maps.Circle bounds-circle (js/google.maps.Circle. #js {:center #js {:lat 49.2827
                                                                :lng 123.1207}
                                                   :radius 5000})
        ^js/google.maps.places.Autocomplete autocomplete (js/google.maps.places.Autocomplete. (web-utils/element-by-id element-id)
                                                          #js {:types  #js ["address"]
                                                               :bounds (.getBounds bounds-circle)})]
    (.addListener autocomplete "place_changed" (fn []
                                                 (when on-change
                                                   (debug "Place: " (.getPlace autocomplete))
                                                   (on-change (place->address (.getPlace autocomplete))))))
    autocomplete))