(ns eponai.common.ui.checkout.google-places
  (:require
    [eponai.web.utils :as web-utils]
    [taoensso.timbre :refer [debug]]))

(defn place->address [place]
  (when place
    (let [address-comps (.-address_components place)
          ret
          (reduce (fn [m c]
                    (let [k (keyword (first (.-types c)))
                          v {:short (.-short_name c)
                             :long (.-long_name c)}]
                      (assoc m k v)))
                  {}
                  address-comps)]
      (debug "Ret : " ret)
      ret)))

(defn mount-places-address-autocomplete [{:keys [on-change element-id]}]
  (let [bounds-circle (js/google.maps.Circle. #js {:center #js {:lat 49.2827
                                                                :lng 123.1207}
                                                   :radius 50000})
        autocomplete (js/google.maps.places.Autocomplete. (web-utils/element-by-id element-id)
                                                          #js {:types  #js ["address"]
                                                               :bounds (.getBounds bounds-circle)})]
    (.addListener autocomplete "place_changed" (fn []
                                                 (when on-change
                                                   (on-change (place->address (.getPlace autocomplete))))))))