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
  (make-axis [_ width height domain]
    (let [x-scale (.. js/d3 -time scale
                      (range #js [0 width])
                      (nice (.. js/d3 -time -year))
                      (domain (.. js/d3
                                  (extent domain (fn [d] (.-name d))))))

          y-scale (.. js/d3 -scale linear
                      (range #js [height 0])
                      (nice)
                      (domain #js [0 (.. js/d3
                                         (max domain (fn [d] (.-value d))))]))]
      {:x-axis (.. js/d3 -svg axis
                   (scale x-scale)
                   (orient "bottom")
                   (ticks (max (/ width 150) 2))
                   (tickFormat #((.. js/d3
                                     -time
                                     (format "%b %Y"))
                                 (js/Date. %))))

       :y-axis (.. js/d3 -svg axis
                   (scale y-scale)
                   (orient "left")
                   (ticks (max (/ height 50) 2))
                   (tickFormat (.. js/d3
                                   (format ",.2f"))))
       :x-scale x-scale
       :y-scale y-scale}))

  (create [this]
    (let [{:keys [id width height data]} (om/props this)
          svg (d3/build-svg (str "#area-chart-" id) width height)

          js-domain (clj->js (flatten (map :values data)))
          js-data-values (clj->js (map :values data))

          {:keys [margin]} (om/get-state this)
          {inner-width :width
           inner-height :height} (d3/svg-dimensions svg {:margin margin})

          {:keys [x-axis y-axis x-scale y-scale]} (.make-axis this inner-width inner-height js-domain)
          color-scale (.. js/d3 -scale category20)

          line (.. js/d3 -svg area
                   (x (fn [d] (x-scale (.-name d))))
                   (y0 inner-height)
                   (y1 (fn [d] (y-scale (.-value d)))))

          graph (.. svg
                    (append "g")
                    (attr "transform" (str "translate(" (:left margin) "," (:top margin) ")")))]

      (.. graph
          (append "g")
          (attr "class" "x axis grid")
          (attr "transform" (str "translate(0," inner-height ")"))
          (call x-axis))

      (.. graph
          (append "g")
          (attr "class" "y axis grid")
          (attr "transform" (str "translate(0,0)"))
          (call y-axis))

      (mapv
        (fn [i data-set]
          (let [start-data (.map data-set
                                 (fn [d] #js {:name  (.-name d)
                                              :value 0}))]
            (.. graph
                (append "path")
                (attr "class" "area")
                (attr "id" (str "area-" i))
                (style "fill" (fn [] (color-scale i)))
                transition
                (duration 500)
                (attrTween "d" (fn []
                                 (let [interpolator (.. js/d3
                                                        (interpolateArray start-data data-set))]
                                   (fn [t]
                                     (line (interpolator t)))))))))
        (range)
        js-data-values)

      (d3/update-on-resize this id)
      (om/update-state! this assoc :svg svg :js-domain js-domain :js-data-values js-data-values :x-scale x-scale :y-scale y-scale :line line :x-axis x-axis :y-axis y-axis :graph graph :color-scale color-scale)))

  (update [this]
    (let [{:keys [svg x-scale y-scale x-axis y-axis line js-data-values js-domain margin graph color-scale]} (om/get-state this)
          {inner-width :width
           inner-height :height} (d3/svg-dimensions svg {:margin margin})
          draw-graph (fn [i data-set]
                       (let [points (.. graph
                                        (selectAll "circle")
                                        (data data-set))]
                         (.. graph
                             (selectAll (str "#area-" i))
                             transition
                             (duration 250)
                             (attr "d" (line data-set)))
                         (.. points
                             enter
                             (append "circle")
                             (attr "class" "data-point")
                             (attr "r" "3.0")
                             (style "fill" (fn [] (color-scale i)))
                             (style "stroke" (fn [] (color-scale i)))
                             (attr "cx" (fn [d] (x-scale (.-name d))))
                             (attr "cy" inner-height))
                         (.. points
                             transition
                             (duration 250)
                             (attr "cx" (fn [d] (x-scale (.-name d))))
                             (attr "cy" (fn [d] (y-scale (.-value d)))))
                         (.. points
                             exit
                             remove)))]

      ; When resize is in progress and sidebar pops in, inner size can be fucked up here.
      ; So just don't do anything in that case, an update will be triggered when sidebar transition is finished anyway.
      (when-not (or (js/isNaN inner-height) (js/isNaN inner-width))
        (if (empty? js-domain)
          (do
            (d3/no-data-insert svg)
            (.. x-scale
                (range #js [0 inner-width] 0.1)
                (domain #js [(t/minus (t/today) (t/days 30)) (c/to-long (t/now))]))
            (.. y-scale
                (range #js [inner-height 0])
                (domain #js [0 1]))
            (mapv
              draw-graph
              (range)
              (clj->js [(mapv (fn [days]
                                {:name  (c/to-long (t/minus (t/today) (t/days days)))
                                 :value 0})
                              (take 30 (range)))])))
          (do
            (d3/no-data-remove svg)
            (.. x-scale
                (range #js [0 inner-width])
                (domain (.. js/d3
                            (extent js-domain (fn [d] (.-name d))))))
            (.. y-scale
                (range #js [inner-height 0])
                (domain #js [0 (.. js/d3
                                   (max js-domain (fn [d] (.-value d))))]))
            (mapv
              draw-graph
              (range)
              js-data-values)))
        (.. line
            (y0 inner-height))
        (.. y-axis
            (ticks (max (/ inner-height 50) 2))
            (tickSize (* -1 inner-width) 0 0))
        (.. x-axis
            (ticks (max (/ inner-width 100) 2))
            (tickSize (* -1 inner-height) 0 0))
        (.. svg
            (selectAll ".x.axis")
            (attr "transform" (str "translate(0, " inner-height ")"))
            transition
            (duration 250)
            (call x-axis))

        (.. svg
            (selectAll ".y.axis")
            transition
            (duration 250)
            (call y-axis)))))

  (initLocalState [_]
    {:margin {:top 10 :bottom 30 :left 40 :right 10}})
  (componentDidMount [this]
    (d3/create-chart this))

  (componentDidUpdate [this _ _]
    (d3/update-chart this))

  (componentWillReceiveProps [this next-props]
    (d3/update-chart-data this next-props))

  (render [this]
    (let [{:keys [id]} (om/props this)]
      (html
        [:div
         (opts {:id (str "area-chart-" id)
                :style {:height "100%"
                        :width "100%"}})]))))

(def ->AreaChart (om/factory AreaChart))