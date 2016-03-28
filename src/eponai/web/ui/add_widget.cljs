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
            [cljs-time.format :as f]
            [eponai.web.routes :as routes]
            [cljs-time.core :as time]
            [cljs-time.coerce :as coerce]
            [eponai.common.format :as format]))

;;; ####################### Actions ##########################

(defn- select-graph-style [component style]
  (let [default-group-by {:graph.style/bar    :transaction/tags
                          :graph.style/area   :transaction/date
                          :graph.style/line   :transaction/date
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

(defn- chart-filters [component]
  (let [{:keys [show-filter?]} (om/get-state component)]
    (html
      [:div
       [:hr]
       (eponai.web.ui.all-transactions/filter-settings component)])))


(defn- chart-settings [component {:keys [input-graph input-function input-report input-filter]}]
  (let [{:keys [graph/style]} input-graph]
    (html
      [:div
       [:hr]
       (cond
         (= style :graph.style/bar)
         [:div
          [:div.row.small-up-2.collapse
           [:div.column
            (chart-function component input-function)]
           [:div.column
            (chart-group-by component input-report [:transaction/tags :transaction/currency])]]
          [:div.row
           (chart-filters component)]]

         (or (= style :graph.style/area) (= style :graph.style/line))
         [:div
          [:div.row.small-up-2.collapse
           [:div.column
            (chart-function component input-function)]
           [:div.column
            (chart-group-by component input-report [:transaction/date])]]
          [:div.row
           (chart-filters component)]]

         (= style :graph.style/number)
         [:div
          (chart-function component input-function)
          (chart-filters component)])])))


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
    (let [{:keys [index widget]} (om/get-computed this)]
      (if widget
        (let [input-filter (:widget/filter widget)]
          (update-query-params! this assoc :filter input-filter)
          {:input-graph    (:widget/graph widget)
           :input-report   (:widget/report widget)
           :input-function (first (:report/functions (:widget/report widget)))
           :input-widget   widget
           :input-filter   (update input-filter :filter/include-tags set)})
        {:input-graph    {:graph/style :graph.style/bar}
         :input-function {:report.function/id :report.function.id/sum}
         :input-report   {:report/group-by :transaction/tags}
         :input-widget   {:widget/index  index
                          :widget/width  25
                          :widget/height 2}
         :input-filter   {:filter/include-tags #{}}})))
  (set-this-month-filter [this]
    (let [update-filter-fn (fn [filter]
                             (-> filter
                                 (dissoc :filter/end-date :filter/last-x-days)
                                 (assoc :filter/start-date (let [today (time/today)
                                                                 year (time/year today)
                                                                 month (time/month today)]
                                                             (coerce/to-date (time/date-time year month 1))))))]
      (om/update-state! this update :input-filter update-filter-fn)
      (utils/update-filter this (:input-filter (om/get-state this)))))

  (set-last-x-days-filter [this span]
    (om/update-state! this update :input-filter (fn [filter]
                                                  (-> filter
                                                      (dissoc :filter/end-date :filter/start-date)
                                                      (assoc :filter/last-x-days span))))
    (utils/update-filter this (:input-filter (om/get-state this))))

  (reset-date-filters [this]
    (om/update-state! this update :input-filter dissoc :filter/start-date :filter/end-date :filter/last-x-days)
    (utils/update-filter this (:input-filter (om/get-state this))))

  (update-date-filter [this value]
    (let [time-type (cljs.reader/read-string value)]
      (cond
        (= time-type :all-time) (.reset-date-filters this)
        (= time-type :last-7-days) (.set-last-x-days-filter this 7)
        (= time-type :last-30-days) (.set-last-x-days-filter this 30)
        (= time-type :this-month) (.set-this-month-filter this)
        true (om/update-state! this update :input-filter dissoc :filter/last-x-days))
      (om/update-state! this assoc :date-filter time-type)))

  (format-filters [this]
    (let [{:keys [input-filter] :as st} (om/get-state this)
          formatted-filters (reduce (fn [m [k v]]
                                      (debug "Reducing: " m " k " k " v " v)
                                      (if (or (= :filter/start-date k)
                                              (= :filter/end-date k))
                                        (assoc m k (format/date->ymd-string v))
                                        (assoc m k v)))
                                    {}
                                    input-filter)]
      (debug "Formatted filters: " formatted-filters)
      (assoc st :input-filter formatted-filters)))

  (render [this]
    (let [{:keys [query/transactions]} (om/props this)
          {:keys [input-graph input-report input-filter] :as state} (om/get-state this)
          {:keys [on-close
                  on-save
                  on-delete
                  dashboard
                  widget]} (om/get-computed this)
          data (report/generate-data input-report input-filter transactions)]
      (html
        [:div.clearfix
         [:ul.breadcrumbs
          [:li
           [:a
            {:href (routes/inside "dashboard/" (:budget/uuid (:dashboard/budget dashboard)))}
            "Dashboard"]]
          [:li
           [:span (if widget "Edit widget" "New widget")]]]
         [:input
          {:value       (:report/title input-report)
           :type        "text"
           :placeholder "Untitled"
           :on-change   #(change-report-title this (.-value (.-target %)))}]
         [:hr]
         [:div
          (let [style (:graph/style input-graph)]
            ;[:div.row]
            [:fieldset
             [:input
              (opts {:type     "radio"
                     :on-click #(select-graph-style this :graph.style/bar)
                     :name     "graph"
                     :id       "graph-bar"
                     :checked  (= style :graph.style/bar)})]
             [:label {:for "graph-bar"}
              "Bar Chart"]
             [:input
              (opts {:type     "radio"
                     :on-click #(select-graph-style this :graph.style/area)
                     :name     "graph"
                     :id       "area-chart"
                     :checked  (= style :graph.style/area)})]
             [:label {:for "area-chart"}
              "Area Chart"]
             [:input
              (opts {:type     "radio"
                     :on-click #(select-graph-style this :graph.style/line)
                     :name     "graph"
                     :id       "line-chart"
                     :checked  (= style :graph.style/line)})]
             [:label {:for "line-chart"}
              "Line Chart"]
             [:input
              (opts {:type     "radio"
                     :on-click #(select-graph-style this :graph.style/number)
                     :name     "graph"
                     :id       "number-chart"
                     :checked  (= style :graph.style/number)})]
             [:label {:for "number-chart"}
              "Number"]])
          [:div
           (opts {:style {:height 300}})
           (->Widget (om/computed {:widget/graph  input-graph
                                   :widget/report input-report
                                   :widget/data   data}
                                  {:data transactions}))]]

         (chart-settings this state)


         [:div.float-right
          [:a.button.secondary
           {:on-click on-close}
           "Cancel"]
          [:a.button.primary
           {:on-click #(on-save (cond-> (.format-filters this)
                                        true
                                        (assoc :input-dashboard {:dashboard/uuid (:dashboard/uuid dashboard)})

                                        (not (:widget/uuid (:input-widget state)))
                                        (assoc-in [:input-widget :widget/uuid] (d/squuid))

                                        (not (:graph/uuid (:input-graph state)))
                                        (assoc-in [:input-graph :graph/uuid] (d/squuid))

                                        (not (:report/uuid (:input-report state)))
                                        (assoc-in [:input-report :report/uuid] (d/squuid))))}


           "Save"]]
         [:div.float-left
          (when on-delete
            [:a.button.alert
             {:on-click #(do (on-delete widget)
                             (on-close))}
             "Delete"])]]))))

(def ->NewWidget (om/factory NewWidget))