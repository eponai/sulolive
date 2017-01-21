(ns eponai.common.ui.business
  (:require
    [om.next :as om :refer [defui]]
    [om.dom :as dom]
    [eponai.common.business.budget :as b]
    [taoensso.timbre :refer [debug]]
    #?@(:cljs [[cljsjs.nvd3]
               [cljs.reader :as reader]])
    [clojure.data :as data]
    [medley.core :as medley]))

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
                    :values (vec (map-indexed (fn [i [_ v]]
                                                {:x i :y (* v (:fixed/days world))})
                                              values))}))
            (map (fn [color m] (assoc m :color color))
                 ["47a"
                  "a47"
                  "7a4"])
            ((fn [x] (debug x) x))
            (clj->js)))))

(def graph-order [:varying-biz
                  :varying-vis
                  :varying-biz-and-vis])

(defn graphs [this]
  (let [{:fixed/keys [visitors businesses]} (:model (om/get-state this))]
    {:varying-biz-and-vis (let [biz-range (take 9 (iterate (partial * 2) 10))
                                vis-range (mapv (partial * 10) biz-range)]
                            {:biz-range             biz-range
                             :vis-range             vis-range
                             :x-axis/tick-format-fn (fn [x] (str [(nth biz-range x) (nth vis-range x)]))
                             :x-axis/label          "[businesses visitors]"})
     :varying-biz         (let [biz-range (take 9 (iterate (partial * 2) 10))]
                            {:biz-range             biz-range
                             :vis-range             (repeat visitors)
                             :x-axis/tick-format-fn (fn [x] (nth biz-range x))
                             :x-axis/label          (str "businesses=x, visitors=" visitors)})
     :varying-vis         (let [vis-range (into [] (take 6) (iterate (partial * 10) 1))]
                            {:biz-range             (repeat businesses)
                             :vis-range             vis-range
                             :x-axis/tick-format-fn (fn [x] (nth vis-range x))
                             :x-axis/label          (str "businesses=" businesses ", visitors=x")})}))

#?(:cljs
   (defn adjusted-business-model [this]
     (let [model (:query/business-model (om/props this))
           state-model (->> (:model (om/get-state this))
                            (medley/map-vals reader/read-string))]
       (merge model state-model))))

#?(:cljs
   (defn create-chart [chart-ref business-model {:keys        [biz-range vis-range]
                                                 :x-axis/keys [tick-format-fn label]}]
     (let [chart (-> (js/nv.models.lineChart)
                     (.options #js {:duration                300
                                    :useInteractiveGuideline true}))]
       (set! (.-xAxis chart)
             (-> (.-xAxis chart)
                 (.tickFormat tick-format-fn)
                 (.axisLabel label)))
       ;; (.yScale chart (.log (.-scale js/d3)))
       (set! (.-yAxis chart)
             (-> (.-yAxis chart)
                 (.showMaxMin false)
                 (.axisLabel "USD ($)")))
       (-> (.select js/d3 (str "#" chart-ref))
           (.append "svg")
           (.datum (graph-data business-model biz-range vis-range))
           (.call chart))
       chart)))

#?(:cljs
   (defn update-chart [chart-ref business-model {:keys        [biz-range vis-range]
                                                 :x-axis/keys [tick-format-fn label]} chart]
     {:pre [(some? chart)]}
     (let [select-str (str "#" chart-ref " svg")]
       (set! (.-xAxis chart)
             (-> (.-xAxis chart)
                 (.tickFormat tick-format-fn)
                 (.axisLabel label)))
       (-> (.select js/d3 select-str)
           (.datum (graph-data business-model biz-range vis-range))
           (.transition)
           (.duration 500)
           (.call chart)))))

#?(:cljs
   (defn render-input-controls [this]
     (let [model (:query/business-model (om/props this))
           create-input (fn [k]
                          (dom/div #js {:className "column"
                                        :style     #js {:display        "flex"
                                                        :justifyContent "space-between"}}
                            (dom/span nil (str k))
                            (dom/input #js {:value    (str (or (get-in (om/get-state this) [:model k])
                                                               (get model k)))
                                            :onChange #(do
                                                         (om/update-state! this assoc-in [:model k] (.-value (.-target %))))})))
           controls [:visitor/stream-viewing-in-secs
                     :conversion-rate/product-sales
                     :price/avg-product
                     :price/business-subscription
                     :product/commission-rate
                     :fixed/visitors
                     :fixed/businesses
                     :fixed/days
                     :stream/avg-edges
                     :website/avg-servers]]
       (dom/div #js {:className "row small-up-1"}
         (into [] (map create-input controls))))))

(defui Business
  static om/IQuery
  (query [this]
    [:query/business-model])
  Object
  (initLocalState [this]
    {:model {:fixed/visitors   "2000"
             :fixed/businesses "200"
             :fixed/days       (str b/days-per-month)}})
  (componentDidMount [this]
    #?(:cljs
       (let [charts-by-graph-key
             (into {} (map-indexed (fn [id graph-key]
                                     (let [create-graph-fn (memoize
                                                             #(create-chart (str "line-chart-" id)
                                                                            (adjusted-business-model this)
                                                                            (get (graphs this) graph-key)))]
                                       (.addGraph js/nv create-graph-fn)
                                       ;; returns the graph-key associated with the chart object returned
                                       ;; in create-graph-fn. It's memoized, so it'll just return when we call
                                       ;; it here.
                                       [graph-key (create-graph-fn)])))
                   graph-order)]
         (om/update-state! this assoc :charts charts-by-graph-key))))
  (componentDidUpdate [this _ prev-state]
    #?(:cljs
       (when (not= (:model prev-state) (:model (om/get-state this)))
         (doall
           (map-indexed (fn [id graph-key]
                          (update-chart (str "line-chart-" id)
                                        (adjusted-business-model this)
                                        (get (graphs this) graph-key)
                                        (get-in (om/get-state this) [:charts graph-key])))
                        graph-order)))))
  (render [this]
    (dom/div #js {:className "row small-up-2"
                  :style     #js {:height "350px"}}
      (dom/div #js {:id       "line-chart-0"
                    :className "column"
                    :style     #js {:height "100%"}})
      (dom/div #js {:id       "line-chart-1"
                    :className "column"
                    :style     #js {:height "100%"}})
      (dom/div #js {:id       "line-chart-2"
                    :className "column"
                    :style     #js {:height "100%"}})
      (dom/div #js {:className "column"
                    :style     #js {:height "100%"}}
        (dom/p nil "Controls normalized to USD per Day where applicable")
        #?(:cljs (render-input-controls this))))))

(def ->Business (om/factory Business))