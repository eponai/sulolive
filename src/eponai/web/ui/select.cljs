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
      ;(om/update-state! component assoc :selected selected)
      (when on-select
        (on-select selected)))))

(defui Select
  Object
  (render [this]
    (let [{:keys [selected]} (om/get-state this)
          {:keys [options value disabled clearable placeholder tab-index]} (om/props this)]
      (js/React.createElement
        js/Select
        (clj->js
          (cond->
            {:value     (:value value)
             :options   (clj->js options)
             :clearable (boolean clearable)
             :onChange  (on-select-fn this)
             :disabled  disabled}
            (some? placeholder)
            (assoc :placeholder placeholder)
            (some? tab-index)
            (assoc :tabIndex (str tab-index))))))))

(def ->Select (om/factory Select))

(defui SelectTags
  Object
  (render [this]
    (let [{:keys [selected]} (om/get-state this)
          {:keys [value options disabled clearable on-input-key-down tab-index]} (om/props this)]
      (js/React.createElement
        js/Select.Creatable
        (clj->js
          (cond->
            {:value             (clj->js value)
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
                                                       (.toLowerCase filter-str)) options))))}
            (some? tab-index)
            (assoc :tabIndex (str tab-index))))))))

(def ->SelectTags (om/factory SelectTags))

(defn tags->options [all-tags]
  (->> all-tags
       (map (fn [t]
              {:label (:tag/name t)
               :value (:db/id t)}))
       (clj->js)))

(defui SegmentedOption
  Object
  (render [this]
    (let [{:keys [options value]
           option-name :name} (om/props this)
          {:keys [on-select]} (om/get-computed this)
          {:keys [selected]} (om/get-state this)]
      (html
        [:ul.segmented-container
         (map (fn [opt i]
                (let [id (str option-name "-" i)]
                  [:li.segmented-option
                   {:key id}
                   [:input
                    (cond-> {:id       id :type "radio"
                             :name     option-name
                             :on-click #(do
                                         (om/update-state! this assoc :selected opt)
                                         (when on-select
                                           (on-select opt)))}

                            (= (or (:value selected) (:value value)) (:value opt))
                            (assoc :checked :checked))]
                   [:label {:for id}
                    [:span (:label opt)]]
                   ]))
              options
              (range))]))))

(def ->SegmentedOption (om/factory SegmentedOption))