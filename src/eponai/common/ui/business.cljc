(ns eponai.common.ui.business
  (:require
    [om.next :as om :refer [defui]]
    [om.dom :as dom]
    [eponai.common :as common]
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.business.budget :as b]
    [taoensso.timbre :refer [debug]]
    #?(:cljs [cljsjs.nvd3])
    #?(:clj [clojure.edn :as reader]
       :cljs [cljs.reader :as reader])
    [clojure.set :as set]
    [medley.core :as medley]))

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
         (into (sorted-map-by #(compare (name %1) (name %2))))
         (map (fn [[k values]]
                {:key    (name k)
                 :values (vec (map-indexed (fn [i [_ v]]
                                             {:x i :y (* v (:fixed/days world))})
                                           values))}))
         (map (fn [color m] (assoc m :color color))
              ["47a"
               "a47"
               "7a4"
               "5F5"
               "F55"]))))

(def graph-order [:varying-biz
                  :varying-vis
                  :varying-biz-and-vis])

(defn graphs [this]
  (let [{:fixed/keys [visitors businesses]} (:model (om/get-state this))]
    {:varying-biz-and-vis (let [biz-range (take 9 (iterate (partial * 2) 10))
                                customers-per-business-per-day (/ 1000 30)
                                vis-range (mapv (partial * customers-per-business-per-day) biz-range)]
                            {:biz-range             biz-range
                             :vis-range             vis-range
                             :x-axis/tick-format-fn (fn [x] (str [(nth biz-range x) (nth vis-range x)]))
                             :x-axis/label          "[businesses visitors]"})
     :varying-biz         (let [biz-range (take 9 (iterate (partial * 2) 10))]
                            {:biz-range             biz-range
                             :vis-range             (repeat visitors)
                             :x-axis/tick-format-fn (fn [x] (nth biz-range x))
                             :x-axis/label          (str "businesses=x, visitors=" visitors)})
     :varying-vis         (let [vis-range (into [] (take 9) (iterate (partial * 2) 100))]
                            {:biz-range             (repeat businesses)
                             :vis-range             vis-range
                             :x-axis/tick-format-fn (fn [x] (nth vis-range x))
                             :x-axis/label          (str "businesses=" businesses ", visitors=x")})}))

(defn adjusted-business-model [this]
  (let [model (:query/business-model (om/props this))
        state-model (->> (:model (om/get-state this))
                         (medley/map-vals reader/read-string))]
    (merge model state-model)))

(defn create-chart [chart-ref business-model {:keys        [biz-range vis-range]
                                              :x-axis/keys [tick-format-fn label]}]
  #?(:cljs
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
           (.datum (clj->js (graph-data business-model biz-range vis-range)))
           (.call chart))
       chart)))

(defn update-chart [chart-ref business-model {:keys        [biz-range vis-range]
                                              :x-axis/keys [tick-format-fn label]} chart]
  {:pre [(some? chart)]}
  #?(:cljs
     (let [select-str (str "#" chart-ref " svg")]
       (set! (.-xAxis chart)
             (-> (.-xAxis chart)
                 (.tickFormat tick-format-fn)
                 (.axisLabel label)))
       (-> (.select js/d3 select-str)
           (.datum (clj->js (graph-data business-model biz-range vis-range)))
           (.transition)
           (.duration 500)
           (.call chart)))))

