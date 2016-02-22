(ns eponai.client.ui.add-widget
  (:require [datascript.core :as d]
            [eponai.client.ui :refer-macros [opts]]
            [eponai.client.ui.widget :refer [->Widget]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [eponai.common.format :as f]))

;;; ####################### Actions ##########################

(defn save-widget [component widget]
  (om/transact! component `[(widget/save ~widget)
                            :query/dashboard]))

(defn select-graph-style [component style]
  (let [default-group-by {:graph.style/bar    :transaction/tags
                          :graph.style/area   :transaction/date
                          :graph.style/number :default}]
    (om/update-state! component
                      #(-> %
                           (assoc-in [:input-graph :graph/style] style)
                           (assoc-in [:input-report :report/group-by] (get default-group-by style))))))

(defn change-report-title [component title]
  (om/update-state! component assoc-in [:input-report :report/title] title))

(defn select-function [component function-id]
  (om/update-state! component assoc-in [:input-function :report.function/id] function-id))

(defn select-group-by [component input-group-by]
  (om/update-state! component assoc-in [:input-report :report/group-by] input-group-by))


;;;;; ################### UI components ######################

(defn button-class [field value]
  (if (= field value)
    "btn btn-info btn-md"
    "btn btn-default btn-md"))

(defn chart-function [component input-function]
  (let [function-id (:report.function/id input-function)]
    [:div
     [:h6 "Calculate"]
     [:div.btn-group
      [:button
       {:class    (button-class function-id :report.function.id/sum)
        :on-click #(select-function component :report.function.id/sum)}

       [:span "Sum"]]
      [:button
       {:class    (button-class function-id :report.function.id/mean)
        :on-click #(select-function component :report.function.id/mean)}
       [:span
        "Mean"]]]]))

(defn chart-group-by [component {:keys [report/group-by]} groups]
  [:div
   ;(opts {:style {:width "50%"}})
   [:h6 "Group by"]
   [:div.btn-group
    (map
      (fn [k]
        (let [conf {:transaction/tags     {:icon "fa fa-tag"
                                           :text "All Tags"}
                    :transaction/currency {:icon "fa fa-usd"
                                           :text "All Currencies"}
                    :transaction/date     {:icon "fa fa-calendar"
                                           :text "All Dates"}}]
          [:button
           (opts {:key [k]
                  :class    (button-class group-by k)
                  :on-click #(select-group-by component k)})
           [:i
            (opts {:key [(get-in conf [k :icon])]
                   :class (get-in conf [k :icon])
                   :style {:margin-right 5}})]
           [:span
            (opts {:key [(get-in conf [k :text])]})
            (get-in conf [k :text])]]))
      groups)]])


(defn chart-settings [component {:keys [input-graph input-function input-report]}]
  (let [{:keys [graph/style]} input-graph]
    [:div
     ;[:h5 "Configuration"]
     (cond
       (= style :graph.style/bar)
       [:div
        (chart-function component input-function)
        (chart-group-by component input-report [:transaction/tags :transaction/currency])]

       (= style :graph.style/area)
       [:div
        (chart-function component input-function)
        (chart-group-by component input-report [:transaction/date])]

       (= style :graph.style/number)
       [:div
        (chart-function component input-function)])]))


;;;;;;;; ########################## Om Next components ########################

(defui NewWidget
  static om/IQueryParams
  (params [_]
    {:budget-uuid nil})
  static om/IQuery
  (query [_]
    [{:query/all-dashboards [:dashboard/uuid
                             {:dashboard/budget [:budget/uuid
                                                 :budget/name
                                                 {:transaction/_budget [:transaction/uuid
                                                                        {:transaction/date [:date/ymd
                                                                                            :date/timestamp]}
                                                                        :transaction/amount
                                                                        {:transaction/tags [:tag/name]}]}]}]}])
  Object
  (initLocalState [this]
    (let [{:keys [query/all-dashboards]} (om/props this)
          dashboard (first all-dashboards)]
      {:input-graph     {:graph/style :graph.style/bar}
       :input-function  {:report.function/id :report.function.id/sum}
       :input-report    {:report/group-by :transaction/tags}
       :input-dashboard-uuid (str (:dashboard/uuid dashboard))
       :input-dashboard dashboard}))

  (componentWillMount [this]
    (let [{:keys [widget
                  dashboard]} (om/get-computed this)]
      (when widget
        (om/update-state! this assoc
                          :input-graph (:widget/graph widget)
                          :input-report (:widget/report widget)
                          :input-function (first (:report/functions (:widget/report widget)))))
      (when dashboard
        (om/update-state! this assoc
                          :input-dashboard-uuid (str (:dashboard/uuid dashboard))
                          :input-dashboard dashboard))))

  (update-dashboard [this dashboard-uuid-str]
    (let [{:keys [query/all-dashboards]} (om/props this)
          dashboards (group-by :dashboard/uuid all-dashboards)]
      (om/update-state! this assoc :input-dashboard-uuid dashboard-uuid-str
                        :input-dashboard (first (get dashboards (f/str->uuid dashboard-uuid-str))))))
  (render [this]
    (let [{:keys [query/all-dashboards]} (om/props this)
          {:keys [input-graph input-report input-dashboard-uuid input-dashboard] :as state} (om/get-state this)
          {:keys [on-close
                  widget]} (om/get-computed this)]
      (html
        [:div
         [:div.modal-header
          [:button.close
           {:on-click on-close}
           "x"]
          [:h6 "New widget"]]

         [:div.modal-body
          [:div.row

           [:div
            {:class "col-sm-6"}

            [:select.form-control
             {:on-change     #(.update-dashboard this (.-value (.-target %)))
              :type          "text"
              :default-value input-dashboard-uuid}
             (map
               (fn [{:keys [dashboard/budget] :as dashboard}]
                 [:option
                  (opts {:value (str (:dashboard/uuid dashboard))
                         :key   [(:dashboard/uuid dashboard)]})
                  (:budget/name budget)])
               all-dashboards)]
            [:input.form-control
             {:value       (:report/title input-report)
              :placeholder "Untitled"
              :on-change   #(change-report-title this (.-value (.-target %)))}]
            (chart-settings this state)]
           [:div
            {:class "col-sm-6"}
            (let [style (:graph/style input-graph)]
              [:div.btn-group
               [:div
                (opts {:class    (button-class style :graph.style/bar)
                       :on-click #(select-graph-style this :graph.style/bar)})
                "Bar Chart"]
               [:button
                (opts {:class    (button-class style :graph.style/area)
                       :on-click #(select-graph-style this :graph.style/area)})
                "Area Chart"]
               [:button
                (opts {:class    (button-class style :graph.style/number)
                       :on-click #(select-graph-style this :graph.style/number)})
                "Number"]])
            [:h6 "Preview"]
            [:div
             (opts {:style {:height 300}})
             (->Widget (om/computed {:widget/graph  input-graph
                                     :widget/report input-report}
                                    {:data (:transaction/_budget (:dashboard/budget input-dashboard))}))]]]]

         [:div.modal-footer
          (opts {:style {:display        "flex"
                         :flex-direction "row-reverse"}})
          [:button
           (opts
             {:class    "btn btn-default btn-md"
              :on-click on-close
              :style    {:margin 5}})
           "Cancel"]
          [:button
           (opts {:class    "btn btn-info btn-md"
                  :on-click (fn []
                              (save-widget this
                                           (cond-> state
                                                   true
                                                   (dissoc :on-close)

                                                   true
                                                   (assoc-in [:input-widget :widget/uuid] (or (:widget/uuid widget) (d/squuid)))

                                                   (not (:graph/uuid (:input-graph state)))
                                                   (assoc-in [:input-graph :graph/uuid] (d/squuid))

                                                   (not (:report.function/uuid (:input-function state)))
                                                   (assoc-in [:input-function :report.function/uuid] (d/squuid))

                                                   (not (:report/uuid (:input-report state)))
                                                   (assoc-in [:input-report :report/uuid] (d/squuid))))
                              (on-close))
                  :style    {:margin 5}})
           "Save"]
          ;[:div (str "State " (om/get-state this))]
          ]]))))

(def ->NewWidget (om/factory NewWidget))