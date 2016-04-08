(ns eponai.web.ui.d3.number-chart
  (:require
    [cljsjs.d3]
    [eponai.client.ui :refer-macros [opts]]
    [eponai.web.ui.d3 :as d3]
    [goog.string :as gstring]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug]]))

(defui NumberChart
  Object
  (initLocalState [_]
    {:start-val 0})
  (componentDidMount [this]
    (let [{:keys [id width height]} (om/props this)
          svg (d3/build-svg (str "#number-chart-" id) width height)]
      (om/update-state! this assoc :svg svg)))
  (componentWillReceiveProps [this _]
    (let [{:keys [data]} (om/props this)
          [start-val] (:values (first data))]
      (om/update-state! this assoc :start-val (or start-val 0))))
  (componentDidUpdate [this _ _]
    (let [{:keys [svg]} (om/get-state this)
          {:keys [data]} (om/props this)
          {:keys [start-val]} (om/get-state this)
          [end-val] (:values (first data))]
      (let [text-element (.. svg
                             (selectAll ".txt")
                             (data #js [(gstring/format "%.2f" end-val)]))]
        (.. text-element
            enter
            (append "text")
            (attr "class" "txt stat")
            (attr "x" "50%")
            (attr "y" "50%")
            (attr "text-anchor" "middle"))

        (.. text-element
            (text (gstring/format "%.2f" start-val))
            transition
            (duration 250)
            (tween "text" (fn [d]
                            (this-as jthis
                              (let [i (.. js/d3
                                          (interpolate (.-textContent jthis) (cljs.reader/read-string d)))
                                    prec (.split d ".")
                                    round (if (> (.-length prec) 1)
                                            (.pow js/Math 10 (.-length (get prec 1)))
                                            1)]
                                (fn [t]
                                  (set! (.-textContent jthis) (gstring/format "%.2f" (/ (.round js/Math (* (i t) round)) round))))))))))))
  (render [this]
    (let [{:keys [id]} (om/props this)]
      (html
        [:div
         (opts {:id (str "number-chart-" id)
                :style {:height "100%"
                        :width "100%"}})]))))

(def ->NumberChart (om/factory NumberChart))