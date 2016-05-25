(ns eponai.web.ui.d3.bar-chart
  (:require
    [cljsjs.d3]
    [eponai.client.ui :refer-macros [opts]]
    [eponai.web.ui.d3 :as d3]
    [goog.string :as gstring]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug error]]))

(defui BarChart
  Object
  (make-axis [_ width height]
    (let [y-scale (.. js/d3 -scale ordinal
                      (rangeRoundBands #js [height 0] 0.1))

          x-scale (.. js/d3 -scale linear
                      (range #js [0 width])
                      (nice))]

      {:x-axis (.. js/d3 -svg axis
                   (scale x-scale)
                   (orient "bottom")
                   ;(ticks (max (/ width 50) 2))
                   (tickFormat (.. js/d3
                                   (format ",.2f"))))

       :y-axis (.. js/d3 -svg axis
                   (scale y-scale)
                   (orient "right")
                   (tickSize (* -1 width) 0 0)
                   )
       :x-scale x-scale
       :y-scale y-scale}))

  (create [this]
    (let [{:keys [id width height data]} (om/props this)
          svg (d3/build-svg (om/react-ref this (str "bar-chart-" id)) width height)

          js-data (clj->js data)

          {:keys [margin]} (om/get-state this)
          {inner-width :width
           inner-height :height} (d3/svg-dimensions svg {:margin margin})

          {:keys [x-axis y-axis x-scale y-scale]} (.make-axis this inner-width inner-height)
          graph (.. svg
                    (append "g")
                    (attr "class" "bar-chart")
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
      (d3/focus-append svg {:margin margin})
      (d3/update-on-resize this id)

      (om/update-state! this assoc :svg svg :js-data js-data :x-scale x-scale :y-scale y-scale :x-axis x-axis :y-axis y-axis :graph graph)))

  (update [this]
    (let [{:keys [svg x-scale y-scale js-data margin graph]} (om/get-state this)
          {:keys [data id]} (om/props this)
          {inner-width :width
           inner-height :height} (d3/svg-dimensions svg {:margin margin})

          values (.. js/d3 (merge (.map js-data (fn [d] (.-values d)))))

          color-scale (.. js/d3 -scale category20
                          (domain (.map values (fn [d] (.-name d)))))]

      ; When resize is in progress and sidebar pops in, inner size can be fucked up here.
      ; So just don't do anything in that case, an update will be triggered when sidebar transition is finished anyway.
      (when-not (or (js/isNaN inner-height) (js/isNaN inner-width))
        (.update-scales this inner-width inner-height values)
        (.update-axis this inner-width inner-height)

        (let [bars (.. graph
                       (selectAll "rect.bar")
                       (data values))
              value-texts (.. graph
                              (selectAll "text.bar.value")
                              (data values))
              name-texts (.. graph
                             (selectAll "text.bar.name")
                             (data values))]
          (.. bars
              enter
              (append "rect")
              (attr "class" "bar")
              (attr "width" 0)
              (on "mouseover" (fn [d]
                                (d3/tooltip-remove-all)
                                (d3/tooltip-build id)
                                (d3/tooltip-add-value id d color-scale)
                                (d3/focus-show svg)))
              (on "mousemove" (fn []
                                (d3/tooltip-set-pos id
                                                    (+ 10 (.. js/d3 -event -pageX))
                                                    (+ 10 (.. js/d3 -event -pageY)))))
              (on "mouseout" (fn []
                               (d3/tooltip-remove id)
                               (d3/focus-hide svg))))

          (.. bars
              (style "fill" (fn [d]
                              (color-scale (.-name d))))
              transition
              (duration 250)
              (attr "height" (.. y-scale rangeBand))
              (attr "y" (fn [d] (y-scale (.-name d))))
              (attr "width" (fn [d] (x-scale (.-value d)))))

          (.. bars
              exit
              remove)

          (.. value-texts
              enter
              (append "text")
              (attr "class" "bar value")
              (attr "text-anchor" "end"))

          (.. value-texts
              transition
              (duration 250)
              (attr "y" (fn [d] (+ (y-scale (.-name d)) (/ (.. y-scale rangeBand) 2))))
              (attr "x" inner-width)
              (text (fn [d] (gstring/format "%.2f" (.-value d)))))

          (.. value-texts
              exit
              remove)

          (.. name-texts
              enter
              (append "text")
              (attr "class" "bar value")
              (attr "text-anchor" "start"))

          (.. name-texts
              transition
              (duration 250)
              (attr "y" (fn [d] (+ (y-scale (.-name d)) (/ (.. y-scale rangeBand) 2))))
              (attr "x" 0)
              (text (fn [d] (.-name d))))

          (.. name-texts
              exit
              remove)))))

  (update-axis [this width height]
    (let [{:keys [y-axis x-axis svg]} (om/get-state this)]
      (.. y-axis
          ;(ticks (max (/ inner-height 50) 2))
          (tickSize (* -1 width) 0 0))
      (.. x-axis
          (tickSize (* -1 height) 0 0))

      (.. svg
          (selectAll ".x.axis")
          (attr "transform" (str "translate(0, " height ")"))
          (attr "class" "x axis grid")
          transition
          (duration 250)
          (call x-axis))

      (.. svg
          (selectAll ".y.axis")
          transition
          (duration 250)
          (call y-axis))))

  (update-scales [this width height values]
    (let [{:keys [x-scale y-scale svg]} (om/get-state this)]
      (if (empty? values)
        (do
          (d3/no-data-insert svg)
          (.. x-scale
              (range #js [0 width])
              (domain #js [0 1]))
          (.. y-scale
              (rangeRoundBands #js [height 0] 0.1)
              (domain #js [])))
        (do
          (d3/no-data-remove svg)
          (.. x-scale
              (range #js [0 width])
              (domain #js [0 (.. js/d3
                                 (max values (fn [d] (.-value d))))]))
          (.. y-scale
              (rangeRoundBands #js [height 0] 0.01)
              (domain (.map values (fn [d] (.-name d)))))))))

  (initLocalState [_]
    {:margin {:top 20 :bottom 20 :left 0 :right 0}})

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
         (opts {:ref (str "bar-chart-" id)
                :style {:height "100%"
                        :width "100%"}})]))))

(def ->BarChart (om/factory BarChart))