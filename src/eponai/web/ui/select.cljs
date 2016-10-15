(ns eponai.web.ui.select
  (:require
    [cljsjs.react-select]
    [eponai.client.ui :refer-macros [opts]]
    [om.dom :as dom]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug]]))

(defn on-select-fn [component]
  (fn [sel]
    (let [selected (js->clj sel :keywordize-keys true)
          {:keys [on-select]} (om/get-computed component)]
      (om/update-state! component assoc :selected selected)
      (when on-select
        (on-select selected)))))

(defui Select
  Object
  (render [this]
    (let [{:keys [selected]} (om/get-state this)
          {:keys [options value disabled]} (om/props this)]
      (js/React.createElement
        js/Select
        (clj->js
          {:value     (or (:value selected) (:value value))
           :options   (clj->js options)
           :clearable false
           :onChange  (on-select-fn this)
           :disabled disabled})))))

(def ->Select (om/factory Select))

(defui SelectTags
  Object
  (render [this]
    (let [{:keys [selected]} (om/get-state this)
          {:keys [value options disabled]} (om/props this)]
      (js/React.createElement
        js/Select.Creatable
        (clj->js
          {:value             (clj->js (or selected value))
           :placeholder       "e.g. food"
           :options           (clj->js options)
           :disabled          disabled
           :multi             true
           :clearable         false
           :noResultsText     ""
           :onChange          (on-select-fn this)
           :promptTextCreator (fn [l]
                                (str "Create new tag '" l "'"))
           :filterOptions     (fn [options filter-str _]
                                (if (empty? filter-str)
                                  #js []
                                  (clj->js (filter #(clojure.string/starts-with?
                                                     (.toLowerCase (.-label %))
                                                     (.toLowerCase filter-str)) options))))})))))

(def ->SelectTags (om/factory SelectTags))