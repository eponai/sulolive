(ns eponai.web.ui.widget
  (:require [eponai.common.report :as report]
            [eponai.client.ui :refer-macros [opts]]
            [eponai.web.ui.d3 :as d3]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]))

(defui Widget
  static om/IQuery
  (query [_]
    [:widget/uuid
     :widget/width
     :widget/height
     {:widget/filter [{:filter/include-tags [:tag/name]}
                      {:filter/end-date [:date/timestamp]}
                      {:filter/start-date [:date/timestamp]}]}
     {:widget/report [:report/uuid
                      :report/group-by
                      :report/title
                      {:report/functions [:report.function/uuid
                                          :report.function/attribute
                                          :report.function/id]}]}
     :widget/index
     :widget/data
     {:widget/graph [:graph/style]}])

  Object
  (render [this]
    (let [{:keys [widget/report
                  widget/graph
                  widget/data] :as widget} (om/props this)
          {:keys [on-edit]} (om/get-computed this)]
      (html
        [:div.widget
         (opts {:style {:border        "1px solid #e7e7e7"
                        ;:border-radius "0.5em"
                        :padding       "30px 0 0 0"
                        :width         "100%"
                        :height        "100%"
                        :position      :relative
                        :box-sizing    :border-box
                        :background :white}})
         [:div
          (opts {:style {:position        :absolute
                         :top             0
                         :left            10
                         :right           0
                         :height          30
                         :display         :flex
                         :flex-direction  :row
                         :justify-content :space-between
                         :align-items     :flex-start}})
          [:h5
           (:report/title report)]
          [:div.flex-right
           [:a
            (opts {:style    {:padding "0.5em"}
                   :on-click #(on-edit (dissoc widget ::om/computed :widget/data))
                   :href     "#"})
            [:i.fa.fa-pencil]]
           [:a.draggable
            (opts {:style {:padding "0.5em"}})
            [:i.fa.fa-arrows.draggable]]
           ]]
         (let [{:keys [graph/style]} graph
               settings {:data         data
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