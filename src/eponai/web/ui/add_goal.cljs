(ns eponai.web.ui.add-goal
  (:require
    [eponai.web.ui.add-widget.select-graph :refer [->SelectGraph]]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]))

(defui NewGoal
  Object
  (render [this]
    (let [{:keys [daily-limit]} (om/get-state this)]
      (html
        [:div
         [:ul.breadcrumbs
          [:li
           [:a
            "Dashboard"]]
          [:li
           "New Goal"]]
         [:div.row.column.small-12.medium-6
          [:div.row
           [:div.columns.small-3.text-right
            [:label "Daily limit:"]]
           [:div.columns.small-3.end
            [:input
             {:value     (or daily-limit "")
              :type      "number"
              :on-change #(om/update-state! this assoc :daily-limit (.-value (.-target %)))}]]]]


         ;(when (some? goal-type)
         ;  (cond (= goal-type :save)
         ;        (->SelectGraph (om/computed {:graph {:graph/style :graph.style/bar}}
         ;                                    {:styles [:graph.style/bar]}))))
         ]))))

(def ->NewGoal (om/factory NewGoal))