(ns eponai.web.ui.d3
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [eponai.client.ui :refer [map-all] :refer-macros [style opts]]
            [cljs.core.async :as c :refer [chan]]
            [garden.core :refer [css]]
            [goog.string :as gstring]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [cljsjs.d3]
            [cljsjs.nvd3]
            [taoensso.timbre :refer-macros [debug]]))

(defn build-svg [element width height]
  (-> js/d3
      (.select element)
      (.append "svg")
      (.style #js {:width width :height height})))

(defui NumberChart
  Object
  (initLocalState [_]
    {:start-val 0})
  (componentDidMount [this]
    (let [{:keys [id width height]} (om/props this)
          svg (build-svg (str "#number-chart-" id) width height)]
      (om/update-state! this assoc :svg svg)))
  (componentWillReceiveProps [this _]
    (let [{:keys [data]} (om/props this)
          [start-val] (:values data)]
      (om/update-state! this assoc :start-val (or start-val 0))))
  (componentDidUpdate [this _ _]
    (let [{:keys [svg]} (om/get-state this)
          {:keys [data]} (om/props this)
          {:keys [start-val]} (om/get-state this)
          [end-val] (:values data)]
      (let [text-element (.. svg
                             (selectAll ".txt")
                             (data #js [(gstring/format "%.2f" end-val)]))]
        (.. text-element
            enter
            (append "text")
            (attr "class" "txt stat")
            (attr "x" "50%")
            (attr "y" "50%")
            (attr "text-anchor" "middle"))

        (.. text-element
            (text (gstring/format "%.2f" start-val))
            transition
            (duration 500)
            (tween "text" (fn [d]
                            (this-as jthis
                              (let [i (.. js/d3
                                          (interpolate (.-textContent jthis) (cljs.reader/read-string d)))
                                    prec (.split d ".")
                                    round (if (> (.-length prec) 1)
                                            (.pow js/Math 10 (.-length (get prec 1)))
                                            1)]
                                (fn [t]
                                  (set! (.-textContent jthis) (gstring/format "%.2f" (/ (.round js/Math (* (i t) round)) round))))))))))))
  (render [this]
    (let [{:keys [id]} (om/props this)]
      (html
        [:div
         (opts {:id (str "number-chart-" id)
                :style {:height "100%"
                        :width "100%"}})]))))

(def ->NumberChart (om/factory NumberChart))

(defui BarChart
  Object
  (componentDidMount [this]
    (let [{:keys [id width height]} (om/props this)
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
                          (margin #js {:right 50
                                       :left 50
                                       :top 50
                                       :bottom 50})
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
         (opts {:id (str "bar-chart-" id)
                :style {:height "100%"
                        :width "100%"}})]))))

(def ->BarChart (om/factory BarChart))

(defui AreaChart
  Object
  (componentDidMount [this]
    (let [{:keys [id width height]} (om/props this)
          svg (build-svg (str "#area-chart-" id) width height)]
      (om/update-state! this assoc :svg svg)))

  (componentDidUpdate [this _ _]
    (let [{:keys [svg]} (om/get-state this)
          {:keys [data]} (om/props this)
          chart-data (or (clj->js data) #js [])]
      (.. js/nv
          (addGraph (fn []
                      (let [chart (.. js/nv
                                      -models
                                      stackedAreaChart
                                      (margin #js {:right 50
                                                   :left 50
                                                   :top 50
                                                   :bottom 50})
                                      (x #(.-name %))
                                      (y #(.-value %))
                                      (useInteractiveGuideline true)
                                      (showControls false)
                                      (showLegend false)
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
         (opts {:id (str "area-chart-" id)
                :style {:height "100%"
                        :width "100%"}})]))))

(def ->AreaChart (om/factory AreaChart))

(defui LineChart
  Object
  (componentDidMount [this]
    (let [{:keys [id width height]} (om/props this)
          svg (build-svg (str "#line-chart-" id) width height)]
      (om/update-state! this assoc :svg svg)))

  (componentDidUpdate [this _ _]
    (let [{:keys [svg]} (om/get-state this)
          {:keys [data]} (om/props this)
          chart-data (or (clj->js data) #js [])]
      (.. js/nv
        (addGraph (fn []
                    (let [chart (.. js/nv
                                    -models
                                    lineChart
                                    (margin #js {:right 50
                                                 :left 50
                                                 :top 50
                                                 :bottom 50})
                                    (x #(.-name %))
                                    (y #(.-value %))
                                    (useInteractiveGuideline true)
                                    (showLegend false)
                                    (showYAxis true)
                                    (showXAxis true))]
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
  (render [this]
    (let [{:keys [id]} (om/props this)]
      (html
        [:div
         (opts {:id (str "line-chart-" id)
                :style {:height "100%"
                        :width "100%"}})]))
;    nv.addGraph(function() {
;                            var chart = nv.models.lineChart()
;                                .margin({left: 100})  //Adjust chart margins to give the x-axis some breathing room.
;                                .useInteractiveGuideline(true)  //We want nice looking tooltips and a guideline!
;                                .transitionDuration(350)  //how fast do you want the lines to transition?
;                            .showLegend(true)       //Show the legend, allowing users to turn on/off line series.
;                            .showYAxis(true)        //Show the y-axis
;                            .showXAxis(true)        //Show the x-axis
;                            ;
;
;                            chart.xAxis     //Chart x-axis settings
;                            .axisLabel('Time (ms)')
;                                        .tickFormat(d3.format(',r'));
;
;                                        chart.yAxis     //Chart y-axis settings
;                                        .axisLabel('Voltage (v)')
;                                                    .tickFormat(d3.format('.02f'));
;
;/* Done setting the chart up? Time to render it!*/
;    var myData = sinAndCos();   //You need data...
;
;d3.select('#chart svg')    //Select the <svg> element you want to render the chart in.
;.datum(myData)         //Populate the <svg> element with chart data...
;.call(chart);          //Finally, render the chart!
;
;//Update the chart when window resizes.
;nv.utils.windowResize(function() { chart.update() });
;return chart;
;});
    ))

(def ->LineChart (om/factory LineChart))