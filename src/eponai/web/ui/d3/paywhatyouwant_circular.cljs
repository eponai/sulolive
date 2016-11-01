(ns eponai.web.ui.d3.paywhatyouwant-circular
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.web.ui.d3 :as d3]
    [goog.string :as gstring]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug]]))

(defn- end-angle [value]
  (let [limit 10]
    (if (zero? value)
      0
      (let [progress (/ value limit)]
        (* 2 js/Math.PI progress)))))

(defui PayWhatYouWant
  Object
  (create [this]
    (let [{:keys [id value]} (om/props this)
          {:keys [on-select-value on-change-value]} (om/get-computed this)
          svg (d3/build-svg (om/react-ref this "paywhatyouwant"))

          slider (.. js/d3
                     (select ".slider"))
          {inner-height :height
           inner-width :width} (d3/svg-dimensions svg)

          container (.. svg
                        (append "g")
                        (attr "class" "slider")
                        (attr "transform" (str "translate(" (/ inner-width 2) "," (/ inner-height 2) ")")))
          dot-radius 7
          radius (- (/ (min inner-width inner-height) 2) dot-radius)
          circumference (.. container
                            (append "circle")
                            (attr "class" "circum")
                            (attr "r" radius))

          dispatch (.. js/d3 (dispatch "sliderChange" "sliderSet"))
          handle (.. container
                     (append "g")
                     (attr "class" "dot")
                     (selectAll "circle")
                     (data #js [#js {:x 0 :y (- 0 radius)}])
                     enter
                     (append "circle")
                     ;(attr "class" "slider-handle")
                     (attr "r" dot-radius)
                     (attr "cx" #(.-x %))
                     (attr "cy" #(.-y %))
                     (call (.. js/d3 drag
                               (subject (fn [d] d))
                               (on "start" (fn []
                                             (this-as jthis
                                               (.. js/d3 (select jthis)
                                                   (classed "dragging" true)))
                                             (.. js/d3 -event -sourceEvent stopPropagation)))
                               (on "drag" (fn [d]
                                            (this-as jthis
                                              (let [d-from-origin (js/Math.sqrt (+ (js/Math.pow (.. js/d3 -event -x) 2)
                                                                                   (js/Math.pow (.. js/d3 -event -y) 2)))
                                                    alpha (js/Math.acos (/ (.. js/d3 -event -x) d-from-origin))]
                                                (.. js/d3
                                                    (select jthis)
                                                    (attr "cx" (set! (.-x d) (* radius (js/Math.cos alpha))))
                                                    (attr "cy" (set! (.-y d) (if (neg? (.. js/d3 -event -y))
                                                                               (* -1 radius (js/Math.sin alpha))
                                                                               (* radius (js/Math.sin alpha))))))))))
                               (on "end"
                                   (fn []
                                     (this-as jthis
                                       (.. js/d3 (select jthis)
                                           (classed "dragging" false))))))))
          arc (.. js/d3
                  arc
                  (innerRadius (- (/ (min inner-width inner-height) 2) 5))
                  (outerRadius (/ (min inner-width inner-height) 2))
                  (startAngle 0))
          txt (.. slider
                  (append "text")
                  (attr "class" "txt")
                  (attr "text-anchor" "middle")
                  (attr "font-size" 14))
          x-scale (.. js/d3 scaleLinear
                      (range #js [0 inner-width])
                      (domain #js [1 100]))
          slider-track (.. slider
                           (append "div")
                           (attr "class" "slider-track"))
          slider-handle (.. slider
                            (append "div")
                            (attr "class" "slider-handle"))
          mouseX-within-bounds (fn []
                                 (let [mouseX (first (.. js/d3 (mouse (.node slider-track))))]
                                   (min inner-width (max 0 mouseX))))]


      (.. slider
          (call (.. js/d3 drag
                    (on "start" (fn []
                                  (this-as jthis
                                    (.. dispatch
                                        (call "sliderChange" jthis (mouseX-within-bounds))))
                                  (.. js/d3 -event -sourceEvent preventDefault)))
                    (on "drag" (fn []
                                 (this-as jthis
                                   (.. dispatch
                                       (call "sliderChange" jthis (mouseX-within-bounds))))))
                    (on "end"
                        (fn []
                          (this-as jthis
                            (.. dispatch
                                (call "sliderSet" jthis (.invert x-scale (mouseX-within-bounds))))))))))
      (.. dispatch
          (on "sliderChange.slider" (fn [mouseX]
                                      (.. slider-handle
                                          (style "left" (str mouseX "px")))
                                      (when on-change-value
                                        (on-change-value (.invert x-scale mouseX)))))
          (on "sliderSet.slider" (fn [value]
                                   (when on-select-value
                                     (on-select-value value)))))
      ;(.. graph
      ;    (append "path")
      ;    (attr "class" "background")
      ;    (datum #js {:endAngle (* 2 js/Math.PI)})
      ;    (attr "d" arc))

      ;(d3/update-on-resize this id)
      (om/update-state! this assoc :arc arc :txt txt :x-scale x-scale :slider-handle slider-handle)))

  (update [this]
    (let [{:keys [value]} (om/props this)
          {:keys [slider-handle x-scale]} (om/get-state this)]
      (.. slider-handle
          (style "left" (str (x-scale value) "px")))))

  (componentDidMount [this]
    (.create this))
  (componentDidUpdate [this _ _]
    (.update this))

  (componentWillUnmount [this]
    (d3/unmount-chart this))

  (render [_]
    (html
      [:div.paywhatyouwant
       (opts {:ref "paywhatyouwant"})])))

(def ->PayWhatYouWant (om/factory PayWhatYouWant))