(defn render-input-controls [this]
  (let [create-input (fn [i [k label unit]]
                       (my-dom/div
                         (css/grid-row {:key i})
                         (my-dom/div
                           (css/grid-column)
                           (dom/label nil label))
                         (my-dom/div
                           (->> (css/grid-column)
                                (css/grid-column-size {:small 4}))
                           (my-dom/div
                             (->> (css/grid-row)
                                  (css/add-class :collapse)
                                  (css/align :middle))
                             (my-dom/div (->> (css/grid-column)
                                              (css/text-align :right))
                                         (dom/small nil unit))
                             (my-dom/div (css/grid-column)
                                         (dom/input #js {:type     "text"
                                                         :style    #js {:margin 0}
                                                         :value    (str (or (get-in (om/get-state this) [:model k])
                                                                            (get-in (om/props this) [:query/business-model k])))
                                                         :onChange #(om/update-state! this assoc-in [:model k] (.-value (.-target %)))}))))))
        controls {:pricing   {:label    "Our pricing"
                              :controls [[:price/business-subscription "Business subscription fee" "as $USD per day"]
                                         [:product/commission-rate "Commission rate (pre tax, pre shipping)" "% as 0-1"]
                                         [:price/transaction-fees-rate "Transaction fee rate" "% as 0-1"]
                                         [:price/transaction-fees-fixed "Fixed transaction fee" "$USD"]]}
                  :variables {:label    "Variables"
                              :controls [[:visitor/stream-viewing-in-secs "Average time visitor watches a stream" "seconds"]
                                         [:conversion-rate/product-sales "Product sales conversion rate" "$USD"]
                                         [:price/avg-product "Average product price" "$USD"]
                                         [:price/sales-tax "Sales tax" "% as 0-1"]
                                         [:price/avg-shipping-cost "Average shipping cost" "$USD"]]}
                  :tech      {:label    "Tech stuff"
                              :controls [[:stream/avg-edges "Average streaming servers" "servers"]
                                         [:website/avg-servers "Average website servers" "servers"]
                                         [:stream/p2p-efficiency "Percent of how much is streamed p2p" "% as 0-1"]
                                         [:cloudfront/reserved-capacity-savings "Cloudfront reserved capacity savings" "% as 0-1"]]}
                  :graphs    {:label    "Graph settings"
                              :controls [[:fixed/days "Normalize to number of days" "days"]
                                         [:fixed/businesses "Adjust fixed business graph" "businesses"]
                                         [:fixed/visitors "Adjust fixed visitor graph" "visitors"]]}}]

    (dom/div nil
      (map-indexed (fn [i {:keys [label controls]}]
                     [(dom/h3 #js {:key (str "label-" i)} label)
                      (dom/div #js {:key (str "controls-" i)}
                        (into [] (map-indexed create-input) controls))])
                   (map controls [:pricing :variables :graphs :tech])))))

(defui Business
  static om/IQuery
  (query [this]
    [:query/business-model])
  Object
  (initLocalState [this]
    {:model {:fixed/visitors   "2000"
             :fixed/businesses "20"
             :fixed/days       (str b/days-per-month)}})
  (componentDidMount [this]
    (let [charts-by-graph-key
          (into {} (map-indexed
                     (fn [id graph-key]
                       #?(:cljs
                          (let [create-graph-fn (memoize
                                                  #(create-chart (str "line-chart-" id)
                                                                 (adjusted-business-model this)
                                                                 (get (graphs this) graph-key)))]
                            (.addGraph js/nv create-graph-fn)
                            ;; returns the graph-key associated with the chart object returned
                            ;; in create-graph-fn. It's memoized, so it'll just return when we call
                            ;; it here.
                            [graph-key (create-graph-fn)]))))
                graph-order)]
      (om/update-state! this assoc :charts charts-by-graph-key)))
  (componentDidUpdate [this _ prev-state]
    (when (not= (:model prev-state) (:model (om/get-state this)))
      (doall
        (map-indexed (fn [id graph-key]
                       (update-chart (str "line-chart-" id)
                                     (adjusted-business-model this)
                                     (get (graphs this) graph-key)
                                     (get-in (om/get-state this) [:charts graph-key])))
                     graph-order))))
  (render [this]
    (dom/div #js {:className "row small-up-2"
                  :style     #js {:height "350px"}}
             (dom/div #js {:id        "line-chart-0"
                           :className "column"
                           :style     #js {:height "100%"}})
             (dom/div #js {:id        "line-chart-1"
                           :className "column"
                           :style     #js {:height "100%"}})
             (dom/div #js {:id        "line-chart-2"
                           :className "column"
                           :style     #js {:height "100%"}})
             (dom/div #js {:className "column"
                           :style     #js {:height "100%"}}
                      (dom/p nil "Controls normalized to USD per Day where applicable")
                      (render-input-controls this)))))

(def ->Business (om/factory Business))
