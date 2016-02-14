(ns eponai.client.ui.dashboard
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [eponai.client.ui :refer [map-all] :refer-macros [style opts]]
            [eponai.client.ui.d3 :as d3]
            [cljs.core.async :as c :refer [chan]]
            [garden.core :refer [css]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [eponai.client.routes :as routes]
            [eponai.client.ui.tag :as tag]))

(defn sum-by-day [transactions]
  (let [grouped (group-by :transaction/date transactions)
        sum-fn (fn [[day ts]]
                 (assoc day :date/sum (reduce #(+ %1 (:transaction/amount %2)) 0 ts)))]
    (map sum-fn grouped)))

(defn sum-by-tag [transactions]
  (let [sum-fn (fn [m transaction]
                 (let [tags (:transaction/tags transaction)]
                   (reduce (fn [m2 tagname]
                             (update m2 tagname
                                     #(if %
                                       (+ % (:transaction/amount transaction))
                                       (:transaction/amount transaction))))
                           m
                           (if (empty? tags)
                             ["no tags"]
                             (map :tag/name tags)))))]
    (reduce sum-fn {} transactions)))

(defn on-add-tag-key-down [this input-tag]
  (fn [e]
    (when (and (= 13 (.-keyCode e))
               (seq (.. e -target -value)))
      (.preventDefault e)
      (om/update-state! this #(-> %
                               (update :input-tags conj input-tag)
                               (assoc :input-tag ""))))))

(defn button-class [field value]
  (if (= field value)
    "btn btn-info btn-md"
    "btn btn-default btn-md"))

(defui ChartCategories
  Object
  (initLocalState [_]
    {:input-tags []})
  (render [this]
    (let [{:keys [input-category]} (om/get-state this)]
      (html
        [:div
         [:h6 "Categories"]
         [:div
          [:button
           {:class (button-class input-category :category-tags)
            :on-click #(om/update-state! this assoc :input-category :category-tags)}
           [:i
            (opts {:class "fa fa-tag"
                   :style {:margin-right 5}})]
           [:span "All Tags"]]
          [:button
           {:class (button-class input-category :category-currencies)
            :on-click #(om/update-state! this assoc :input-category :category-currencies)}
           [:i
            (opts {:class "fa fa-usd"
                   :style {:margin-right 5}})]
           [:span "All Currencies"]]
          [:button
           {:class (button-class input-category :category-locations)
            :on-click #(om/update-state! this assoc :input-category :category-locations)}
           [:i
            (opts {:class "fa fa-map-marker"
                   :style {:margin-right 5}})]
           [:span
            "All Locations"]]]
         [:h6 "or"]
         [:p (str input-category)]
         [:button
          {:class (button-class input-category :category-custom)
           :on-click #(om/update-state! this assoc :input-category :category-custom)}
          [:span
           "Custom..."]]]))))

(def ->ChartCategories (om/factory ChartCategories))

(defui ChartCalculation
  Object
  (render [this]
    (let [{:keys [input-calc
                  input-value-type]} (om/get-state this)]
      (html
        [:div
         [:div
          [:button
           {:class    (button-class input-calc :calc-sum)
            :on-click #(om/update-state! this assoc :input-calc :calc-sum)}

           [:span "Sum"]]
          [:button
           {:class    (button-class input-calc :calc-mean)
            :on-click #(om/update-state! this assoc :input-calc :calc-mean)}
           [:span
            "Mean"]]]

         [:div
          [:h6 "Show values"]
          [:button
           {:class    (button-class input-value-type :value-type-amount)
            :on-click #(om/update-state! this assoc :input-value-type :value-type-amount)}
           "$"]
          [:button
           {:class    (button-class input-value-type :value-type-percent)
            :on-click #(om/update-state! this assoc :input-value-type :value-type-percent)}
           "%"]]

         [:div (str "Calculation: " input-calc)]
         [:div (str "Value type " input-value-type)]]))))

(def ->ChartCalculation (om/factory ChartCalculation))

(defui BarChartSettings
  Object
  (initLocalState [_]
    {:chart-style :vertical})
  (render [this]
    (let [{:keys [chart-style]} (om/get-state this)]
      (html
        [:div
         [:h5 "Configuration"]
         [:h6 "Appearance"]
         [:div
          [:button
           (opts {:class (button-class chart-style :vertical)
                  :on-click #(om/update-state! this assoc :chart-style :vertical)})
           "Vertical"]
          [:button
           (opts {:class (button-class chart-style :horizontal)
                  :on-click #(om/update-state! this assoc :chart-style :horizontal)})
           "Horizontal"]]

         (when chart-style
           [:div
            (opts {:style {:display "flex"
                           :flex-direction "row"}})
            [:div
             (opts {:style {:width "50%"}})
             [:h6 "X-axis"]
             (if (= chart-style :vertical)
               (->ChartCategories)
               (->ChartCalculation))]
            [:div
             (opts {:style {:width "50%"}})
             [:h6 "Y-axis"]
             (if (= chart-style :vertical)
               (->ChartCalculation)
               (->ChartCategories))]])]))))

(def ->BarChartSettings (om/factory BarChartSettings))

(defui ChartSettings
  Object
  (render [this]
    (let [{:keys [graph]} (om/props this)]

      (html
        [:div
         (cond
           (= graph :graph1)
           (->BarChartSettings)

           (= graph :graph2)
           [:div
            "Area chart settings"]

           (= graph :graph3)
           [:div
            "Number chart settings"])]))))

(def ->ChartSettings (om/factory ChartSettings))

(defui NewWidget
  Object
  (initLocalState [_]
    {:graph nil})
  (render [this]
    (let [{:keys [graph]} (om/get-state this)
          {:keys [on-close]} (om/props this)]
      (html
        [:div.overlay
         (opts {:style {:position         :absolute
                        :top              0
                        :left             0
                        :width            "100%"
                        :height           "100%"
                        :z-index          10
                        :background-color "rgba(255,255,255,0.95)"}})
         [:div
          (opts {:style {:display         "flex"
                         :flex-direction  "row"
                         :justify-content "space-between"}})
          [:h3 "Add new widget"]
          [:button
           {:class "close"
            :on-click on-close}
           "X"]]
         [:hr]
         [:h4 "Graph"]
         [:div
          (opts {:style {:display "flex"
                         :flex-direction "row"}})
          [:button
           (opts {:class (button-class graph :graph1)
                  :style {:margin 10}
                  :on-click #(om/update-state! this assoc :graph :graph.style/bar)})
           "Bar Chart"]
          [:button
           (opts {:class (button-class graph :graph2)
                  :style {:margin 10}
                  :on-click #(om/update-state! this assoc :graph :graph.style/area)})
           "Area Chart"]
          [:button
           (opts {:class (button-class graph :graph3)
                  :style {:margin 10}
                  :on-click #(om/update-state! this assoc :graph :graph.style/number)})
           "Number"]]
         [:hr]
         (->ChartSettings {:graph graph})
         [:hr]
         [:div
          (opts {:style {:display        "flex"
                         :flex-direction "row-reverse"}})
          [:button
           (opts
             {:class    "btn btn-default btn-md"
              :on-click on-close
              :style    {:margin 5}})
           "Cancel"]
          [:button
           (opts {:class    "btn btn-info btn-md"
                  :on-click on-close
                  :style    {:margin 5}})
           "Save"]]
         ]))))

(def ->NewWidget (om/factory NewWidget))

(defui Dashboard
  static om/IQuery
  (query [_]
    ['{:query/one-budget [:budget/uuid
                          {:transaction/_budget
                           [:transaction/uuid
                            {:transaction/date
                             [:date/ymd
                              :date/timestamp]}
                            :transaction/amount
                            {:transaction/tags [:tag/name]}]}]}])
  Object
  (initLocalState [_]
    {:add-new true})
  (render [this]
    (let [{:keys [query/one-budget]} (om/props this)
          {:keys [add-new]} (om/get-state this)
          sum-by-day (sum-by-day (:transaction/_budget one-budget))
          sum-by-tag (sum-by-tag (:transaction/_budget one-budget))]
      (html
        [:div
         (opts {:style {:position :relative}})
         (when add-new
           (->NewWidget {:on-close #(om/update-state! this assoc :add-new false)}))
         [:button
          {:class "btn btn-default btn-md"
           :on-click #(om/update-state! this assoc :add-new true)}
          "New widget"]
         [:a
          {:class "btn btn-default btn-md"
           :href  (routes/inside "/widget/new")}
          "Edit"]
         [:p [:span "This is the dashboard for budget "]
          [:strong (str (:budget/uuid one-budget))]]
         (d3/->AreaChart {:data         [{:key    "All Transactions"
                                          :values (reduce #(conj %1
                                                                 {:name (:date/timestamp %2) ;date timestamp
                                                                  :value (:date/sum %2)}) ;sum for date
                                                          []
                                                          (sort-by :date/timestamp sum-by-day))}]
                          :width        "100%"
                          :height       400
                          :title-axis-y "Amount ($)"})
         (d3/->BarChart {:data         [{:key    "All Transactions"
                                         :values (reduce #(conj %1 {:name  (first %2) ;tag name
                                                                    :value (second %2)}) ;sum for tag
                                                         []
                                                         sum-by-tag)}]
                         :width        "100%"
                         :height       400
                         :title-axis-y "Amount ($)"})]))))

(def ->Dashboard (om/factory Dashboard))
