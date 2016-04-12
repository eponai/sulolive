(ns eponai.web.ui.d3.progress-bar
  (:require
    [cljsjs.d3]
    [eponai.client.ui :refer-macros [opts]]
    [eponai.web.ui.d3 :as d3]
    [goog.string :as gstring]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug]]))

(defui ProgressBar
  Object
  (create [this]
    (let [{:keys [id width height data]} (om/props this)
          svg (d3/build-svg (str "#progress-bar-" id) width height)
          {:keys [margin]} (om/get-state this)
          {inner-height :height
           inner-width :width} (d3/svg-dimensions svg {:margin margin})
          js-data (clj->js (assoc data :endAngle 0))
          _ (debug "Progress bar goal got data: " data)
          path-width (/ (min inner-width inner-height) 7)
          ;js-first-data (last js-data)
          graph (.. svg
                    (append "g")
                    (attr "transform" (str "translate(" (/ inner-width 2) "," (/ inner-height 2) ")")))
          meter (.. graph
                    (append "g")
                    (attr "class" "circle-progress"))
          arc (.. js/d3
                  -svg
                  arc
                  (innerRadius (- (/ (min inner-width inner-height) 2) path-width))
                  (outerRadius (/ (min inner-width inner-height) 2))
                  (startAngle 0))
          text-element (.. meter
                           (selectAll ".txt")
                           (data #js [js-data]))]
      (.. meter
          (append "path")
          (attr "class" "background")
          (datum #js {:endAngle (* 2 js/Math.PI)})
          (style "fill" "#ddd")
          (attr "d" arc))

      (.. meter
          (append "path")
          (attr "class" "foreground")
          (datum js-data)
          (style "fill" "orange")
          (attr "d" arc)
          transition
          (duration 500)
          (ease "linear")
          (attrTween "d" (fn [d]
                           (let [progress (/ (.-value d) (.-max d))
                                 interpolate (.. js/d3
                                                 (interpolate 0 (* 2 js/Math.PI progress)))]
                             (fn [t]
                               (set! (.-endAngle d) (interpolate t))
                               (arc d))))))
      (.. text-element
          enter
          (append "text")
          (attr "text-anchor" "middle")
          (attr "dy" ".35em")
          (attr "font-size" "24")
          (text "0"))

      (.. text-element
          transition
          (duration 500)
          (text (fn [d] ((.. js/d3
                             -time
                             (format "%d %b %y"))
                          (js/Date. (.-name d)))))
          ;(tween "text" (fn [d]
          ;                (debug "Progress data text: ")
          ;                (this-as jthis
          ;                  (let [value (.-value d)
          ;                        i (.. js/d3
          ;                              (interpolate (.-textContent jthis) value))
          ;                        prec (.split (str value) ".")
          ;                        round (if (> (.-length prec) 1)
          ;                                (.pow js/Math 10 (.-length (get prec 1)))
          ;                                1)]
          ;                    (fn [t]
          ;
          ;                      (set! (.-textContent jthis) (gstring/format "%.2f" (/ (.round js/Math (* (i t) round)) round)))))))
          ;       )
          )

      (d3/update-on-resize this id)
      (om/update-state! this assoc :svg svg :graph graph :meter meter :arc arc :js-data js-data)))

  (update [this]
    (let [{:keys [svg meter arc js-data margin graph]} (om/get-state this)
          {inner-width :width
           inner-height :height} (d3/svg-dimensions svg {:margin margin})
          ;js-first-data (last js-data)
          path-width (/ (min inner-width inner-height) 7)]
      ;(debug "Progress meter update: " js-first-data)
      (when-not (or (js/isNaN inner-height) (js/isNaN inner-width))
        (.. graph
            (attr "transform" (str "translate(" (/ inner-width 2) "," (/ inner-height 2) ")")))

        (.. arc
            (innerRadius (- (/ (min inner-width inner-height) 2) path-width))
            (outerRadius (/ (min inner-width inner-height) 2)))
        ;(.. meter
        ;    (selectAll ".foreground")
        ;    ;(datum js-first-data)
        ;    transition
        ;    (duration 250)
        ;    (attr "d" arc))
        (.. meter
            (selectAll ".foreground")
            (attr "d" arc))
        (.. meter
            (selectAll ".background")
            (attr "d" arc))
        )))

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
         (opts {:id (str "progress-bar-" id)
                :style {:height "100%"
                        :width "100%"}})]))))

(def ->ProgressBar (om/factory ProgressBar))
