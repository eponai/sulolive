(ns eponai.web.ui.d3.burndown-chart
  (:require
    [cljsjs.d3]
    [eponai.client.ui :refer-macros [opts]]
    [eponai.web.ui.d3 :as d3]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug]]
    [cljs-time.core :as t]
    [cljs-time.coerce :as c]))

(defui BurndownChart
  Object
  (make-axis [_ width height]
    (let [x-scale (.. js/d3 -time scale
                      (range #js [0 width])
                      (nice (.. js/d3 -time -year)))

          y-scale (.. js/d3 -scale linear
                      (range #js [height 0])
                      (nice))]
      {:x-axis (.. js/d3 -svg axis
                   (scale x-scale)
                   (orient "bottom")
                   (ticks (max (/ width 150) 2))
                   (tickSize (* -1 height) 0 0)
                   (tickFormat #((.. js/d3
                                     -time
                                     (format "%b %d"))
                                 (js/Date. %))))

       :y-axis (.. js/d3 -svg axis
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
          svg (d3/build-svg (str "#burndown-chart-" id) width height)
          last-data (last data)
          chart-data [{:key    "user-input"
                       :values (or (:values last-data) [])}
                      {:key    "guideline"
                       :values (or (:guide last-data) [])}]

          js-data (clj->js chart-data)

          {:keys [margin]} (om/get-state this)
          {inner-width :width
           inner-height :height} (d3/svg-dimensions svg {:margin margin})

          {:keys [x-axis y-axis x-scale y-scale]} (.make-axis this inner-width inner-height)
          color-scale (.. js/d3 -scale category20)
          graph (.. svg
                    (append "g")
                    (attr "class" "line-chart")
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
      (om/update-state! this assoc
                        :svg svg :js-data js-data
                        :x-scale x-scale :y-scale y-scale :x-axis x-axis :y-axis y-axis :graph graph :color-scale color-scale)))

  (update [this]
    (let [{:keys [svg margin x-scale y-scale js-data]} (om/get-state this)
          {:keys [id]} (om/props this)
          {inner-width :width
           inner-height :height} (d3/svg-dimensions svg {:margin margin})]

      ; When resize is in progress and sidebar pops in, inner size can be fucked up here.
      ; So just don't do anything in that case, an update will be triggered when sidebar transition is finished anyway.
      (when-not (or (js/isNaN inner-height) (js/isNaN inner-width))
        (.update-scales this inner-width inner-height)
        (.update-lines this)
        (.update-axis this inner-width inner-height)

        (d3/focus-set-height svg inner-height)
        (.. svg
            (on "mousemove" (fn []
                              (this-as jthis
                                (d3/mouse-over
                                  (.. js/d3 (mouse jthis))
                                  x-scale
                                  js-data
                                  (fn [x-position values]
                                    (let [time-format (.. js/d3
                                                          -time
                                                          (format "%b %d %Y"))]
                                      (d3/tooltip-add-data id
                                                           (time-format (js/Date. x-position))
                                                           values (fn [d]
                                                                    (condp = (.-key d)
                                                                      "user-input"
                                                                      "orange"
                                                                      "guideline"
                                                                      "green")))
                                      (d3/tooltip-set-pos id
                                                          (+ 30 (.. js/d3 -event -pageX))
                                                          (.. js/d3 -event -pageY))

                                      (d3/focus-set-guide svg (x-scale x-position) 5)
                                      (d3/focus-set-data-points svg
                                                                values
                                                                {:x-fn     (fn [d] (x-scale (.-name d)))
                                                                 :y-fn     (fn [d] (y-scale (.-value d)))
                                                                 :color-fn (fn [d]
                                                                             (condp = (.-key d)
                                                                               "user-input"
                                                                               "orange"
                                                                               "guideline"
                                                                               "green"))})))))))
            (on "mouseover" (fn []
                              (d3/tooltip-remove-all)
                              (d3/tooltip-build id)
                              (d3/focus-show svg)))
            (on "mouseout" (fn []
                             (d3/tooltip-remove id)
                             (d3/focus-hide svg)))))))

  (update-scales [this width height]
    (let [{:keys [x-scale y-scale js-data svg]} (om/get-state this)
          values (.. js/d3 (merge (.map js-data (fn [d] (.-values d)))))]
      (if (empty? values)
        (do
          (d3/no-data-insert svg)
          (.. x-scale
              (range #js [0 width] 0.1)
              (domain #js [(t/minus (t/today) (t/days 30)) (c/to-long (t/now))]))
          (.. y-scale
              (range #js [height 0])
              (domain #js [0 1])))
        (do
          (d3/no-data-remove svg)
          (.. x-scale
              (range #js [0 width])
              (domain (.. js/d3
                          (extent values (fn [d] (.-name d))))))
          (.. y-scale
              (range #js [height 0])
              (domain #js [0 (.. js/d3
                                 (max values (fn [d] (.-value d))))]))))))

  (update-lines [this]
    (let [{:keys [graph js-data x-scale y-scale]} (om/get-state this)
          line (.. js/d3 -svg line
                   (x (fn [d] (x-scale (.-name d))))
                   (y (fn [d] (y-scale (.-value d)))))
          graph-area (.. graph
                         (selectAll ".line")
                         (data js-data))]
      (.. graph-area
          enter
          (append "path")
          (attr "class" "line")
          (style "stroke" (fn [d]
                            (condp = (.-key d)
                              "user-input"
                              "orange"
                              "guideline"
                              "green"))))

      (.. graph-area
          transition
          (duration 250)
          (attr "d" (fn [d] (line (.-values d)))))

      (.. graph-area
          exit
          remove)))

  (update-axis [this width height]
    (let [{:keys [y-axis x-axis svg]} (om/get-state this)]
      (.. y-axis
          (ticks (max (/ height 50) 2))
          (tickSize (* -1 width) 0 0))
      (.. x-axis
          (ticks (max (/ width 100) 2))
          (tickSize (* -1 height) 0 0))
      (.. svg
          (selectAll ".x.axis")
          (attr "transform" (str "translate(0, " height ")"))
          transition
          (duration 250)
          (call x-axis))
      (.. svg
          (selectAll ".y.axis")
          (attr "transform" "translate(0, 0)")
          transition
          (duration 250)
          (call y-axis))))

  (initLocalState [_]
    {:margin {:top 10 :bottom 20 :left 20 :right 20}})
  (componentDidMount [this]
    (d3/create-chart this))

  (componentDidUpdate [this _ _]
    (d3/update-chart this))

  (componentWillReceiveProps [this next-props]
    (let [last-data (last (:data next-props))
          chart-data [{:key    "user-input"
                       :values (:values last-data)}
                      {:key    "guideline"
                       :values (:guide last-data)}]]
      (d3/update-chart-data this chart-data)))

  (render [this]
    (let [{:keys [id]} (om/props this)]
      (html
        [:div
         (opts {:id (str "burndown-chart-" id)
                :style {:height "100%"
                        :width "100%"}})]))))

(def ->BurndownChart (om/factory BurndownChart))