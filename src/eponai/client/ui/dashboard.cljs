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

  (delete-widget [this widget]
    (om/transact! this `[(widget/delete ~(select-keys widget [:widget/uuid]))
                         :query/dashboard]))
  (render [this]
    (let [{:keys [query/dashboard]} (om/props this)
          {:keys [add-widget]} (om/get-state this)]
      (html
        [:div

         ;[:button
         ; {:class    "btn btn-default btn-md"
         ;  :on-click #(.show-add-widget this true)}
         ; "New widget"]
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
