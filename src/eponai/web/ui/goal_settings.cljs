(ns eponai.web.ui.goal-settings
  (:require
    [eponai.web.ui.utils :as utils]
    [om.dom :as dom]
    [om.next :as om :refer-macros [defui]]))

(defui GoalSettings
  Object
  (render [this]
    (let [{:keys [is-showing? input-limit]} (om/get-state this)
          {:keys [on-change on-close]} (om/get-computed this)]
      (dom/div
        #js {:className "nav-link"}
        (dom/div
          nil
          (dom/a
            #js {:className "nav-link"
                 :onClick   #(om/update-state! this assoc :is-showing? true)}
            (dom/i
              #js {:className "fa fa-fw fa-star"}))

          (when is-showing?
            (dom/div
              nil
              (utils/click-outside-target #(om/update-state! this assoc :is-showing? false))
              (dom/div
                #js {:className "menu dropdown"}
                (dom/input
                  #js {:value    input-limit
                       :onChange #(om/update-state! this assoc :input-limit (.-value (.-target %)))})
                (dom/a
                  #js {:className "button small"
                       :onClick   #(do
                                    (om/update-state! this assoc :is-showing? false)
                                    (when on-change
                                      (on-change input-limit)))}
                  "Save")
                (dom/a
                  #js {:className "button secondary"
                       :onClick   #(om/update-state! this assoc :is-showing? false)}
                  "Close")))))))))

(def ->GoalSettings (om/factory GoalSettings {:keyfn :key}))
