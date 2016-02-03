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
    (let [{:keys [width height]} (om/props this)
          svg (build-svg "#bar-chart" width height)]
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
                          (tooltips true)
                          (showValues false)
                          )]
            (.-xAxis chart)
            (.. chart
                -yAxis
                (tickFormat (.. js/d3
                                (format ",.2f"))))
            (.. svg
                (datum chart-data)
                (call chart))
            (.. js/nv
                -utils
                (windowResize (.-update chart))))))))

  (render [_]
    (html
      [:div#bar-chart])))

(def ->BarChart (om/factory BarChart))

(defui AreaChart
  Object
  (componentDidMount [this]
    (let [{:keys [width height]} (om/props this)
          svg (build-svg "#area-chart" width height)]
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
                                      ;(rightAlignYAxis true)
                                      ;(showControls true)
                                      (clipEdge true)
                                      )]
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
                            (call chart))
                        (.. js/nv
                            -utils
                            (windowResize (.-update chart)))))))))

  (render [_]
    (html
      [:div#area-chart])))

(def ->AreaChart (om/factory AreaChart))
