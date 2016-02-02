(ns eponai.client.ui.d3
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [eponai.client.ui :refer [map-all] :refer-macros [style opts]]
            [cljs.core.async :as c :refer [chan]]
            [garden.core :refer [css]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [cljsjs.d3]))

(defui BarChart
  Object
  (initLocalState [_]
    {:margin {:top    20
              :right  30
              :bottom 30
              :left   40}})
  (componentDidMount [this]
    (let [{:keys [width height]} (om/props this)
          svg (-> js/d3
                  (.select ".chart")
                  (.append "svg")
                  (.attr "width" width)
                  (.attr "height" height))]
      (om/update-state! this assoc :svg svg)))

  (componentDidUpdate [this _ _]
    (let [{:keys [svg
                  margin]} (om/get-state this)
          {:keys [data
                  width
                  height
                  title-axis-y]} (om/props this)
          data (clj->js data)]
      (let [inner-width  (- width (:right margin) (:left margin))
            inner-height  (- height (:top margin) (:bottom margin))

            y (-> js/d3
                  .-scale
                  .linear
                  (.domain #js [0 (-> js/d3
                                      (.max data
                                            (fn [d]
                                              (.-value d))))])
                  (.range #js [inner-height 0]))
            x (-> js/d3
                  .-scale
                  .ordinal
                  (.domain (.map data (fn [d]
                                        (.-name d))))
                  (.rangeRoundBands #js [0 inner-width]
                                    0.1))
            x-axis (-> js/d3
                       .-svg
                       .axis
                       (.scale x)
                       (.orient "bottom"))
            y-axis (-> js/d3
                       .-svg
                       .axis
                       (.scale y)
                       (.orient "left"))
            chart (-> svg
                      (.attr "width" width)
                      (.attr "height" height)
                      (.append "g")
                      (.attr "transform" (str "translate(" (:left margin) ", "
                                              (:top margin) ")")))]

        (-> chart
            (.append "g")
            (.attr "class" "x axis")
            (.attr "transform" (str "translate(0, " inner-height ")"))
            (.call x-axis))

        (-> chart
            (.append "g")
            (.attr "class" "y axis")
            (.call y-axis))

        (-> chart
            (.selectAll ".bar")
            (.data data)
            (.enter)
            (.append "rect")
            (.attr "class" "bar")
            (.attr "x" (fn [d] (x (.-name d))))
            (.attr "y" (fn [d] (y (.-value d))))
            (.attr "height" (fn [d] (- inner-height (y (.-value d)))))
            (.attr "width" (.rangeBand x)))

        (-> chart
            (.append "g")
            (.attr "class" "y axis")
            (.call y-axis)
            (.append "text")
            (.attr "transform" "rotate(-90)")
            (.attr "y" 6)
            (.attr "dy" ".71em")
            (.style "text-anchor" "end")
            (.text title-axis-y)))))

  (render [this]
    (prn "Bar props: " (om/props this))
    (html
      [:div.chart
       [:style (css
                 [:.bar
                  {:fill "#2EC4B6"
                   :stroke "#20968B"}]
                 [:.x.axis
                  [:path
                   {:display :none}]]
                 [:.axis
                  [:text
                   {:font "10px sans-serif"}]
                  [:path :line
                   {:fill :none
                    :stroke "#000"
                    :shape-rendering "crispEdges"}]])]])))

(def ->BarChart (om/factory BarChart))

(defui AreaChart
  Object
  (initLocalState [_]
    {:margin {:top    20
              :right  30
              :bottom 30
              :left   40}
     :svg    nil})
  (componentDidMount [this]
    (let [{:keys [margin]} (om/get-state this)
          {:keys [width height]} (om/props this)
          svg (-> js/d3
                  (.select ".chart")
                  (.append "svg")
                  (.attr "width" width)
                  (.attr "height" height))]
      (om/update-state! this assoc :svg svg)))
  (componentDidUpdate [this _ _]
    (let [{:keys [svg
                  margin]} (om/get-state this)
          {:keys [data
                  width
                  height
                  title-axis-y]} (om/props this)
          data (clj->js data)]

      (let [inner-width  (- width (:right margin) (:left margin))
            inner-height  (- height (:top margin) (:bottom margin))

            date-format (-> js/d3
                           .-time
                           (.format "%Y-%m-%d"))
            _ (.forEach data
                        (fn [d]
                          (set! (.-name d)
                                (.parse date-format (.-name d)))))
            x (-> js/d3
                  .-time
                  .scale
                  (.domain (-> js/d3
                               (.extent data (fn [d]
                                               (.-name d)))))
                  (.range #js [0 inner-width]))
            y (-> js/d3
                  .-scale
                  .linear
                  (.domain #js [0 (-> js/d3
                                      (.max data (fn [d]
                                                   (.-value d))))])
                  (.range #js [inner-height 0]))
            y-mean (-> js/d3
                       .-svg
                       .line
                       (.x (fn [d] (x (.-name d))))
                       (.y (y (-> js/d3
                                  (.mean data
                                         (fn [d] (.-value d)))))))
            x-axis (-> js/d3
                       .-svg
                       .axis
                       (.scale x)
                       (.orient "bottom")
                       (.tickFormat (-> js/d3
                                        .-time
                                        (.format "%b")))
                       (.ticks (-> js/d3 .-time .-month) 1))
            y-axis (-> js/d3
                       .-svg
                       .axis
                       (.scale y)
                       (.orient "left"))
            area (-> js/d3
                     .-svg
                     .area
                     (.x (fn [d] (x (.-name d))))
                     (.y0 inner-height)
                     (.y1 (fn [d] (y (.-value d)))))
            chart (-> svg
                      (.attr "width" width)
                      (.attr "height" height)
                      (.append "g")
                      (.attr "transform" (str "translate(" (:left margin) ", "
                                              (:top margin) ")")))]
        (-> chart
            (.append "path")
            (.datum data)
            (.attr "class" "area")
            (.attr "d" area))

        (-> chart
            (.append "path")
            (.datum data)
            (.attr "class" "line")
            (.style "stroke-dasharray" "5,3")
            (.style "stroke-width" "2")
            (.attr "d" y-mean))

        (-> chart
            (.append "g")
            (.attr "class" "x axis")
            (.attr "transform" (str "translate(0, " inner-height ")"))
            (.call x-axis))

        (-> chart
            (.append "g")
            (.attr "class" "y axis")
            (.call y-axis)
            (.append "text")
            (.attr "transform" "rotate(-90)")
            (.attr "y" 6)
            (.attr "dy" ".71em")
            (.style "text-anchor" "end")
            (.text title-axis-y)))))

  (render [_]
    (html
      [:div.chart
       [:style (css
                 [:.area
                  {:fill :steelblue
                   }]
                 [:.line
                  {:fill            :none
                   :stroke          "#E71D36"
                   :shape-rendering "crispEdges"}]
                 [:.axis
                  [:path :line
                   {:fill            :none
                    :stroke          "#000"
                    :shape-rendering "crispEdges"}]])]])))

(def ->AreaChart (om/factory AreaChart))
