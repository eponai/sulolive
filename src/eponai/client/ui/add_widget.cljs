(ns eponai.client.ui.add-widget
  (:require [datascript.core :as d]
            [eponai.client.ui :refer-macros [opts]]
            [eponai.client.ui.dashboard :refer [->Widget]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]))

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
    [{:query/all-transactions [:transaction/uuid
                               {:transaction/date [:date/ymd
                                                   :date/timestamp]}
                               :transaction/amount
                               {:transaction/tags [:tag/name]}]}
     {:query/dashboard [:dashboard/uuid
                        {:dashboard/budget [:budget/uuid
                                            :budget/name]}]}])
  Object
  (initLocalState [_]
    {:input-graph    {:graph/style :graph.style/bar}
     :input-function {:report.function/id :report.function.id/sum}
     :input-report   {:report/group-by :transaction/tags}})

  (render [this]
    (let [{:keys [query/all-transactions
                  query/dashboard]} (om/props this)
          {:keys [input-graph input-report] :as state} (om/get-state this)
          {:keys [on-close]} (om/get-computed this)]
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
                                    {:data all-transactions}))]]]]

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
                                           (-> state
                                               (dissoc :on-close)
                                               (assoc :input-dashboard dashboard)
                                               (assoc-in [:input-widget :widget/uuid] (d/squuid))
                                               (assoc-in [:input-graph :graph/uuid] (d/squuid))
                                               (assoc-in [:input-function :report.function/uuid] (d/squuid))
                                               (assoc-in [:input-report :report/uuid] (d/squuid))))
                              (on-close))
                  :style    {:margin 5}})
           "Save"]
          [:div.text-center (str "State: " state)]]]))))

(def ->NewWidget (om/factory NewWidget))