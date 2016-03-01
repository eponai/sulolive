(ns eponai.client.ui.add-widget
  (:require [datascript.core :as d]
            [eponai.client.ui :refer [update-query-params!] :refer-macros [opts]]
            [eponai.client.ui.widget :refer [->Widget]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [eponai.common.format :as f]
            [eponai.client.ui.utils :as utils]
            [eponai.common.report :as report]))

;;; ####################### Actions ##########################

(defn- select-graph-style [component style]
  (let [default-group-by {:graph.style/bar    :transaction/tags
                          :graph.style/area   :transaction/date
                          :graph.style/number :default}]
    (om/update-state! component
                      #(-> %
                           (assoc-in [:input-graph :graph/style] style)
                           (assoc-in [:input-report :report/group-by] (get default-group-by style))))))

(defn- change-report-title [component title]
  (om/update-state! component assoc-in [:input-report :report/title] title))

(defn- select-function [component function-id]
  (om/update-state! component assoc-in [:input-function :report.function/id] function-id))

(defn- select-group-by [component input-group-by]
  (om/update-state! component assoc-in [:input-report :report/group-by] input-group-by))


;;;;; ################### UI components ######################

(defn- button-class [field value]
  (if (= field value)
    "btn btn-info btn-md"
    "btn btn-default btn-md"))

(defn- chart-function [component input-function]
  (let [function-id (:report.function/id input-function)]
    (html
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
          "Mean"]]]])))

(defn- chart-group-by [component {:keys [report/group-by]} groups]
  (html
    [:div
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
             (opts {:key      [k]
                    :class    (button-class group-by k)
                    :on-click #(select-group-by component k)})
             [:i
              (opts {:key   [(get-in conf [k :icon])]
                     :class (get-in conf [k :icon])
                     :style {:margin-right 5}})]
             [:span
              (opts {:key [(get-in conf [k :text])]})
              (get-in conf [k :text])]]))
        groups)]]))

(defn- update-filter [component input-filter]
  (update-query-params! component assoc :filter input-filter))

(defn- add-tag [component tagname]
  (let [{:keys [input-filter]} (om/get-state component)
        new-filters (update input-filter :filter/include-tags #(conj % tagname))]

    (om/update-state! component assoc
                      :input-filter new-filters
                      :input-tag "")
    (update-filter component new-filters)))

(defn- chart-filter [component {:keys [input-tag]}]
  (html
    [:div
     [:h6 "Filters"]
     (utils/tag-input {:value input-tag
                       :placeholder "Filter tags..."
                       :on-add-tag #()})]))


(defn- chart-settings [component {:keys [input-graph input-function input-report input-filter]}]
  (let [{:keys [graph/style]} input-graph]
    (html
      [:div
       (cond
         (= style :graph.style/bar)
         [:div
          (chart-function component input-function)
          (chart-group-by component input-report [:transaction/tags :transaction/currency])
          (chart-filter component input-filter)]

         (= style :graph.style/area)
         [:div
          (chart-function component input-function)
          (chart-group-by component input-report [:transaction/date])
          (chart-filter component input-filter)]

         (= style :graph.style/number)
         [:div
          (chart-function component input-function)
          (chart-filter component input-filter)])])))


;;;;;;;; ########################## Om Next components ########################

(defui NewWidget
  static om/IQueryParams
  (params [_]
    {:input-filter nil})
  static om/IQuery
  (query [_]
    ['{(:query/all-transactions {:filter ?input-filter}) [:transaction/uuid
                                                          :transaction/amount
                                                          :transaction/conversion
                                                          {:transaction/tags [:tag/name]}
                                                          {:transaction/date [:date/ymd
                                                                              :date/timestamp]}]}])
  Object
  (initLocalState [_]
    {:input-graph          {:graph/style :graph.style/bar}
     :input-function       {:report.function/id :report.function.id/sum}
     :input-report         {:report/group-by :transaction/tags}})

  (componentWillMount [this]
    (let [{:keys [widget]} (om/get-computed this)]
      (when widget
        (om/update-state! this
                          #(-> %
                               (assoc :input-graph (:widget/graph widget)
                                      :input-report (:widget/report widget)
                                      :input-function (first (:report/functions (:widget/report widget))))
                               (assoc-in [:input-widget :widget/uuid] (:widget/uuid widget)))))))
  (render [this]
    (let [{:keys [query/all-transactions]} (om/props this)
          {:keys [input-graph input-report] :as state} (om/get-state this)
          {:keys [on-close
                  on-save
                  dashboard
                  index]} (om/get-computed this)]
      (html
        [:div
         [:div.modal-header
          [:button.close
           {:on-click on-close}
           "x"]
          [:h4 (str "New widget - " (:budget/name (:dashboard/budget dashboard)))]]

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
                                     :widget/report input-report
                                     :widget/data   (report/generate-data input-report all-transactions)}
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
                  :on-click #(on-save (cond-> state
                                              true
                                              (assoc :input-dashboard {:dashboard/uuid (:dashboard/uuid dashboard)})

                                              true
                                              (assoc-in [:input-widget :widget/index] index)

                                              (not (:widget/uuid (:input-widget state)))
                                              (assoc-in [:input-widget :widget/uuid] (d/squuid))

                                              (not (:graph/uuid (:input-graph state)))
                                              (assoc-in [:input-graph :graph/uuid] (d/squuid))

                                              (not (:report.function/uuid (:input-function state)))
                                              (assoc-in [:input-function :report.function/uuid] (d/squuid))

                                              (not (:report/uuid (:input-report state)))
                                              (assoc-in [:input-report :report/uuid] (d/squuid))))

                            :style {:margin 5}})
           "Save"]]]))))

(def ->NewWidget (om/factory NewWidget))