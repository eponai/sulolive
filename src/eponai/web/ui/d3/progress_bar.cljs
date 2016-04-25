(ns eponai.web.ui.d3.progress-bar
  (:require
    [cljsjs.d3]
    [eponai.client.ui :refer-macros [opts]]
    [eponai.web.ui.d3 :as d3]
    [goog.string :as gstring]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug]]))

(defn- end-angle [d]
  (let [progress (if (map? d)
                   (/ (:value d) (:max d))
                   (/ (.-value d) (.-max d)))]
    (debug "Goal end angle: " progress)
    (* 2 js/Math.PI progress)))
(defui ProgressBar
  Object
  (create [this]
    (let [{:keys [id width height data]} (om/props this)
          svg (d3/build-svg (str "#progress-bar-" id) width height)
          {:keys [margin]} (om/get-state this)
          {inner-height :height
           inner-width :width} (d3/svg-dimensions svg {:margin margin})

          cycle (first data)
          _ (debug "Goal cycle: " cycle)

          js-data (clj->js [(assoc (last (:values cycle)) :endAngle 0 :max (:limit cycle))])
          path-width (/ (min inner-width inner-height) 7)
          graph (.. svg
                    (append "g")
                    (attr "transform" (str "translate(" (/ inner-width 2) "," (/ inner-height 2) ")")))
          meter (.. graph
                    (append "g")
                    (attr "class" "circle-progress"))
          txt (.. meter
                  (append "text")
                  (attr "class" "txt")
                  (attr "text-anchor" "middle")
                  ;(attr "dy" ".15em")
                  (attr "font-size" 14))
          arc (.. js/d3
                  -svg
                  arc
                  (innerRadius (- (/ (min inner-width inner-height) 2) path-width))
                  (outerRadius (/ (min inner-width inner-height) 2))
                  (startAngle 0))]

      (.. meter
          (append "path")
          (attr "class" "background")
          (datum #js {:endAngle (* 2 js/Math.PI)})
          (style "fill" "green")
          (attr "d" arc))
      (d3/update-on-resize this id)
      (om/update-state! this assoc :svg svg :graph graph :meter meter :arc arc :js-data js-data :txt txt)))

  (update [this]
    (let [{:keys [svg meter arc js-data margin graph start-angle txt]} (om/get-state this)
          {inner-width :width
           inner-height :height} (d3/svg-dimensions svg {:margin margin})
          path-width (/ (min inner-width inner-height) 7)]
      (when-not (or (js/isNaN inner-height) (js/isNaN inner-width))
        (let [progress (.. meter
                           (selectAll ".foreground")
                           (data js-data))
              date-txt (.. txt
                           (selectAll ".date-txt")
                           (data js-data))
              val-txt (.. txt
                          (selectAll ".val-txt")
                          (data js-data))]

          (debug "Goal data progress-meter: " js-data)
          (.. graph
              (attr "transform" (str "translate(" (/ inner-width 2) "," (/ inner-height 2) ")")))

          (.. arc
              (innerRadius (- (/ (min inner-width inner-height) 2) path-width))
              (outerRadius (/ (min inner-width inner-height) 2)))

          (.. progress
              enter
              (append "path")
              (attr "class" "foreground")
              (style "fill" "orange"))
          
          (.. progress
              transition
              (duration 250)
              (attrTween "d"
                         (fn [d]
                           (let [interpolate (.. js/d3
                                                 (interpolate start-angle (end-angle d)))]
                             (fn [t]
                               (set! (.-endAngle d) (interpolate t))
                               (arc d))))))
          (.. meter
              (selectAll ".background")
              (attr "d" arc))
          (.. val-txt
              enter
              (append "tspan")
              (attr "class" "val-txt")
              (text "0"))

          (.. val-txt
              transition
              (duration 500)
              (text (fn [d]
                      (gstring/format "%.2f" (.-value d)))))

          (.. date-txt
              enter
              (append "tspan")
              (attr "class" "date-txt")
              (attr "dy" "1.5em")
              (attr "x" 0)
              (text ""))

          (.. date-txt
              transition
              (duration 500)
              (text (fn [d]
                       ((.. js/d3
                                 -time
                                 (format "%a %d %b"))
                              (js/Date. (.-name d))))))))))

  (componentDidMount [this]
    (d3/create-chart this))

  (componentDidUpdate [this _ _]
    (d3/update-chart this))

  (componentWillReceiveProps [this next-props]
    (let [{:keys [data]} next-props
          cycle (first data)
          ;new-data (assoc (last (:values cycle)) :endAngle 0 :max (:limit cycle))
          new-start-angle (end-angle (assoc (last (:values cycle)) :max (:limit cycle)))]
      (om/update-state! this assoc :start-angle new-start-angle)
      (d3/update-chart-data this [(last (:values cycle))])))

  (initLocalState [_]
    {:start-angle 0})

  (render [this]
    (let [{:keys [id]} (om/props this)]
      (html
        [:div
         (opts {:id (str "progress-bar-" id)
                :style {:height "100%"
                        :width "100%"}})]))))

(def ->ProgressBar (om/factory ProgressBar))
