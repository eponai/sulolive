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
          js-first-data (clj->js (assoc (first data) :endAngle 0))
          graph (.. svg
                    (append "g")
                    (attr "transform" (str "translate(" (/ inner-width 2) "," (/ inner-height 2) ")")))
          meter (.. graph
                    (append "g")
                    (attr "class" "season-progress"))
          arc (.. js/d3
                  -svg
                  arc
                  (innerRadius (- (/ inner-height 2) 50))
                  (outerRadius (/ inner-height 2))
                  (startAngle 0))
          text-element (.. meter
                           (selectAll ".txt")
                           (data #js [js-first-data]))]
      (.. meter
          (append "path")
          (datum #js {:endAngle (* 2 js/Math.PI)})
          (style "fill" "#ddd")
          (attr "d" arc))

      (.. meter
          (append "path")
          (datum js-first-data)
          (style "fill" "orange")
          (attr "class" "foreground")
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
          (tween "text" (fn [d]
                          (debug "Progress data text: ")
                          (this-as jthis
                            (let [value (.-value d)
                                  i (.. js/d3
                                        (interpolate (.-textContent jthis) value))
                                  prec (.split (str value) ".")
                                  round (if (> (.-length prec) 1)
                                          (.pow js/Math 10 (.-length (get prec 1)))
                                          1)]
                              (fn [t]
                                (set! (.-textContent jthis) (gstring/format "%.2f" (/ (.round js/Math (* (i t) round)) round)))))))))

      (om/update-state! this assoc :svg svg :graph graph)))

  (componentDidMount [this]
    (d3/create-chart this))

  (render [this]
    (let [{:keys [id]} (om/props this)]
      (html
        [:div
         (opts {:id (str "progress-bar-" id)
                :style {:height "100%"
                        :width "100%"}})]))))

(def ->ProgressBar (om/factory ProgressBar))
