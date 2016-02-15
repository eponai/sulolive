(ns eponai.client.ui.d3
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [eponai.client.ui :refer [map-all] :refer-macros [style opts]]
            [cljs.core.async :as c :refer [chan]]
            [garden.core :refer [css]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [cljsjs.d3]
            [cljsjs.nvd3]))

(defn build-svg [element width height]
  (-> js/d3
      (.select element)
      (.append "svg")
      (.attr "width" width)
      (.attr "height" height)))

(defui BarChart
  Object
  (componentDidMount [this]
    (let [{:keys [width height id]} (om/props this)
          svg (build-svg (str "#bar-chart-" id) width height)]
      (om/update-state! this assoc :svg svg)))

  (componentDidUpdate [this _ _]
    (let [{:keys [svg]} (om/get-state this)
          {:keys [data]} (om/props this)
          chart-data (clj->js data)]
      (.addGraph
        js/nv
        (fn []
          (let [chart (.. js/nv
                          -models
                          discreteBarChart
                          (x  #(.-name %))
                          (y #(.-value %))
                          (staggerLabels false)
                          (showValues true))]
            (.-xAxis chart)

            (.. chart
                -tooltip
                -enabled)

            (.. chart
                -yAxis
                (tickFormat (.. js/d3
                                (format ",.2f"))))
            (.. svg
                (datum chart-data)
                transition
                (duration 500)
                (call chart))
            (.. js/nv
                -utils
                (windowResize (.-update chart))))))))
  (render [this]
    (let [{:keys [id]} (om/props this)]
      (html
        [:div
         {:id (str "bar-chart-" id)}]))))

(def ->BarChart (om/factory BarChart))

(defui AreaChart
  Object
  (componentDidMount [this]
    (let [{:keys [width height id]} (om/props this)
          svg (build-svg ("#area-chart-" id) width height)]
      (om/update-state! this assoc :svg svg)))

  (componentDidUpdate [this _ _]
    (let [{:keys [svg]} (om/get-state this)
          {:keys [data]} (om/props this)
          chart-data (clj->js data)]
      (.. js/nv
          (addGraph (fn []
                      (let [chart (.. js/nv
                                      -models
                                      stackedAreaChart
                                      (margin #js {:right 100})
                                      (x #(.-name %))
                                      (y #(.-value %))
                                      (useInteractiveGuideline true)
                                      (showControls false)
                                      (clipEdge true))]
                        (.. chart
                            -xAxis
                            (tickFormat #((.. js/d3
                                              -time
                                              (format "%x"))
                                          (js/Date. %))))
                        (.. chart
                            -yAxis
                            (tickFormat (.. js/d3
                                            (format ",.2f"))))
                        (.. svg
                            (datum chart-data)
                            transition
                            (duration 500)
                            (call chart))
                        (.. js/nv
                            -utils
                            (windowResize (.-update chart)))))))))
  (render [this]
    (let [{:keys [id]} (om/props this)]
      (html
        [:div
         :id (str "area-chart-" id)]))))

(def ->AreaChart (om/factory AreaChart))
