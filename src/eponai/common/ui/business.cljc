(ns eponai.common.ui.business
  (:require
    [om.next :as om :refer [defui]]
    [om.dom :as dom]
    [eponai.common.business.budget :as b]
    [taoensso.timbre :refer [debug]]
    #?(:cljs [cljsjs.nvd3])))

#?(:cljs
   (defn create-graph [this]
     (let [chart (-> (js/nv.models.lineChart)
                     (.options #js {:duration                300
                                    :useInteractiveGuideline true}))
           _ (set! (.-xAxis chart)
                   (-> (.-xAxis chart)
                       (.axisLabel "Time (s)")))
           _ (set! (.-yAxis chart)
                   (-> (.-yAxis chart)
                       (.axisLabel "Voltage (v)")))
           data (clj->js [{:key "data" :color "667711" :values [{:x 1 :y 2} {:x 2 :y 3}]}])
           chart-ref (om/react-ref this "line-chart")
           selected (.select js/d3 chart-ref)]
       (debug [:chart-ref chart-ref :selected selected])
       (-> selected
           (.append "svg")
           (.datum data)
           (.call chart))
       chart)))

(defui Business
  static om/IQuery
  (query [this]
    [:query/business-model])
  Object
  (componentDidMount [this]
    #?(:cljs (.addGraph js/nv #(create-graph this))))
  (render [this]
    (dom/div #js {:ref   "line-chart"
                  :style #js {:height "100%"
                              :width  "100%"}})))

(def ->Business (om/factory Business))