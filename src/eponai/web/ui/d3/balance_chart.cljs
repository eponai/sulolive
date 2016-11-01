(ns eponai.web.ui.d3.balance-chart
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.web.ui.d3 :as d3]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug]]
    [cljs-time.core :as t]
    [cljs-time.coerce :as c]))

(defn create-style-defs [svg]
  (let [{inner-height :height} (d3/svg-dimensions svg)
        defs (.. svg
                 (append "defs"))
        gradient (.. defs
                     (append "linearGradient")
                     (attr "class" "blue-gradient")
                     (attr "id" "balance-chart-gradient")
                     (attr "x1" "0%")
                     (attr "y1" "0%")
                     (attr "y2" "100%")
                     (attr "x2" "0%"))
        inset (.. defs
                  (append "filter")
                  (attr "id" "inset-shadow"))

        drop-shadow (.. defs
                        (append "filter")
                        (attr "height" (str inner-height "px"))
                        (attr "id" "drop-shadow"))]

    (.. gradient
        (append "stop")
        (attr "class" "stop-top")
        (attr "offset" "0%"))
    (.. gradient
        (append "stop")
        (attr "class" "stop-bottom")
        (attr "offset" "100%"))

    (.. inset
        (append "feGaussianBlur")
        (attr "in" "SourceAlpha")
        (attr "stdDeviation" "0,3")
        (attr "result" "inset-blur"))
    (.. inset
        (append "feOffset")
        (attr "in" "inset-blur")
        (attr "dx" 0)
        (attr "dy" 3)
        (attr "result" "inset-offset-blur"))

    (.. inset
        (append "feComposite")
        (attr "operator" "out")
        (attr "in" "SourceGraphic")
        (attr "in2" "inset-offset-blur")
        (attr "result" "inset-inverse"))
    (.. inset
        (append "feFlood")
        (attr "flood-color" "#57BFBF")
        (attr "flood-opacity" 1)
        (attr "result" "inset-color"))
    (.. inset
        (append "feComposite")
        (attr "operator" "in")
        (attr "in" "inset-color")
        (attr "in2" "inset-inverse")
        (attr "result" "inset-shadow"))
    (.. inset
        (append "feComposite")
        (attr "operator" "over")
        (attr "in" "inset-shadow")
        (attr "in2" "SourceGraphic"))

    (.. drop-shadow
        (append "feGaussianBlur")
        (attr "in" "SourceAlpha")
        (attr "stdDeviation" "0,1")
        (attr "result" "line-blur"))
    (.. drop-shadow
        (append "feOffset")
        (attr "in" "line-blur")
        (attr "dx" 0)
        (attr "dy" 2)
        (attr "result" "line-offset-blur"))
    (.. drop-shadow
        (append "feFlood")
        (attr "flood-color" "#C77F2C")
        (attr "flood-opacity" 1)
        (attr "result" "line-color"))
    (.. drop-shadow
        (append "feComposite")
        (attr "operator" "in")
        (attr "in" "line-color")
        (attr "in2" "line-offset-blur")
        (attr "result" "drop-shadow"))
    (.. drop-shadow
        (append "feBlend")
        (attr "in" "SourceGraphic")
        (attr "in2" "drop-shadow")
        (attr "mode" "normal"))
    ))

