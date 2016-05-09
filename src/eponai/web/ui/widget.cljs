(ns eponai.web.ui.widget
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.web.ui.d3.area-chart :refer [->AreaChart]]
    [eponai.web.ui.d3.bar-chart :refer [->BarChart]]
    [eponai.web.ui.d3.burndown-chart :refer [->BurndownChart]]
    [eponai.web.ui.d3.line-chart :refer [->LineChart]]
    [eponai.web.ui.d3.number-chart :refer [->NumberChart]]
    [eponai.web.ui.d3.progress-bar :refer [->ProgressBar]]
    [eponai.web.routes :as routes]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug]]))

(defn dimensions [graph]
  (let [style (:graph/style graph)]
    (cond (or
            (= style :graph.style/bar)
            (= style :graph.style/line)
            (= style :graph.style/area))
          {:maxH 3
           :minH 2
           :minW 50
           :maxW 100}

          :else
          {:minH 2
           :minW 25
           :maxW 100})))
(defui Widget
  static om/IQuery
  (query [_]
    '[:widget/uuid
     :widget/width
     :widget/height
     {:widget/filter [*
                      {:filter/include-tags [:tag/name]}
                      {:filter/exclude-tags [:tag/name]}
                      {:filter/end-date [:date/timestamp]}
                      {:filter/start-date [:date/timestamp]}]}
     {:widget/report [*
                      {:report/track [*
                                      {:track/functions [*]}]}
                      {:report/goal [*
                                     {:goal/cycle [*]}]}]}
     :widget/index
     :widget/data
     {:widget/graph [:graph/style
                     {:graph/filter [{:filter/include-tags [:tag/name]}
                                     {:filter/exclude-tags [:tag/name]}]}]}])

  Object
  (render [this]
    (let [{:keys [widget/report
                  widget/graph
                  widget/data] :as widget} (om/props this)
          {:keys [project-id
                  id]} (om/get-computed this)]
      (debug "Render widget: " widget)
      (html
        [:div.widget
         [:div.widget-title
          [:p [:strong (:report/title report)]]
          [:div.flex-right.widget-menu
           [:a.widget-edit.secondary
            (opts {:style {:padding "0.5em"}
                   ;:on-click #(on-edit (dissoc widget ::om/computed :widget/data))
                   :href  (when project-id
                            (routes/key->route :route/project->widget+type+id {:route-param/project-id  project-id
                                                                               :route-param/widget-type (if (:report/track report) :track :goal)
                                                                               :route-param/widget-id   (str (:db/id widget))}))})
            [:i.fa.fa-pencil]]
           [:a.widget-move.secondary
            (opts {:style {:padding "0.5em"}})
            [:i.fa.fa-arrows.widget-move]]]]

         (let [{:keys [graph/style]} graph
               settings {:data         data
                         :id           (str (or (:widget/uuid widget) id "widget"))
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
                 (->LineChart settings)

                 (= style :graph.style/progress-bar)
                 (->ProgressBar settings)

                 (= style :graph.style/burndown)
                 (->BurndownChart settings)
                 ;[:div
                 ; "Progress: "
                 ; [:div.progress
                 ;  {:aria-valuenow  "50"
                 ;   :aria-valuemin  "0"
                 ;   :aria-valuetext "$10"
                 ;   :aria-valuemax  "100"}
                 ;  [:div.progress-meter
                 ;   (opts {:style {:width "50%"}})]]]
                 ))]))))

(def ->Widget (om/factory Widget))