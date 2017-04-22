(ns eponai.common.ui.checkout.google-places
  (:require
    [eponai.web.utils :as web-utils]
    [taoensso.timbre :refer [debug]]))

(defn place->address [^js/google.maps.places.PlaceResult place]
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

(defn mount-places-address-autocomplete [{:keys [on-change element-id]}]
  (let [bounds-circle (js/google.maps.Circle. #js {:center #js {:lat 49.2827
                                                                :lng 123.1207}
                                                   :radius 5000})
        autocomplete (js/google.maps.places.Autocomplete. (web-utils/element-by-id element-id)
                                                          #js {:types  #js ["address"]
                                                               :bounds (.getBounds bounds-circle)})]
    (.addListener autocomplete "place_changed" (fn []
                                                 (when on-change
                                                   (debug "Place: " (.getPlace autocomplete))
                                                   (on-change (place->address (.getPlace autocomplete))))))
    autocomplete))