(ns eponai.web.ui.goal-settings
  (:require
    [eponai.web.ui.utils :as utils]
    [om.dom :as dom]
    [om.next :as om :refer-macros [defui]]))

(defui GoalSettings
  Object
  (initLocalState [this]
    (let [{:keys [limit]} (om/props this)]
      {:input-limit limit}))
  (render [this]
    (let [{:keys [is-showing? input-limit]} (om/get-state this)
          {:keys [on-change on-close on-open]} (om/get-computed this)]
      (dom/div
        #js {:className "nav-link"}
        (dom/div
          nil
          (dom/a
            #js {:className "nav-link has-tip top"
                 :title     "Goal settings"
                 :onClick   #(do (om/update-state! this assoc :is-showing? true)
                                 (when on-open
                                   (on-open)))}
            (dom/i
              #js {:className "fa fa-fw fa-star"}))

          (when is-showing?
            (dom/div
              nil
              (utils/click-outside-target #(om/update-state! this assoc :is-showing? false))
              (dom/div
                #js {:className "menu dropdown goal-settings"}
                (dom/input
                  #js {:value    (or input-limit "")
                       :type "text"
                       :onChange #(om/update-state! this assoc :input-limit (.-value (.-target %)))})
                (dom/div
                  #js {:className "actions"}
                  (dom/a
                    #js {:className "button small"
                         :onClick   #(do
                                      (om/update-state! this assoc :is-showing? false)
                                      (when on-change
                                        (on-change input-limit)))}
                    "Save")
                  (dom/a
                    #js {:className "button small secondary"
                         :onClick   #(om/update-state! this assoc :is-showing? false)}
                    "Close"))))))))))

(def ->GoalSettings (om/factory GoalSettings {:keyfn :key}))
