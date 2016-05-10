(ns eponai.web.ui.d3.area-chart
  (:require
    [cljsjs.d3]
    [eponai.client.ui :refer-macros [opts]]
    [eponai.web.ui.d3 :as d3]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug]]
    [cljs-time.coerce :as c]
    [cljs-time.core :as t]))

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

          line (.. js/d3 -svg line
                   (x #(x-scale (.-name %)))
                   (y #(y-scale (.-value %))))

          graph (.. svg
                    (append "g")
                    (attr "transform" (str "translate(" (:left margin) "," (:top margin) ")"))
                    (attr "width" inner-width))
          focus (.. svg
                    (append "g")
                    (attr "class" "focus")
                    (style "display" "none")) ]


      (.. focus
          (append "text")
          (attr "x" 9)
          (attr "dy" ".35em"))
      (.. focus
          (append "rect")
          (attr "class" "guide")
          (attr "height" inner-height)
          ;(attr "width" "1px")
          )

      (.. graph
          (append "g")
          (attr "class" "x axis grid")
          (attr "transform" (str "translate(0," inner-height ")"))
          (call x-axis))

      (.. svg
          (on "mousemove" (fn []
                            (this-as jthis
                              (let [mouseX (first (.. js/d3 (mouse jthis)))
                                    sample-data (.-values (first js-data))
                                    bisect-date (.. js/d3
                                                 (bisector (fn [d]
                                                             (.-name d)))
                                                 -left)
                                    x0 (.invert x-scale mouseX)
                                    i (bisect-date sample-data x0 1)
                                    d0 (get sample-data (dec i))
                                    d1 (get sample-data i)
                                    x-value (if (> (- x0 (.-name d0)) (- (.-name d1) x0))
                                              i (dec i))
                                    found-data (.map js-data #(get (.-values %) x-value))

                                    point (.. focus
                                              (selectAll "circle")
                                              (data found-data))
                                    guide (.. focus
                                              (select ".guide"))]
                                (.. point
                                    enter
                                    (append "circle")
                                    (attr "class" "point")
                                    (attr "r" 3.5))

                                (.. point
                                    (attr "transform" (fn [d]
                                                        (str "translate(" (x-scale (.-name d)) "," (y-scale (+ (.-y0 d) (.-y d))) ")")))
                                    (style "stroke" (fn [_ i]
                                                    (color-scale i))))
                                (.. guide
                                    (attr "transform" (str "translate(" (x-scale (.-name (first found-data))) ",0)")))))))
          (on "mouseover" (fn []
                            (.. focus (style "display" nil))))
          (on "mouseout" (fn []
                           (.. focus (style "display" "none")))))


      (d3/update-on-resize this id)
      (om/update-state! this assoc :svg svg :js-data js-data :x-scale x-scale :y-scale y-scale :stack stack :area area :x-axis x-axis :y-axis y-axis :graph graph :color-scale color-scale :line line)))

  (update [this]
    (let [{:keys [svg graph x-scale y-scale x-axis y-axis js-data margin stack vertical]} (om/get-state this)
          {inner-width :width
           inner-height :height} (d3/svg-dimensions svg {:margin margin})
          layers (stack js-data)
          domain (.. js/d3 (merge (.map layers (fn [d] (.-values d)))))]

      ; When resize is in progress and sidebar pops in, inner size can be fucked up here.
      ; So just don't do anything in that case, an update will be triggered when sidebar transition is finished anyway.
      (when-not (or (js/isNaN inner-height) (js/isNaN inner-width))
        (if (empty? domain)
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
                            (extent domain (fn [d] (.-name d))))))
            (.. y-scale
                (range #js [inner-height 0])
                (domain #js [0 (.. js/d3
                                   (max domain
                                        (fn [d]
                                          (+ (.-y0 d) (.-y d)))))]))))
        (.update-areas this layers)

        (.. y-axis
            (ticks (max (/ inner-height 50) 2))
            (tickSize (* -1 inner-width) 0 0))
        (.. x-axis
            (ticks (max (/ inner-width 100) 2))
            (tickSize (* -1 inner-height) 0 0))
        (.. graph
            (selectAll ".x.axis")
            (attr "transform" (str "translate(0, " inner-height ")"))
            transition
            (duration 250)
            (call x-axis))

        (.. graph
            (selectAll ".y.axis")
            transition
            (duration 250)
            (call y-axis)))))

  (update-areas [this layers]
    (let [{:keys [graph color-scale area line]} (om/get-state this)
          graph-area (.. graph
                         (selectAll ".area")
                         (data layers))]
      (.. graph-area
          enter
          (append "path")
          (attr "class" "area")
          (style "fill" (fn [d i]
                          (let [data-id (.-id d)]
                            (if (= data-id "mean")
                              "none"
                              (color-scale i)))))
          (style "stroke" (fn [d i]
                            (let [data-id (.-id d)]
                              (if (= data-id "mean")
                                "black"
                                (color-scale i))))))

      (.. graph-area
          transition
          (duration 250)
          (attr "d" (fn [d]
                      (let [data-id (.-id d)]
                        (if (= data-id "mean")
                          (line (.-values d))
                          (area (.-values d)))))))

      (.. graph-area
          exit
          remove)))

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