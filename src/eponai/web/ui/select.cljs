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
          {:keys [options value disabled clearable placeholder]} (om/props this)]
      (js/React.createElement
        js/Select
        (clj->js
          (cond->
            {:value       (or (:value selected) (:value value))
             :options     (clj->js options)
             :clearable   (boolean clearable)
             :onChange    (on-select-fn this)
             :disabled    disabled}
            (some? placeholder)
            (assoc :placeholder placeholder)))))))

(def ->Select (om/factory Select))

(defui SelectTags
  Object
  (render [this]
    (let [{:keys [selected]} (om/get-state this)
          {:keys [value options disabled clearable on-input-key-down]} (om/props this)]
      (js/React.createElement
        js/Select.Creatable
        (clj->js
          {:value             (clj->js (or selected value))
           :placeholder       "e.g. food"
           :options           (clj->js options)
           :disabled          disabled
           :multi             true
           :clearable         (boolean clearable)
           :noResultsText     ""
           :onChange          (on-select-fn this)
           :promptTextCreator (fn [l]
                                (str "Create new tag '" l "'"))
           :onInputKeyDown    #(do (debug "Got input key down event: " %) (on-input-key-down))
           :filterOptions     (fn [options filter-str _]
                                (if (empty? filter-str)
                                  #js []
                                  (clj->js (filter #(clojure.string/starts-with?
                                                     (.toLowerCase (.-label %))
                                                     (.toLowerCase filter-str)) options))))})))))

(def ->SelectTags (om/factory SelectTags))

(defn tags->options [all-tags]
  (->> all-tags
       (map (fn [t]
              {:label (:tag/name t)
               :value (:db/id t)}))
       (clj->js)))