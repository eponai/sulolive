(ns eponai.web.ui.add-widget.add-goal
  (:require
    [cljs-time.coerce :as c]
    [cljs-time.core :as t]
    [eponai.client.ui :refer-macros [opts]]
    [eponai.common.report :as report]
    [eponai.web.ui.datepicker :refer [->Datepicker]]
    [eponai.web.ui.widget :refer [->Widget]]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug]]))

(defui NewGoal

  Object
  (set-input [this props]
    (let [{:keys [widget]} (::om/computed props)
          goal (get-in widget [:widget/report :report/goal])]
      (when widget
        (om/update-state! this assoc
                          :style (get-in widget [:widget/graph :graph/style])
                          :daily-limit (:goal/value goal)
                          :start (c/from-long (get-in goal [:goal/cycle :cycle/start]))))))
  (componentDidMount [this]
    (.set-input this (om/props this)))
  (componentWillReceiveProps [this new-props]
    (.set-input this new-props))

  (render [this]
    (let [{:keys [widget
                  on-change
                  transactions]} (om/get-computed this)
          {:keys [daily-limit
                  filtered-tags
                  start
                  end
                  style]} (om/get-state this)]
      (debug "Goal widget: " widget)
      (html
        [:div
         ;[:ul.breadcrumbs
         ; [:li
         ;  [:a.disabled
         ;   "New Goal"]]]
         [:div.row.column.small-12.medium-6
          [:div
           [:div.row
            (->Widget (assoc widget :widget/data (report/generate-data (:widget/report widget) transactions {:data-filter (get-in widget [:widget/graph :graph/filter])})))]
           [:div.row
            [:div.columns.small-12
             [:input
              {:type     "radio"
               :id       "burndown-option"
               :checked  (= style :graph.style/burndown)
               :on-click #(when (and (not= style :graph.style/burndown)
                                     on-change)
                           (on-change (-> widget
                                          (assoc-in [:widget/graph :graph/style] :graph.style/burndown)
                                          (update-in [:widget/report :report/goal :goal/value] * (t/number-of-days-in-the-month (t/today)))
                                          (assoc-in [:widget/report :report/goal :goal/cycle :cycle/period] :cycle.period/month))))}]
             [:label {:for "burndown-option"} "Burndown chart"]
             [:input
              {:type     "radio"
               :id       "progress-option"
               :checked  (= style :graph.style/progress-bar)
               :on-click #(when (and (not= style :graph.style/progress-bar)
                                     on-change)
                           (on-change
                             (-> widget
                                 (assoc-in [:widget/graph :graph/style] :graph.style/progress-bar)
                                 (assoc-in [:widget/report :report/goal :goal/cycle :cycle/period] :cycle.period/day)
                                 (update-in [:widget/report :report/goal :goal/value] / (t/number-of-days-in-the-month (t/today))))))}]
             [:label {:for "progress-option"} "Progress meter"]]]
           [:div.row
            [:div.columns.small-3.text-right
             [:label (if (= style :graph.style/progress-bar)
                       "Daily limit:"
                       "Monthly limit:")]]
            [:div.columns.small-3.end
             [:input
              {:value     (or (get-in widget [:widget/report :report/goal :goal/value]) "")
               :type      "number"
               :on-change #(let [value (.-value (.-target %))]
                            (debug "Value: " (type value))
                            (om/update-state! this assoc :daily-limit value)
                            (when on-change
                              (on-change (-> widget
                                             (assoc-in [:widget/report :report/goal :goal/value] value)))))}]]]
           [:div.row
            [:div.columns.small-3.text-right
             "Title:"]
            [:div.columns.small-9
             [:input
              {:value     (or (get-in widget [:widget/report :report/title]) "")
               :type      "text"
               :on-change #(let [t (.-value (.-target %))]
                            (when on-change
                              (on-change (assoc-in widget [:widget/report :report/title] t))))}]]]
           ]

          ;[:div.row
          ; [:div.columns.small-3.text-right
          ;  [:label "End Date:"]]
          ; [:div.columns.small-9.end
          ;  (->Datepicker
          ;    (opts {:key       [::date-picker]
          ;           :value     end
          ;           :on-change #(do
          ;                        (om/update-state! this assoc :end %)
          ;                        (when on-change
          ;                          (on-change (assoc-in widget [:widget/report :report/goal :goal/cycle :cycle/period-count]
          ;                                               (t/in-days (t/interval start (c/from-date %)))))))}))]]

          ;(filter/->TagFilter {:tags filtered-tags}
          ;                    {:on-change #(om/update-state! this assoc :filtered-tags %)})
          ]]))))

(def ->NewGoal (om/factory NewGoal))