(ns eponai.web.ui.widget
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.web.ui.d3.area-chart :refer [->AreaChart]]
    [eponai.web.ui.d3.bar-chart :refer [->BarChart]]
    [eponai.web.ui.d3.line-chart :refer [->LineChart]]
    [eponai.web.ui.d3.number-chart :refer [->NumberChart]]
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
     {:widget/graph [:graph/style
                     {:graph/filter [{:filter/include-tags [:tag/name]}
                                     {:filter/end-date [:date/timestamp]}
                                     {:filter/start-date [:date/timestamp]}]}]}])

  Object
  (render [this]
    (let [{:keys [widget/report
                  widget/graph
                  widget/data] :as widget} (om/props this)
          {:keys [on-edit]} (om/get-computed this)]
      (html
        [:div.widget
         [:div.widget-title
          [:p [:strong (:report/title report)]]
          [:div.flex-right.widget-menu
           [:a.widget-edit.secondary
            (opts {:style    {:padding "0.5em"}
                   :on-click #(on-edit (dissoc widget ::om/computed :widget/data))
                   :href     "#"})
            [:i.fa.fa-pencil]]
           [:a.widget-move.secondary
            (opts {:style {:padding "0.5em"}})
            [:i.fa.fa-arrows.widget-move]]]]

         (let [{:keys [graph/style]} graph
               settings {:data         data
                         :id           (str (or (:widget/uuid widget) "widget"))
                         ;; Pass the widget to make this component re-render
                         ;; if the widget data changes.
                         :widget       widget
                         :width        "100%"
                         :height       "100%"
                         :title-axis-y "Amount ($)"}]
           (cond (= style :graph.style/bar)
                 (->BarChart settings)

                 (= style :graph.style/area)
                 (->AreaChart settings)

                 (= style :graph.style/number)
                 (->NumberChart settings)

                 (= style :graph.style/line)
                 (->LineChart settings)))]))))

(def ->Widget (om/factory Widget))