(ns eponai.web.ui.add-widget
  (:require [datascript.core :as d]
            [eponai.client.ui :refer [update-query-params! map-all] :refer-macros [opts]]
            [eponai.web.ui.datepicker :refer [->Datepicker]]
            [eponai.web.ui.widget :refer [->Widget]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [eponai.web.ui.utils :as utils]
            [eponai.common.report :as report]
            [taoensso.timbre :refer-macros [debug]]
            [cljs-time.format :as f]))

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
    "button primary"
    "button secondary"))

(defn- chart-function [component input-function]
  (let [function-id (:report.function/id input-function)]
    (html
      [:div
       [:h6 "Calculate"]
       [:div.button-group
        [:a
         {:class    (button-class function-id :report.function.id/sum)
          :on-click #(select-function component :report.function.id/sum)}

         [:span "Sum"]]
        [:a
         {:class    (button-class function-id :report.function.id/mean)
          :on-click #(select-function component :report.function.id/mean)}
         [:span
          "Mean"]]]])))

(defn- chart-group-by [component {:keys [report/group-by]} groups]
  (html
    [:div
     [:h6 "Group by"]
     [:div.button-group
      (map
        (fn [k]
          (let [conf {:transaction/tags     {:icon "fa fa-tag"
                                             :text "Tags"}
                      :transaction/currency {:icon "fa fa-usd"
                                             :text "Currencies"}
                      :transaction/date     {:icon "fa fa-calendar"
                                             :text "Dates"}}]
            [:a
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

(defn- chart-filters [component {:keys [filter/include-tags
                                        filter/start-date
                                        filter/end-date]}]
  (html
    [:div
     [:h6 "Filters"]
     (->Datepicker
       (opts {:key         ["From date..."]
              :placeholder "From date..."
              :value       start-date
              :on-change   #(utils/select-date-filter component :filter/start-date %)}))
     (->Datepicker
       (opts {:key         ["To date..."]
              :placeholder "To date..."
              :value       end-date
              :on-change   #(utils/select-date-filter component :filter/end-date %)}))
     (utils/tag-filter component include-tags)]))


(defn- chart-settings [component {:keys [input-graph input-function input-report input-filter]}]
  (let [{:keys [graph/style]} input-graph]
    (html
      [:div
       (cond
         (= style :graph.style/bar)
         [:div
          (chart-function component input-function)
          (chart-group-by component input-report [:transaction/tags :transaction/currency])
          (chart-filters component input-filter)]

         (= style :graph.style/area)
         [:div
          (chart-function component input-function)
          (chart-group-by component input-report [:transaction/date])
          (chart-filters component input-filter)]

         (= style :graph.style/number)
         [:div
          (chart-function component input-function)
          (chart-filters component input-filter)])])))


;;;;;;;; ########################## Om Next components ########################

(defui NewWidget
  static om/IQueryParams
  (params [_]
    {:filter {:filter/include-tags #{}}})
  static om/IQuery
  (query [_]
    ['{(:query/transactions {:filter ?filter}) [:transaction/uuid
                                                :transaction/amount
                                                :transaction/conversion
                                                {:transaction/currency [:currency/code]}
                                                {:transaction/tags [:tag/name]}
                                                {:transaction/date [:date/ymd
                                                                    :date/timestamp]}]}])
  Object
  (initLocalState [this]
    (let [{:keys [index]} (om/get-computed this)]
      {:input-graph    {:graph/style :graph.style/bar}
       :input-function {:report.function/id :report.function.id/sum}
       :input-report   {:report/group-by :transaction/tags}
       :input-widget   {:widget/index  index
                        :widget/width  33
                        :widget/height 1}
       :input-filter   {:filter/include-tags #{}}}))

  (componentWillMount [this]
    (let [{:keys [widget]} (om/get-computed this)
          {:keys [input-filter]} (om/get-state this)
          new-filters (update input-filter :filter/include-tags #(set (concat % (:filter/include-tags (:widget/filter widget)))))]
      (when widget
        (om/update-state! this
                          (fn [st]
                            (-> st
                                (assoc :input-graph (:widget/graph widget)
                                       :input-report (:widget/report widget)
                                       :input-function (first (:report/functions (:widget/report widget))))
                                (assoc-in [:input-widget :widget/uuid] (:widget/uuid widget))
                                (assoc :input-filter new-filters))))
        (update-query-params! this assoc :filter input-filter))))
  (render [this]
    (let [{:keys [query/transactions]} (om/props this)
          {:keys [input-graph input-report input-filter] :as state} (om/get-state this)
          {:keys [on-close
                  on-save
                  on-delete
                  dashboard
                  widget]} (om/get-computed this)]
      (html
        [:div
         [:h3 (str "New widget - " (:budget/name (:dashboard/budget dashboard)))]

         [:div.row.small-up-1
          [:input
           {:value       (:report/title input-report)
            :type        "text"
            :placeholder "Untitled"
            :on-change   #(change-report-title this (.-value (.-target %)))}]
          [:div.column
           (let [style (:graph/style input-graph)]
             [:div.button-group
              [:a
               (opts {:class    (button-class style :graph.style/bar)
                      :on-click #(select-graph-style this :graph.style/bar)})
               "Bar Chart"]
              [:a
               (opts {:class    (button-class style :graph.style/area)
                      :on-click #(select-graph-style this :graph.style/area)})
               "Area Chart"]
              [:a
               (opts {:class    (button-class style :graph.style/number)
                      :on-click #(select-graph-style this :graph.style/number)})
               "Number"]])
           [:div
            (opts {:style {:height 300}})
            (->Widget (om/computed {:widget/graph  input-graph
                                    :widget/report input-report
                                    :widget/data   (report/generate-data input-report input-filter transactions)}
                                   {:data transactions}))]]

          [:div.column
           (chart-settings this state)]]

         [:div
          (opts {:style {:display         :flex
                         :flex-direction  :row-reverse
                         :justify-content :space-between}})

          [:div
           (opts {:style {:display        "flex"
                          :flex-direction "row-reverse"}})
           [:a.button.secondary
            {:on-click on-close}
            "Cancel"]
           [:a.button.primary
            {:on-click #(on-save (cond-> state
                                                true
                                                (assoc :input-dashboard {:dashboard/uuid (:dashboard/uuid dashboard)})

                                                (not (:widget/uuid (:input-widget state)))
                                                (assoc-in [:input-widget :widget/uuid] (d/squuid))

                                                (not (:graph/uuid (:input-graph state)))
                                                (assoc-in [:input-graph :graph/uuid] (d/squuid))

                                                (not (:report.function/uuid (:input-function state)))
                                                (assoc-in [:input-function :report.function/uuid] (d/squuid))

                                                (not (:report/uuid (:input-report state)))
                                                (assoc-in [:input-report :report/uuid] (d/squuid))))}


            "Save"]]
          (when on-delete
            [:a.button.alert
             {:on-click #(do (on-delete widget)
                             (on-close))}
             "Delete"])]]))))

(def ->NewWidget (om/factory NewWidget))