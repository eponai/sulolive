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

(defn chart-group-by [component input-report]
  (let [group-by (:report/group-by input-report)]
    [:div
     [:h6 "Categories"]
     [:div
      [:button
       {:class    (button-class group-by :transaction/tags)
        :on-click #(om/update-state! component assoc-in [:input-report :report/group-by] :transaction/tags)}
       [:i
        (opts {:class "fa fa-tag"
               :style {:margin-right 5}})]
       [:span "All Tags"]]
      [:button
       {:class    (button-class group-by :transaction/currency)
        :on-click #(om/update-state! component assoc-in [:input-report :report/group-by] :transaction/currency)}
       [:i
        (opts {:class "fa fa-usd"
               :style {:margin-right 5}})]
       [:span "All Currencies"]]]]))

(defn chart-function [component input-function]
  (let [function-id (:report.function/id input-function)]
    [:div
     [:button
      {:class    (button-class function-id :report.function.id/sum)
       :on-click #(om/update-state! component assoc-in [:input-function :report.function/id] :report.function.id/sum)}

      [:span "Sum"]]
     [:button
      {:class    (button-class function-id :report.function.id/mean)
       :on-click #(om/update-state! component assoc-in [:input-function :report.function/id] :report.function.id/mean)}
      [:span
       "Mean"]]]))

(defn bar-chart-settings [component {:keys [input-report input-function]}]
  [:div
   [:h5 "Configuration"]
   [:div
    (opts {:style {:display        "flex"
                   :flex-direction "row"}})
    [:div
     (opts {:style {:width "50%"}})
     [:h6 "X-axis"]
     (chart-group-by component input-report)
     ]
    [:div
     (opts {:style {:width "50%"}})
     [:h6 "Y-axis"]
     (chart-function component input-function)]]])

(defn chart-settings [component {:keys [input-graph] :as state}]
  (let [{:keys [graph/style]} input-graph]
    [:div
     (cond
       (= style :graph.style/bar)
       (bar-chart-settings component state)

       (= style :graph.style/area)
       [:div
        "Area chart settings"]

       (= style :graph.style/number)
       [:div
        "Number chart settings"])]))

(defui NewWidget
  static om/IQuery
  (query [_]
    [])
  Object
  (initLocalState [this]
    (let [{:keys [on-close]} (om/props this)]
      {:on-close on-close
       :input-graph    {:graph/style nil}
       :input-function {:report.function/id :report.function.id/sum}
       :input-report   {:report/group-by :transaction/tags}}))
  (render [this]
    (let [{:keys [input-graph
                  on-close] :as state} (om/get-state this)]
      (prn "state : " state)
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
         (let [style (:graph/style input-graph)]

           [:div
            (opts {:style {:display "flex"
                         :flex-direction "row"}})
            [:button
             (opts {:class    (button-class style :graph.style/bar)
                    :style    {:margin 10}
                    :on-click #(om/update-state! this assoc-in [:input-graph :graph/style] :graph.style/bar)})
             "Bar Chart"]
            [:button
             (opts {:class    (button-class style :graph.style/area)
                    :style    {:margin 10}
                    :on-click #(om/update-state! this assoc-in [:input-graph :graph/style] :graph.style/area)})
             "Area Chart"]
            [:button
             (opts {:class    (button-class style :graph.style/number)
                    :style    {:margin 10}
                    :on-click #(om/update-state! this assoc-in [:input-graph :graph/style] :graph.style/number)})
             "Number"]])
         [:hr]
         (chart-settings this state)
         [:hr]
         [:div (str "State: " state)]
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
                  :on-click (fn []
                              (prn "on-close: " on-close)
                              (om/transact! this `[(widget/save ~state)])
                              (on-close))
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