(defui BalanceChart
  Object
  (make-axis [_]
    (let [x-scale (.. js/d3 scaleTime)
          y-scale (.. js/d3 scaleLinear nice)]

      {:x-axis  (.. js/d3 (axisBottom x-scale))
       :y-axis  (.. js/d3 (axisLeft y-scale))
       :x-scale x-scale
       :y-scale y-scale}))

  (create [this]
    (let [{:keys [id]} (om/props this)
          svg (d3/build-svg (om/react-ref this (str "balance-" id)))

          {:keys [margin]} (om/get-state this)
          {inner-width :width
           inner-height :height} (d3/svg-dimensions svg {:margin margin})

          {:keys [x-axis y-axis x-scale y-scale]} (.make-axis this)

          area (.. js/d3 area
                   (curve (.. js/d3 -curveMonotoneX) )
                   (x  #(x-scale (:date %)))
                   (y0 inner-height)
                   (y1 #(if (= 0 %2)
                         (y-scale (+ 0.01 (:balance %)))
                         (y-scale (:balance %)))))
          line (.. js/d3 line
                   (curve (.. js/d3 -curveMonotoneX) )
                   (x #(x-scale (:date %)))
                   (y #(if (= 0 %2)
                        (y-scale (+ 0.01 (:spent %)))
                        (y-scale (:spent %)))))
          graph (.. svg
                    (append "g")
                    (attr "class" "chart")
                    (attr "transform" (str "translate(" (:left margin) "," (:top margin) ")"))
                    (attr "width" inner-width))]
      (d3/focus-append svg {:margin margin} )
      (.. svg
          (append "rect")
          (attr "class" "focus-overlay")
          (attr "transform" (str "translate(" (or (:left margin) 0) "," (or (:top margin) 0) ")"))
          (attr "width" inner-width)
          (attr "height" inner-height))

      (create-style-defs svg)
      (.. graph
          (append "g")
          (attr "class" "x axis grid")
          (attr "transform" (str "translate(0," inner-height ")")))

      (.. graph
          (append "g")
          (attr "class" "y axis grid")
          (attr "transform" (str "translate(-1,0)")))
      (d3/clip-path-append svg id)


      (d3/update-on-resize this id)
      (om/update-state! this assoc :svg svg :x-scale x-scale :y-scale y-scale :area area :line line :x-axis x-axis :y-axis y-axis :graph graph)))

  (value-range [_ values]
    (let [[low high] (cond (empty? values)
                           [0 10]
                           (= 1 (count values))
                           (let [value (first values)
                                 max-val (max (:balance value) (:spent value))]
                             (if (neg? max-val)
                               [max-val 0]
                               [0 max-val]))

                           (< 1 (count values))
                           [(apply min (map #(min (:balance %) (:spent %)) values))
                            (apply max (map #(max (:balance %) (:spent %)) values))])]
      (if (= low high)
        [low (+ high 10)]
        [low high])))

  (update [this]
    (let [{:keys [svg x-scale y-scale margin graph area line balance-visible? spent-visible?]} (om/get-state this)
          {:keys [report id]} (om/props this)
          {:keys [data-points x-domain y-domain]} report
          ;_ (debug "Balance chart values: " data-points)
          js-values (into-array data-points)
          ;[min-y max-y] (.value-range this data-points)
          ;_ (debug "min: " min-y " max " max-y)
          ;_ (debug "Balance Report: " report)
          _ (debug "js-values: " js-values)
          {inner-width :width
           inner-height :height} (d3/svg-dimensions svg {:margin margin})
          graph-area (.. graph
                         (selectAll ".area")
                         (data #js [js-values]))
          spent-line (.. graph
                         (selectAll ".line")
                         (data #js [js-values]))]
      (.. x-scale
          (range #js [0 inner-width])
          (domain (clj->js x-domain)))
      (.. y-scale
          (range #js [inner-height 0])
          (domain (clj->js y-domain)))

      (.. graph-area
          enter
          (append "path")
          (attr "class" "area")
          (attr "d" area))

      (.. graph-area
          transition
          (duration 250)
          (style "opacity" (if balance-visible? 1 0))
          (attr "d" area))

      (.. graph-area
          exit
          remove)

      (.. spent-line
          enter
          (append "path")
          (attr "class" "line")
          (attr "d" line))

      (.. spent-line
          transition
          (duration 100)
          (style "opacity" (if spent-visible? 1 0)))
      (.. spent-line
          transition
          (duration 250)
          (delay 100)
          (attr "d" line))

      (.. spent-line
          exit
          remove)
      (d3/clip-path-set-dimensions id inner-width inner-height)
      (.update-axis this inner-width inner-height)
      (.update-interactive-focus this inner-height)))

  (update-interactive-focus [this inner-height]
    (let [{:keys [svg x-scale y-scale margin spent-visible? balance-visible?]} (om/get-state this)
          {:keys [id report]} (om/props this)
          {:keys [data-points]} report
          js-values (into-array data-points)
          focus-overlay (.. svg (select ".focus-overlay"))
          focus (.. svg (select ".focus"))]

      (d3/focus-set-height svg inner-height)
      (.. focus-overlay
          (on "mousemove" (fn []
                            (this-as jthis
                              (d3/mouse-over
                                (.. js/d3 (mouse jthis))
                                x-scale
                                js-values
                                (fn [{:keys [date spent balance]}]
                                  (let [focus-data #js [{:class "spent" :value spent :visible spent-visible?}
                                                        {:class "balance" :value balance :visible balance-visible?}]
                                        point (.. focus
                                                  (selectAll "circle")
                                                  (data focus-data))

                                        time-format (d3/time-formatter "%A %b %d")]
                                    (d3/tooltip-add-data id (time-format (js/Date. date)) focus-data)
                                    (d3/tooltip-set-pos id (+ 10 (.. js/d3 -event -pageX))
                                                        (+ 10 (.. js/d3 -event -pageY)))

                                    (d3/focus-set-guide svg (x-scale date) 5)

                                    (.. point
                                        enter
                                        (append "circle")
                                        (attr "class" (fn [d] (str "point " (:class d))))
                                        (attr "r" "0.5rem"))

                                    (.. point
                                        (attr "transform" (fn [d] (str "translate(" (x-scale date) "," (y-scale (:value d)) ")")))
                                        (style "display" (fn [d]
                                                           (if (:visible d)
                                                             nil "none"))))))
                                margin))))
          (on "mouseover" (fn []
                            (d3/tooltip-remove-all)
                            (d3/tooltip-build id)
                            (d3/focus-show svg)))
          (on "mouseout" (fn []
                           (d3/tooltip-remove id)
                           (d3/focus-hide svg))))))

  (update-axis [this width height]
    (let [{:keys [x-axis y-axis graph]} (om/get-state this)]
      (.. y-axis
          (ticks (max (/ height 100) 2)))
      (.. x-axis
          (ticks (max (/ width 100) 2)))
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

  (componentDidMount [this]
    (.create this))

  (componentDidUpdate [this _ _]
    (.update this))

  (componentWillReceiveProps [this new-props]
    (if (not= new-props (om/props this))
      (.update this)))

  (componentWillUnmount [this]
    (d3/unmount-chart this))

  (initLocalState [_]
    {:margin {:top 20 :left 100 :bottom 30 :right 20}
     :balance-visible? true
     :spent-visible? true})
  (render [this]
    (let [{:keys [id]} (om/props this)
          {:keys [balance-visible? spent-visible?]} (om/get-state this)]
      (html
        [:div.graph-container
         [:div.clearfix
          [:ul.menu.legend
           [:li
            [:a
             {:on-click #(om/update-state! this update :balance-visible? not)
              :class    (when balance-visible? "visible")}
             [:i.circle#legend-balance]
             [:span "Balance"]]]
           [:li
            [:a
             {:on-click #(om/update-state! this update :spent-visible? not)
              :class    (when spent-visible? "visible")}
             [:i.circle#legend-spent]
             [:span "Spent by day"]]]]]
         [:div.graph
          (opts {:ref (str "balance-" id)
                 :id  id})]]))))

(def ->BalanceChart (om/factory BalanceChart))
