(ns eponai.web.ui.d3
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
    [cljsjs.d3]
    [goog.string :as gstring]
    [sablono.core :refer-macros [html]]
    [om.next :as om]
    [taoensso.timbre :refer-macros [debug]]))

(defn build-svg [element width height]
  (-> js/d3
      (.select element)
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

(defn tooltip-add-value [tooltip value color-scale]
  (let [values (.. tooltip
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

(defn tooltip-add-data [tooltip title values color-scale]
  (let [values (.. tooltip
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
                (gstring/format "%.2f" (.-value d)))))
    (.. values
        (select ".color")
        (style "background" (fn [_ i] (color-scale i))))))

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

(defn mouse-over [mouse x-scale js-data f]
  (let [mouseX (first mouse)
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