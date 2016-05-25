(ns eponai.web.ui.d3
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
    [cljsjs.d3]
    [cljsjs.react.dom]
    [goog.string :as gstring]
    [sablono.core :refer-macros [html]]
    [om.next :as om]
    [taoensso.timbre :refer-macros [debug error]]))

(defn build-svg [ref width height]
  (-> js/d3
      (.select (js/ReactDOM.findDOMNode ref))
      (.append "svg")
      (.style #js {:width width :height height})))

(defn tooltip-build [id]
  (let [tooltip (.. js/d3
                    (select "body")
                    (append "div")
                    (attr "id" (str "tooltip-" id))
                    (attr "class" "d3 tooltip"))]
    (.. tooltip
        (append "text")
        (attr "class" "txt title")
        (attr "x" "50%")
        (attr "text-anchor" "middle"))
    tooltip))

(defn tooltip-remove-all []
  (.. js/d3
      (selectAll ".d3.tooltip")
      remove))

(defn tooltip-remove [id]
  (.. js/d3
     (select (str "#tooltip-" id))
      remove))

(defn tooltip-select [id]
  (.. js/d3
      (select (str "#tooltip-" id))))

(defn tooltip-add-value [id value color-scale]
  (let [tooltip (tooltip-select id)
        values (.. tooltip
                   (selectAll ".values")
                   (data #js [value]))
        enter-sel (.. values
                      enter
                      (append "div")
                      (attr "class" "values"))]
    (.. enter-sel
        (append "div")
        (attr "class" "color"))

    (.. enter-sel
        (append "text")
        (attr "class" "txt value"))

    (.. tooltip
        (select ".txt.title")
        (text (.-name value)))

    (.. values
        (select ".txt.value")
        (text (gstring/format "%.2f" (.-value value))))
    (.. values
        (select ".color")
        (style "background" (color-scale (.-name value))))))

(defn tooltip-add-data [id title values color-fn]
  (let [tooltip (tooltip-select id)
        values (.. tooltip
                   (selectAll ".values")
                   (data values))
        enter-sel (.. values
                      enter
                      (append "div")
                      (attr "class" "values"))]
    (.. enter-sel
        (append "div")
        (attr "class" "color"))

    (.. enter-sel
        (append "text")
        (attr "class" "txt value"))

    (.. tooltip
        (select ".txt.title")
        (text title))

    (.. values
        (select ".txt.value")
        (text (fn [d]
                (when d
                  (gstring/format "%.2f" (.-value d))))))
    (.. values
        (select ".color")
        (style "background" color-fn))))

(defn tooltip-set-pos [id left top]
  (let [tooltip (tooltip-select id)]
    (.. tooltip
        (style "left" (str left "px"))
        (style "top" (str top "px")))))

(defn clip-path-append [el id]
  (.. el
      (append "clipPath")
      (attr "id" (str "clip-" id))
      (append "rect")))

(defn clip-path-set-dimensions [id width height]
  (.. js/d3
      (select (str "#clip-" id))
      (selectAll "rect")
      (attr "width" width)
      (attr "height" height)))

(defn clip-path-url [id]
  (str "url(#clip-" id ")"))

(defn brush-append [el x y]
  (.. el
      (append "g")
      (attr "class" "brush")
      (attr "transform" (str "translate(" x ", " y ")"))))

(defn brush-config [el {:keys [x-scale height on-end]}]
  (let [brush (.. js/d3
                  -svg
                  brush
                  (x x-scale))
        brushend (fn []
                   (on-end (.extent brush))
                   (.. el
                       (select ".brush")
                       (call (.clear brush))))]

    (.. brush
        (x x-scale)
        (on "brushend" brushend))

    (.. el
        (selectAll ".brush")
        (call brush)
        (selectAll "rect")
        (attr "y" 0)
        (attr "height" height))))

(defn focus-append [svg & [{:keys [margin clip-path]}]]
  (let [focus (.. svg
                  (append "g")
                  (attr "class" "focus")
                  (style "display" "none")
                  (attr "transform" (str "translate(" (or (:left margin) 0) "," (or (:top margin) 0) ")")))]
    (.. focus
        (append "rect")
        (attr "class" "guide"))
    (when clip-path
      (.. focus
          (select "rect")
          (style "clip-path" clip-path)))))

(defn focus-set-height [el height]
  (let [focus (.. el
                  (select ".focus"))]
    (.. focus
        (select ".guide")
        (attr "height" height))))

(defn focus-set-guide [elem x y]
  (let [guide (.. elem
                  (select ".focus")
                  (select ".guide"))]
    (.. guide
        (attr "transform" (str "translate(" x "," y ")")))))

(defn focus-show [elem]
  (.. elem
      (select ".focus")
      (style "display" nil)))

(defn focus-hide [elem]
  (.. elem
      (select ".focus")
      (style "display" "none")))

(defn focus-set-data-points [elem values {:keys [x-fn y-fn color-fn]}]
  (assert (and x-fn y-fn color-fn) "Needs functions provided to find coordinate and set color.")
  (let [focus (.. elem
                  (select ".focus"))
        point (.. focus
                  (selectAll "circle")
                  (data values))]
    (.. point
        enter
        (append "circle")
        (attr "class" "point")
        (attr "r" 3.5))

    (.. point
        (attr "transform" #(str "translate(" (x-fn %) "," (y-fn %) ")"))
        (style "stroke" color-fn))))

(defn svg-dimensions [svg & [opts]]
  (let [{:keys [margin]} opts

        width (js/parseInt (.. svg (style "width")))
        height (js/parseInt (.. svg (style "height")))]

    {:width  (- width (or (:left margin) 0) (or (:right margin) 0))
     :height (- height (or (:top margin) 0) (or (:bottom margin) 0))}))

(defn no-data-insert [container]
  (let [no-data-text (.. container
                    (selectAll "text.no-data")
                    (data #js ["No transactions available"]))

        width (js/parseInt (.. container (style "width")) 10)
        height (js/parseInt (.. container (style "height")) 10)]
    (.. no-data-text
        enter
        (append "text")
        (attr "class" "no-data")
        (attr "dy" "-.7em")
        (style "text-anchor" "middle"))

    (.. no-data-text
        (attr "x" (/ width 2))
        (attr "y" (/ height 2))
        (text (fn [t] t)))))

(defn no-data-remove [container]
  (.. container
      (selectAll "text.no-data")
      remove))

(defn mouse-over-burndown [mouse x-scale values f & [margin]]
  (let [mouseX (first mouse)
        sample-data (.-values values)
        bisect-date (.. js/d3
                        (bisector (fn [d]
                                    (.-name d)))
                        -left)
        x0 (.invert x-scale mouseX)
        i (bisect-date sample-data x0 1)
        d0 (get sample-data (dec i))
        d1 (get sample-data i)]
    (when (and d1 d0 f)
      (let [index (if (> (- x0 (.-name d0)) (- (.-name d1) x0))
                    i (dec i))]
        (f (.-name (get sample-data index)) index)))))

(defn mouse-over [mouse x-scale js-data f & [margin]]
  (let [mouseX (- (first mouse) (or (:left margin) 0))
        sample-data (.-values (first js-data))
        bisect-date (.. js/d3
                        (bisector (fn [d]
                                    (.-name d)))
                        -left)
        x0 (.invert x-scale mouseX)
        i (bisect-date sample-data x0 1)
        d0 (get sample-data (dec i))
        d1 (get sample-data i)]
    (when (and d1 d0 f)
      (let [index (if (> (- x0 (.-name d0)) (- (.-name d1) x0))
                      i (dec i))]
        (f (.-name (get sample-data index)) (.map js-data #(get (.-values %) index)))))))

;; Responsive update helpers
(defn window-resize [component]
  (let [{:keys [resize-timer]} (om/get-state component)]
    (when resize-timer
      (js/clearTimeout resize-timer))
    (om/update-state! component assoc
                      :resize-timer
                      (js/setTimeout (fn []
                                       (.update component)
                                       (om/update-state! component dissoc :resize-timer))
                                     50))))

(defn update-on-resize [component el-id]
  (.. js/d3
      (select js/window)
      (on (str "resize." el-id) #(window-resize component))))

(defn create-chart [component]
  (.create component))

(defn update-chart [component]
  (let [{:keys [resize-timer]} (om/get-state component)]
    ; If a resize timer is active, it will trigger an update when it's finished and remove itself from the state.
    ; Don't do anything in that case to avoid updating too fast.
    (when-not resize-timer
      (.update component))))

(defn update-chart-data [component new-data]
  (let [js-domain (clj->js (flatten (map :values new-data)))
        js-data (clj->js (or new-data []))]
    (om/update-state! component assoc
                      :js-domain js-domain
                      :js-data js-data)))