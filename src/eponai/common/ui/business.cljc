(ns eponai.common.ui.business
  (:require
    [om.next :as om :refer [defui]]
    [om.dom :as dom]
    [eponai.common.business.budget :as b]
    [taoensso.timbre :refer [debug]]
    #?@(:cljs [[cljsjs.nvd3]
               [cljs.reader :as reader]])
    [clojure.data :as data]))

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

(def graph-order [:varying-biz
                  :varying-vis
                  :varying-biz-and-vis])

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
   (defn create-graph [chart-ref business-model {:keys        [biz-range vis-range]
                                                 :x-axis/keys [tick-format-fn label]}]
     (let [chart (-> (js/nv.models.lineChart)
                     (.options #js {:duration                300
                                    :useInteractiveGuideline true}))]
       (set! (.-xAxis chart)
             (-> (.-xAxis chart)
                 (.tickFormat tick-format-fn)
                 (.axisLabel label)))
       (set! (.-yAxis chart)
             (-> (.-yAxis chart)
                 (.showMaxMin false)
                 (.axisLabel "USD ($)")))
       (-> (.select js/d3 (str "#" chart-ref))
           (.append "svg")
           (.datum (graph-data business-model biz-range vis-range))
           (.call chart))
       chart)))

(defn adjusted-business-model [this]
  (let [model (:query/business-model (om/props this))
        state-model (:model (om/get-state this))
        ret (merge model state-model)]
    (debug "adjusted-model-differs: " (data/diff model ret))
    ret))

#?(:cljs
   (defn render-input-controls [this]
     (comment
       + Conversion rate
       + Avg price per product
       + Time watched per user
       + Commission
       + Business subscription price)
     (let [model (:query/business-model (om/props this))
           create-input (fn [k]
                          (dom/div nil
                            (dom/span nil (str k))
                            (dom/input #js {:value    (str (or (get-in (om/get-state this) [:model k])
                                                               (get model k)))
                                            :onChange #(do
                                                         (debug %)
                                                         (om/update-state! this assoc-in [:model k]
                                                                           (reader/read-string (.-value (.-target %)))))})))
           controls [:visitor/time-watching-stream
                     :conversion-rate/product-sales
                     :price/avg-product
                     :price/business-subscription
                     :product/commission-rate]]
       (into [] (map create-input controls)))))

(defui Business
  static om/IQuery
  (query [this]
    [:query/business-model])
  Object
  (componentDidMount [this]
    #?(:cljs
       (let [_ (debug "MOUNTING!!!")
             charts-by-graph-key
             (into {} (map-indexed (fn [id graph-key]
                                     (let [create-graph-fn (memoize
                                                             #(create-graph (str "line-chart-" id)
                                                                            (adjusted-business-model this)
                                                                            (get graphs graph-key)))]
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
         (debug "UPDATING!")
         (doall
           (map-indexed (fn [id graph-key]
                          (debug "Updating key: " graph-key)
                          (let [chart-ref (str "line-chart-" id)
                                select-str (str "#" chart-ref " svg")
                                _ (debug "Selecting: " select-str)
                                svg (.select js/d3 select-str)
                                _ (debug "Selected: " select-str " svg: " svg)
                                graph (get graphs graph-key)
                                _ (debug "Getting data for graph: " graph)
                                chart-data (graph-data (adjusted-business-model this)
                                                       (:biz-range graph)
                                                       (:vis-range graph))
                                _ (debug "Chart data: " chart-data)
                                chart (get-in (om/get-state this) [:charts graph-key])]
                            (debug "chart: " chart)
                            (if-not (some? chart)
                              (debug "No chart object found in state: " (om/get-state this))
                              (do
                                (-> svg
                                    (.datum chart-data)
                                    (.transition)
                                    (.duration 500)
                                    (.call chart))
                                (debug "Calling UPDATE chart")
                                (.update chart)))))
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
        #?(:cljs (render-input-controls this))))))

(def ->Business (om/factory Business))