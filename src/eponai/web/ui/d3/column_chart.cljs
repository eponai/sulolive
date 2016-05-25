(ns eponai.web.ui.d3.column-chart
  (:require
    [cljsjs.d3]
    [eponai.client.ui :refer-macros [opts]]
    [eponai.web.ui.d3 :as d3]
    [goog.string :as gstring]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug error]]))

(defui ColumnChart
  Object
  (make-axis [_ width height]
    (let [x-scale (.. js/d3 -scale ordinal
                      (rangeRoundBands #js [0 width] 0.1))

          y-scale (.. js/d3 -scale linear
                      (range #js [height 0])
                      (nice))]
      {:x-axis (.. js/d3 -svg axis
                   (scale x-scale)
                   (orient "bottom")
                   (tickSize (* -1 height) 0 0))

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
          svg (d3/build-svg (om/react-ref this (str "column-chart-" id)) width height)

          js-data (clj->js data)

          {:keys [margin]} (om/get-state this)
          {inner-width :width
           inner-height :height} (d3/svg-dimensions svg {:margin margin})

          {:keys [x-axis y-axis x-scale y-scale]} (.make-axis this inner-width inner-height)
          graph (.. svg
                    (append "g")
                    (attr "class" "column-chart")
                    (attr "transform" (str "translate(" (:left margin) "," (:top margin) ")")))
          focus (.. svg
                    (append "g")
                    (attr "class" "focus")
                    (style "display" "none"))]
      (.. graph
          (append "g")
          (attr "class" (if (< 30 (.. x-scale rangeBand)) "x axis grid" "x axis grid hidden"))
          (attr "transform" (str "translate(0," inner-height ")")))
      (.. graph
          (append "g")
          (attr "class" "y axis grid")
          (attr "transform" (str "translate(0,0)")))
      (d3/update-on-resize this id)

      (om/update-state! this assoc :svg svg :js-data js-data :x-scale x-scale :y-scale y-scale :x-axis x-axis :y-axis y-axis :graph graph :focus focus)))

  (update [this]
    (let [{:keys [svg x-scale y-scale x-axis y-axis js-data margin graph focus]} (om/get-state this)
          {:keys [data id]} (om/props this)
          {inner-width :width
           inner-height :height} (d3/svg-dimensions svg {:margin margin})

          values (.. js/d3 (merge (.map js-data (fn [d] (.-values d)))))

          color-scale (.. js/d3 -scale category20
                          (domain (.map values (fn [d] (.-name d)))))]

      ; When resize is in progress and sidebar pops in, inner size can be fucked up here.
      ; So just don't do anything in that case, an update will be triggered when sidebar transition is finished anyway.
      (when-not (or (js/isNaN inner-height) (js/isNaN inner-width))
        (if (empty? values)
          (do
            (d3/no-data-insert svg)
            (.. x-scale
                (rangeRoundBands #js [0 inner-width] 0.1)
                (domain #js []))
            (.. y-scale
                (range #js [inner-height 0])
                (domain #js [0 1])))
          (do
            (d3/no-data-remove svg)
            (.. x-scale
                (rangeRoundBands #js [0 inner-width] 0.01)
                (domain (.map values (fn [d] (.-name d)))))
            (.. y-scale
                (range #js [inner-height 0])
                (domain #js [0 (.. js/d3
                                   (max values (fn [d] (.-value d))))]))))

        (.. y-axis
            (ticks (max (/ inner-height 50) 2))
            (tickSize (* -1 inner-width) 0 0))
        (.. x-axis
            (tickSize (* -1 inner-height) 0 0))

        (.. svg
            (selectAll ".x.axis")
            (attr "transform" (str "translate(0, " inner-height ")"))
            (attr "class" (if (< 30 (.. x-scale rangeBand)) "x axis grid" "x axis grid hidden"))
            transition
            (duration 250)
            (call x-axis))

        (.. svg
            (selectAll ".y.axis")
            transition
            (duration 250)
            (call y-axis))

        (mapv
          (fn [data-set]
            (let [data-values (.-values data-set)
                  bars (.. graph
                           (selectAll "rect.bar")
                           (data data-values))
                  texts (.. graph
                            (selectAll "text.bar")
                            (data data-values))]
              (.. bars
                  enter
                  (append "rect")
                  (attr "class" "bar")
                  (attr "transform" (fn [d] (str "translate(" (x-scale (.-name d)) ",0)")))
                  (attr "y" inner-height)
                  (attr "height" 0)
                  (on "mouseover" (fn [d]
                                    (d3/tooltip-remove-all)
                                    (let [tooltip (d3/tooltip-build id)]
                                      (d3/tooltip-add-value id d color-scale)
                                      (.. focus (style "display" nil)))))
                  (on "mousemove" (fn []
                                    (let [tooltip (d3/tooltip-select id)]
                                      (.. tooltip
                                          (style "left" (str (+ 10 (.. js/d3 -event -pageX)) "px"))
                                          (style "top" (str (+ 10 (.. js/d3 -event -pageY)) "px"))))))
                  (on "mouseout" (fn []
                                   (d3/tooltip-remove id)
                                   (.. focus (style "display" "none")))))

              (.. bars
                  (style "fill" (fn [d]
                                  (color-scale (.-name d))))
                  transition
                  (duration 250)
                  (attr "transform" (fn [d] (str "translate(" (x-scale (.-name d)) ",0)")))
                  (attr "width" (.. x-scale rangeBand))
                  (attr "y" (fn [d] (y-scale (.-value d))))
                  (attr "height" (fn [d] (- inner-height (y-scale (.-value d))))))

              (.. bars
                  exit
                  remove)

              (.. texts
                  enter
                  (append "text")
                  (attr "class" "bar")
                  (attr "text-anchor" "middle"))

              (.. texts
                  transition
                  (duration 250)
                  (attr "x" (fn [d] (+ (x-scale (.-name d)) (/ (.. x-scale rangeBand) 2))))
                  (attr "y" (fn [d] (y-scale (.-value d))))
                  (attr "dy" "-0.3em")
                  (text (fn [d] (gstring/format "%.2f" (.-value d)))))

              (.. texts
                  exit
                  remove)))
          js-data))))

  (initLocalState [_]
    {:margin {:top 20 :bottom 20 :left 20 :right 20}})

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
         (opts {:ref (str "column-chart-" id)
                :style {:height "100%"
                        :width "100%"}})]))))

(def ->ColumnChart (om/factory ColumnChart))