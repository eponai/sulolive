(ns eponai.web.ui.dataset-filter-picker
  (:require
    [om.dom :as dom]
    [om.next :as om :refer-macros [defui]]
    [eponai.web.ui.utils.filter :refer [->TagFilter]]
    [eponai.web.ui.utils :as utils]
    [taoensso.timbre :refer-macros [debug]]))

(defn filter-div [selection-value tag]
  (dom/div
    nil
    (dom/div
      #js {:className "filter-item"}
      (dom/select
        #js {:value selection-value}
        (dom/option
          #js {:value "Include"}
          "Include")
        (dom/option
          #js {:value "Exclude"}
          "Exclude"))
      (dom/span
        nil
        " with tag: ")
      (dom/input
        #js {:value (:tag/name tag)
             :type  "text"}))
    (dom/div
      #js {:className "and"}
      "and")))

(defui DatasetFilterPicker
  Object
  (select-tag-filter-type [this new-tag-filter-key]
    (let [{:keys [tag-filter-key tags input-filters]} (om/get-state this)
          {:keys [filters]} (om/props this)
          {:keys [on-change]} (om/get-computed this)]
      (when-not (= tag-filter-key new-tag-filter-key)
        (let [new-filters (cond (= new-tag-filter-key :filter/include-tags)
                                (-> input-filters
                                    (assoc :filter/exclude-tags (map #(assoc % :tag/status :deleted) tags))
                                    (assoc :filter/include-tags (map #(assoc % :tag/status :added) tags)))

                                (= new-tag-filter-key :filter/exclude-tags)
                                (-> filters
                                    (assoc :filter/include-tags (map #(assoc % :tag/status :deleted) tags))
                                    (assoc :filter/exclude-tags (map #(assoc % :tag/status :added) tags))))]
          (om/update-state! this assoc
                            :tag-filter-key new-tag-filter-key
                            :input-filters new-filters)))))

  (init-state [this props]
    (let [{:keys [filters]} props
          tag-filter-key (cond
                           (seq (:filter/exclude-tags filters))
                           :filter/exclude-tags
                           :else
                           :filter/include-tags)]
      {:tags (or (get filters tag-filter-key) [])
       :tag-filter-key tag-filter-key
       :input-filters filters}))

  (initLocalState [this]
    (merge (.init-state this (om/props this))
           {:computed/tag-filter-on-change (fn [tags]
                                             (let [{:keys [tag-filter-key]} (om/get-state this)]
                                               (om/update-state! this assoc-in [:input-filters tag-filter-key] tags)))}))
  (componentWillReceiveProps [this new-props]
    (om/set-state! this (.init-state this new-props)))
  (render [this]
    (let [{:keys [filters]} (om/props this)
          {:keys [is-showing? new-filter input-filters tags tag-filter-key
                  computed/tag-filter-on-change]} (om/get-state this)
          {:keys [on-change on-cancel]} (om/get-computed this)

          ;tag-filter-key (cond
          ;                 (seq (:filter/exclude-tags filters))
          ;                 :filter/exclude-tags
          ;                 :else
          ;                 :filter/include-tags)
          ;tags (or (get filters tag-filter-key) [])
          ]
      ;(debug "Tagfilter state: " input-filters)
      (dom/div
        #js {:className "nav-link datasetfilter"}

        (dom/div
          nil
          (dom/a
            #js {:className "nav-link has-tip top"
                 :title "Filter"
                 :onClick #(om/update-state! this assoc :is-showing? true)}
            (dom/i
              #js {:className "fa fa-filter"}))

          (when is-showing?
            (dom/div
              nil
              (utils/click-outside-target #(om/update-state! this assoc
                                                             :is-showing? false
                                                             :new-filter nil))
              (dom/div
                #js {:className (str "datasetfilterpicker menu dropdown clearfix")}

                (dom/h6
                  #js {:className "small-caps"}
                  "Filter Transactions")

                (dom/select
                  #js {:onChange #(.select-tag-filter-type this (keyword "filter" (.-value (.-target %))))
                       :value    (name tag-filter-key)}
                  (dom/option
                    #js {:value (name :include-tags)}
                    "with tags")
                  (dom/option
                    #js {:value (name :exclude-tags)}
                    "without tags"))

                (->TagFilter (om/computed {:tags (get input-filters tag-filter-key)}
                                          {:on-change   tag-filter-on-change
                                           :input-only? true}))
                ;(dom/div
                ;  nil
                ;  (dom/h6
                ;    #js {:className "small-caps"}
                ;    "Filter transactions")
                ;
                ;  (apply dom/div
                ;         nil
                ;         (map
                ;           #(filter-div "Include" %)
                ;           (:filter/include-tags filters)))
                ;  (when new-filter
                ;    (filter-div "Include" "none"))
                ;  (dom/div
                ;    #js {:className "button hollow expanded new"
                ;         :onClick #(om/update-state! this assoc :new-filter {:filter/include-tags {}})}
                ;    (dom/i
                ;      #js {:className "fa fa-plus"})
                ;    (dom/span
                ;      #js {:className "small-caps"}
                ;      "add filter"))
                ;  (apply dom/div
                ;         nil
                ;         (map (fn [t]
                ;                (dom/a
                ;                  #js {:className "button tiny hollow"}
                ;                  "Include tag: " (:tag/name t)))
                ;              (:filter/exclude-tags-tags filters))))
                (dom/div
                  #js {:className "actions"}
                  (dom/a
                    #js {:className "button small float-right"
                         :onClick   #(do
                                      (om/update-state! this assoc
                                                           :is-showing? false
                                                           :new-filter nil)
                                      (when on-change
                                        (debug "Datasetfilter:  notifying filters: " input-filters)
                                        (on-change input-filters)))}
                    "Apply")
                  (dom/a
                    #js {:className "button small secondary float-right"
                         :onClick   #(om/update-state! this assoc
                                                       :is-showing? false
                                                       :new-filter nil)}
                    "Close"))))))))))

(def ->DatasetFilterPicker (om/factory DatasetFilterPicker))


