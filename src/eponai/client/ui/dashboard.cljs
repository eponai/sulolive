(ns eponai.client.ui.dashboard
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [eponai.client.ui :refer [map-all] :refer-macros [style opts]]
            [eponai.client.ui.d3 :as d3]
            [cljs.core.async :as c :refer [chan]]
            [garden.core :refer [css]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [eponai.client.routes :as routes]
            [eponai.client.ui.tag :as tag]
            [eponai.client.report :as report]
            [eponai.client.ui.utils :as utils]
            [datascript.core :as d]))


(defui Widget
  static om/IQuery
  (query [_]
    [:widget/uuid
     :widget/width
     :widget/height
     :widget/filter
     {:widget/report [:report/uuid
                      :report/group-by
                      :report/title
                      {:report/functions [:report.function/uuid
                                          :report.function/attribute
                                          :report.function/id]}]}
     {:widget/graph [:graph/style]}])

  Object
  (render [this]
    (let [{:keys [widget/report
                  widget/graph] :as widget} (om/props this)
          {:keys [on-delete
                  data]} (om/get-computed this)
          report-data (report/create report data)]
      (html
        [:div.widget
         (opts {:style {:border        "1px solid #e7e7e7"
                        :border-radius "0.5em"
                        :padding       "30px 0 0 0"
                        :width         "100%"
                        :height        300
                        :position      :relative
                        :box-sizing    :border-box}})
         [:div
          (opts {:style {:position        :absolute
                         :top             0
                         :left            10
                         :right           10
                         :height          30
                         :display         :flex
                         :flex-direction  :row
                         :justify-content :space-between
                         :align-items     :flex-start}})
          [:h5
           (:report/title report)]
          (when on-delete
            [:a.close
             {:on-click #(on-delete widget)}
             "x"])]
         (let [{:keys [graph/style]} graph
               settings {:data         report-data
                         :id           (str (or (:widget/uuid widget) "widget"))
                         :width        "100%"
                         :height       "100%"
                         :title-axis-y "Amount ($)"}]
           (cond (= style :graph.style/bar)
                 (d3/->BarChart settings)

                 (= style :graph.style/area)
                 (d3/->AreaChart settings)

                 (= style :graph.style/number)
                 (d3/->NumberChart settings)))]))))

(def ->Widget (om/factory Widget))

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
        :on-click #(.select-function component :report.function.id/sum)}

       [:span "Sum"]]
      [:button
       {:class    (button-class function-id :report.function.id/mean)
        :on-click #(.select-function component :report.function.id/mean)}
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
                  :on-click #(.select-group-by component k)})
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

(defn header []
  "Add new widget")

(defui NewWidget
  Object
  (initLocalState [_]
    {:input-graph    {:graph/style :graph.style/bar}
     :input-function {:report.function/id :report.function.id/sum}
     :input-report   {:report/group-by :transaction/tags}})

  (select-graph-style [this style]
    (let [default-group-by {:graph.style/bar    :transaction/tags
                            :graph.style/area   :transaction/date
                            :graph.style/number :default}]
      (om/update-state! this
                        #(-> %
                             (assoc-in [:input-graph :graph/style] style)
                             (assoc-in [:input-report :report/group-by] (get default-group-by style))))))

  (select-function [this function-id]
    (om/update-state! this assoc-in [:input-function :report.function/id] function-id))

  (select-group-by [this input-group-by]
    (om/update-state! this assoc-in [:input-report :report/group-by] input-group-by))

  (change-report-title [this title]
    (om/update-state! this assoc-in [:input-report :report/title] title))

  (render [this]
    (let [{:keys [input-graph input-report] :as state} (om/get-state this)
          {:keys [on-close
                  on-save
                  transactions]} (om/get-computed this)]
      (html
        [:div
         [:div.modal-header
          [:button.close
           {:on-click on-close}
           "x"]
          [:h6 "New widget"]]

         [:div.modal-body
          ;[:h4 "Preview"]
          [:div.row

           [:div
            {:class "col-sm-6"}

            ;[:h6 "Title"]
            [:input.form-control
             {:value       (:report/title input-report)
              :placeholder "Untitled"
              :on-change   #(.change-report-title this (.-value (.-target %)))}]
            (chart-settings this state)]
           [:div
            {:class "col-sm-6"}
            (let [style (:graph/style input-graph)]
              [:div.btn-group
               [:div
                (opts {:class    (button-class style :graph.style/bar)
                       :on-click #(.select-graph-style this :graph.style/bar)})
                "Bar Chart"]
               [:button
                (opts {:class    (button-class style :graph.style/area)
                       :on-click #(.select-graph-style this :graph.style/area)})
                "Area Chart"]
               [:button
                (opts {:class    (button-class style :graph.style/number)
                       :on-click #(.select-graph-style this :graph.style/number)})
                "Number"]])
            [:h6 "Preview"]
            (->Widget (om/computed {:widget/graph  input-graph
                                    :widget/report input-report}
                                   {:data transactions}))]]]

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
                              (on-save (-> state
                                           (dissoc :on-close)
                                           (assoc-in [:input-widget :widget/uuid] (d/squuid))
                                           (assoc-in [:input-graph :graph/uuid] (d/squuid))
                                           (assoc-in [:input-function :report.function/uuid] (d/squuid))
                                           (assoc-in [:input-report :report/uuid] (d/squuid))))
                              (on-close))
                  :style    {:margin 5}})
           "Save"]
          [:div.text-center (str "State: " state)]]]))))

(def ->NewWidget (om/factory NewWidget))

(defui Dashboard
  static om/IQuery
  (query [_]
    [{:query/dashboard [:dashboard/uuid
                        {:dashboard/widgets (om/get-query Widget)}
                        {:dashboard/budget [:budget/uuid
                                            {:transaction/_budget [:transaction/uuid
                                                                   {:transaction/tags [:tag/name]}
                                                                   :transaction/amount
                                                                   {:transaction/date [:date/ymd
                                                                                       :date/timestamp]}]}]}]}])
  Object
  (initLocalState [_]
    {:add-widget false})
  (show-add-widget [this visible]
    (om/update-state! this assoc :add-widget visible))

  (save-widget [this widget]
    (om/transact! this `[(widget/save ~widget)
                         :query/dashboard]))
  (delete-widget [this widget]
    (om/transact! this `[(widget/delete ~(select-keys widget [:widget/uuid]))
                         :query/dashboard]))

  (render [this]
    (let [{:keys [query/dashboard]} (om/props this)
          {:keys [add-widget]} (om/get-state this)]
      (html
        [:div

         [:button
          {:class    "btn btn-default btn-md"
           :on-click #(.show-add-widget this true)}
          "New widget"]
         (when add-widget
           (utils/modal {:content  (->NewWidget (om/computed {}
                                                             {:on-save      #(.save-widget this (assoc % :input-dashboard dashboard))
                                                              :on-close     #(.show-add-widget this false)
                                                              :transactions (get-in dashboard [:dashboard/budget :transaction/_budget])}))
                         :on-close #(.show-add-widget this false)
                         :class    "modal-lg"}))
         ;[:a
         ; {:class "btn btn-default btn-md"
         ;  :href  (routes/inside "/widget/new")}
         ; "Edit"]

         (map
           (fn [widget-props]
             (->Widget
               (om/computed widget-props
                            {:data (-> dashboard
                                       :dashboard/budget
                                       :transaction/_budget)
                             :on-delete #(.delete-widget this %)})))
           (:dashboard/widgets dashboard))]))))

(def ->Dashboard (om/factory Dashboard))
