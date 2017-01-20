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

(def graphs {:varying-biz-and-vis (let [biz-range [1 10 100 200 500 1000 2000 5000 10000]
                                        vis-range (mapv (partial * 10) biz-range)]
                                    {:biz-range             biz-range
                                     :vis-range             vis-range
                                     :x-axis/tick-format-fn (fn [x] (str [(nth biz-range x) (nth vis-range x)]))
                                     :x-axis/label          "[businesses visitors]"})
             :varying-biz         (let [biz-range [1 10 100 200 500 1000 2000 5000 10000]
                                        visitors 1000]
                                    {:biz-range             biz-range
                                     :vis-range             (repeat visitors)
                                     :x-axis/tick-format-fn (fn [x] (nth biz-range x))
                                     :x-axis/label          (str "businesses=x, visitors=" visitors)})
             :varying-vis         (let [vis-range (into [] (take 6) (iterate (partial * 10) 1))
                                        businesses 100]
                                    {:biz-range             (repeat businesses)
                                     :vis-range             vis-range
                                     :x-axis/tick-format-fn (fn [x] (nth vis-range x))
                                     :x-axis/label          (str "businesses=" businesses ", visitors=x")})})

#?(:cljs
   (defn create-graph [chart-ref {:keys [biz-range vis-range] :x-axis/keys [tick-format-fn label]}]
     (let [chart (-> (js/nv.models.lineChart)
                     (.options #js {:duration                300
                                    :useInteractiveGuideline true}))
           _ (set! (.-xAxis chart)
                   (-> (.-xAxis chart)
                       (.tickFormat tick-format-fn)
                       (.axisLabel label)))
           _ (set! (.-yAxis chart)
                   (-> (.-yAxis chart)
                       (.showMaxMin false)
                       (.axisLabel "USD ($)")))
           data (graph-data b/world biz-range vis-range)
           selected (.select js/d3 chart-ref)]
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
    #?(:cljs (doall (map-indexed (fn [id graph-key]
                                   (debug "Adding graph: " (get graphs graph-key))
                             (.addGraph js/nv #(create-graph (om/react-ref this (str "line-chart-" (inc id)))
                                                             (get graphs graph-key))))
                           [:varying-biz
                            :varying-vis
                            :varying-biz-and-vis]))))
  (render [this]
    (dom/div
      #js {:className "row small-up-2"
           :style #js {:height "350px"}}
      (dom/div #js {:ref   "line-chart-1"
                    :className "column"
                   :style #js {:height "100%"}})
      (dom/div #js {:ref   "line-chart-2"
                    :className "column"
                    :style #js {:height "100%"}})
      (dom/div #js {:ref   "line-chart-3"
                    :className "column"
                    :style #js {:height "100%"}})
      (dom/div #js {:className "column"
                    :style #js {:height "100%"}}))))

(def ->Business (om/factory Business))