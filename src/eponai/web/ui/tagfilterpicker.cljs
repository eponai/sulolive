(ns eponai.web.ui.tagfilterpicker
  (:require
    [cljs-time.core :as time]
    [cljs-time.periodic :as periodic]
    [cljs-time.format :as t.format]
    [om.dom :as dom]
    [om.next :as om :refer-macros [defui]]
    [eponai.common.format.date :as date]
    [eponai.web.ui.utils :as utils]
    [taoensso.timbre :refer-macros [debug]]))

(defui TagFilterPicker
  Object
  (init-state [this props]
    (let [{:keys [filters transactions]} props
          tags (sort-by :tag/name (into [] (comp (mapcat :transaction/tags)
                                                 (map #(select-keys % [:tag/name]))
                                                 (distinct))
                                        transactions))
          filtered (when (seq (:filter/include-tags filters))
                     (map #(select-keys % [:tag/name]) (:filter/include-tags filters)))]
      {:include-tags (set (or filtered tags))
       :tags         tags}))
  (initLocalState [this]
    (.init-state this (om/props this)))
  (componentWillReceiveProps [this new-props]
    (om/set-state! this (.init-state this new-props)))
  (render [this]
    (let [{:keys [include-tags is-showing? search-string tags]} (om/get-state this)
          {:keys [on-cancel on-apply]} (om/get-computed this)
          {:keys [class transactions filters]} (om/props this)
          filtered-tags (if (seq search-string)
                          (filter #(clojure.string/starts-with? (:tag/name %) search-string) tags)
                          tags)]
      ;(debug "render filter picker: " tags)
      ;(debug "render filter include: " include-tags)
      (dom/div
        #js {:className class}
        (dom/div
          nil
          (dom/a
            #js {:className "nav-link tagfilter"
                 :onClick #(om/update-state! this assoc :is-showing? true)}
            (dom/i
              #js {:className "fa fa-fw fa-tags"})
            (dom/small
              nil
              (count include-tags)))
          (when is-showing?
            (dom/div
              nil
              (utils/click-outside-target #(om/update-state! this assoc :is-showing? false))
              (dom/div
                #js {:className (str "tagfilterpicker menu-horizontal dropdown")}

                (dom/div
                  nil
                  (dom/h6
                    #js {:className "small-caps"}
                    "Toggle Tags")
                  (dom/div
                    #js {:className "actions"}
                    (dom/a
                      #js {:className "button small"
                           :onClick   #(do
                                        (om/update-state! this assoc :is-showing? false)
                                        (when on-apply
                                          (debug "On-apply include tags: " include-tags)
                                          (on-apply include-tags)))}
                      "Apply")
                    (dom/a
                      #js {:className "button small secondary"
                           :onClick   #(om/update-state! this assoc :is-showing? false)}
                      "Cancel"))
                  (dom/input
                    #js {:className   "tagsearch"
                         :value       (or search-string "")
                         :placeholder "Search..."
                         :type        "text"
                         :onChange    #(om/update-state! this assoc :search-string (.-value (.-target %)))}))

                (apply dom/div
                       #js {:className "tags"}
                       (into [] (map (fn [t]
                                       (let [is-active? (contains? include-tags t)]

                                         (dom/a
                                           #js {:className (str "tag" (when is-active?
                                                                        " active"))
                                                :onClick   #(if is-active?
                                                             (om/update-state! this update :include-tags disj t)
                                                             (om/update-state! this update :include-tags conj t))}
                                           (:tag/name t)))))
                             filtered-tags))))))))))

(def ->TagFilterPicker (om/factory TagFilterPicker {:keyfn :key}))
