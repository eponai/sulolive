(ns eponai.web.ui.d3.area-chart
  (:require
    [cljsjs.d3]
    [cljs-time.coerce :as c]
    [cljs-time.core :as t]
    [eponai.client.ui :refer-macros [opts]]
    [eponai.web.ui.d3 :as d3]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug]]))

(defui AreaChart
  Object
  (make-axis [_ width height]
    (let [x-scale (.. js/d3 -time scale
                      (range #js [0 width])
                      (nice (.. js/d3 -time -year)))
          y-scale (.. js/d3 -scale linear
                      (range #js [height 0])
                      (nice))]
      {:x-axis  (.. js/d3 -svg axis
                    (scale x-scale)
                    (orient "bottom")
                    (ticks (max (/ width 150) 2))
                    (tickSize (* -1 height) 0 0)
                    (tickFormat (fn [t]
                                  (let [time-format (.. js/d3
                                                        -time
                                                        (format "%b %d"))]
                                    (time-format (js/Date. t))))))
       :y-axis  (.. js/d3 -svg axis
                    (scale y-scale)
                    (orient "left")
                    (ticks (max (/ height 50) 2))
                    (tickSize (* -1 width) 0 0)
                    (tickFormat (.. js/d3
                                    (format ",.2f"))))
       :x-scale x-scale
       :y-scale y-scale}))

  (create [this]
    (let [{:keys [id width height data]} (om/props this)
          svg (d3/build-svg (str "#area-chart-" id) width height)
          js-data (clj->js data)

          {:keys [margin]} (om/get-state this)
          {inner-width :width
           inner-height :height} (d3/svg-dimensions svg {:margin margin})

          {:keys [x-axis y-axis x-scale y-scale]} (.make-axis this inner-width inner-height)
          color-scale (.. js/d3 -scale category20)

          stack (.. js/d3 -layout stack
                    (values (fn [d]
                              (.-values d)))
                    (x (fn [d] (.-name d)))
                    (y (fn [d] (.-value d))))

          area (.. js/d3 -svg area
                   (x  #(x-scale (.-name %)))
                   (y0 #(y-scale (.-y0 %)))
                   (y1 #(y-scale (+ (.-y0 %) (.-y %)))))

          graph (.. svg
                    (append "g")
                    (attr "class" "chart")
                    (attr "transform" (str "translate(" (:left margin) "," (:top margin) ")"))
                    (attr "width" inner-width))

          focus (.. graph
                    (append "g")
                    (attr "class" "focus")
                    (style "display" "none"))]
      (d3/clip-path-append svg id width height)

      (.. focus
          (append "rect")
          (attr "class" "guide"))

      (.. graph
          (append "g")
          (attr "class" "x axis grid")
          (attr "transform" (str "translate(0," inner-height ")"))
          (call x-axis))

      (.. svg
          (append "g")
          (attr "class" "brush"))

      (d3/update-on-resize this id)
      (om/update-state! this assoc :svg svg :js-data js-data :x-scale x-scale :y-scale y-scale :stack stack :area area :x-axis x-axis :y-axis y-axis :graph graph :focus focus :color-scale color-scale)))

  (update [this]
    (let [{:keys [svg graph focus x-scale y-scale x-axis y-axis js-data margin stack color-scale]} (om/get-state this)
          {:keys [id]} (om/props this)
          {inner-width :width
           inner-height :height} (d3/svg-dimensions svg {:margin margin})

          layers (stack js-data)
          values (.. js/d3 (merge (.map layers (fn [d] (.-values d)))))]

      ; When resize is in progress and sidebar pops in, inner size can be fucked up here.
      ; So just don't do anything in that case, an update will be triggered when sidebar transition is finished anyway.
      (when-not (or (js/isNaN inner-height) (js/isNaN inner-width))
        (if (empty? values)
          (do
            (d3/no-data-insert svg)
            (.. x-scale
                (range #js [0 inner-width] 0.1)
                (domain #js [(t/minus (t/today) (t/days 30)) (c/to-long (t/now))]))

            (.. y-scale
                (range #js [inner-height 0])
                (domain #js [0 1])))
          (do
            (d3/no-data-remove svg)
            (.. x-scale
                (range #js [0 inner-width])
                (domain (.. js/d3
                            (extent values (fn [d] (.-name d))))))
            (.. y-scale
                (range #js [inner-height 0])
                (domain #js [0 (.. js/d3
                                   (max values
                                        (fn [d]
                                          (+ (.-y0 d) (.-y d)))))]))))
        (.update-areas this layers)

        (let [brush (.. js/d3
                        -svg
                        brush
                        (x x-scale))
              brushend (fn []
                         (.domain x-scale (.extent brush))
                         (.update-axis this inner-width inner-height)
                         (.update-areas this layers)
                         (.. svg
                             (select ".brush")
                             (call (.clear brush))))]

          (.. brush
              (x x-scale)
              (on "brushend" brushend))
          (.. svg
              (selectAll ".brush")
              (call brush)
              (selectAll "rect")
              (attr "y" 0)
              (attr "height" inner-height)))
        (.update-axis this inner-width inner-height)

        (.. focus
            (select ".guide")
            (attr "height" inner-height))
        (.. svg
            (on "mousemove" (fn []
                              (this-as jthis
                                (d3/mouse-over
                                  (.. js/d3 (mouse jthis))
                                  x-scale
                                  js-data
                                  (fn [x-position values]
                                    (let [point (.. focus
                                                    (selectAll "circle")
                                                    (data values))
                                          guide (.. focus
                                                    (select ".guide"))
                                          tooltip (d3/tooltip-select id)

                                          time-format (.. js/d3
                                                          -time
                                                          (format "%b %d %Y"))]
                                      (d3/tooltip-add-data tooltip (time-format (js/Date. x-position)) values color-scale)

                                      (.. tooltip
                                          (style "left" (str (+ 30 (.. js/d3 -event -pageX)) "px"))
                                          (style "top" (str (.. js/d3 -event -pageY) "px")))

                                      (.. point
                                          enter
                                          (append "circle")
                                          (attr "class" "point")
                                          (attr "r" 3.5))

                                      (.. point
                                          (attr "transform" (fn [d]
                                                              (str "translate(" (x-scale (.-name d)) "," (y-scale (+ (.-y0 d) (.-y d))) ")")))
                                          (style "fill" (fn [_ i]
                                                          (color-scale i)))
                                          (style "stroke" (fn [_ i]
                                                            (color-scale i))))
                                      (.. guide
                                          (attr "transform" (fn [d]
                                                              (str "translate(" (x-scale x-position) ",0)"))))))))))
            (on "mouseover" (fn []
                              (d3/tooltip-remove-all)
                              (d3/tooltip-build id)
                              (.. focus (style "display" nil))))
            (on "mouseout" (fn []
                             (d3/tooltip-remove id)
                             (.. focus (style "display" "none"))))))))

  (update-areas [this layers]
    (let [{:keys [graph color-scale area]} (om/get-state this)
          {:keys [id]} (om/props this)
          graph-area (.. graph
                         (selectAll ".area")
                         (data layers))]
      (.. graph-area
          enter
          (append "path")
          (attr "class" "area")
          (style "clip-path" (d3/clip-path-url id))
          (style "fill" (fn [_ i]
                          (color-scale i)))
          (style "stroke" (fn [_ i]
                            (color-scale i))))

      (.. graph-area
          transition
          (duration 250)
          (attr "d" (fn [d]
                      (area (.-values d)))))

      (.. graph-area
          exit
          remove)))

  (update-axis [this width height]
    (let [{:keys [x-axis y-axis graph]} (om/get-state this)]
      (.. y-axis
          (ticks (max (/ height 50) 2))
          (tickSize (* -1 width) 0 0))
      (.. x-axis
          (ticks (max (/ width 100) 2))
          (tickSize (* -1 height) 0 0))
      (.. graph
          (selectAll ".x.axis")
          (attr "transform" (str "translate(0, " height ")"))
          transition
          (duration 250)
          (call x-axis))

      (.. graph
          (selectAll ".y.axis")
          transition
          (duration 250)
          (call y-axis))))

  (initLocalState [_]
    {:margin {:top 0 :bottom 20 :left 0 :right 0}})
  (componentDidMount [this]
    (d3/create-chart this))

  (componentDidUpdate [this _ _]
    (d3/update-chart this))

  (componentWillReceiveProps [this next-props]
    (d3/update-chart-data this (:data next-props)))

  (render [this]
    (let [{:keys [id]} (om/props this)]
      (html
        [:div
         (opts {:id    (str "area-chart-" id)
                :style {:height "100%"
                        :width  "100%"}})]))))

(def ->AreaChart (om/factory AreaChart))