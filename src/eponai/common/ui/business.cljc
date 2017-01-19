(ns eponai.common.ui.business
  (:require
    [om.next :as om :refer [defui]]
    [om.dom :as dom]
    [eponai.common.business.budget :as b]
    [taoensso.timbre :refer [debug]]
    #?(:cljs [cljsjs.nvd3])))

#?(:cljs
   (defn graph-data [world biz-range vis-range]
     (letfn [(revenue [businesses visitors]
               (-> world
                   (b/add-businesses businesses)
                   (b/add-visitors visitors)
                   (b/revenue)
                   (select-keys [:incomes :expenses :profit])
                   (update :incomes :total)
                   (update :expenses :total)))]
       (->> (map vector biz-range vis-range)
            (map #(apply revenue %))
            (mapcat seq)
            (group-by key)
            (map (fn [[k values]]
                   {:key    (name k)
                    :values (vec (map-indexed (fn [i [_ v]] {:x i :y v}) values))}))
            (map (fn [color m] (assoc m :color color))
                 ["47a"
                  "a47"
                  "7a4"])
            ((fn [x] (debug x) x))
            (clj->js)))))

#?(:cljs
   (defn create-graph [this]
     (let [chart (-> (js/nv.models.lineChart)
                     (.options #js {:duration                300
                                    :useInteractiveGuideline true}))
           biz-range [ 1 10  100 200 500 1000 2000 5000 10000]
           ;;(vec (take 7 (iterate #(* % 2) 1)))
           vis-range (mapv (partial * 1000) biz-range)
           ;;(vec (take 7 (iterate #(* % 5) 1)))
           _ (set! (.-xAxis chart)
                   (-> (.-xAxis chart)
                       (.tickFormat (fn [x] (str [(nth biz-range x) (nth vis-range x)])))
                       (.axisLabel "[businesses visitors]")))
           _ (set! (.-yAxis chart)
                   (-> (.-yAxis chart)
                       (.showMaxMin false)
                       (.axisLabel "USD ($)")))
           data (graph-data b/world biz-range vis-range)
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
    (dom/div
      #js {:style #js {:height "400px"}}
      (dom/div #js {:ref   "line-chart"
                   :style #js {:height "100%"
                               :width  "100%"}}))))

(def ->Business (om/factory Business))