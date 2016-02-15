(ns eponai.client.ui.dashboard
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [eponai.client.ui :refer [map-all] :refer-macros [style opts]]
            [eponai.client.ui.d3 :as d3]
            [cljs.core.async :as c :refer [chan]]
            [garden.core :refer [css]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [eponai.client.routes :as routes]
            [eponai.client.ui.tag :as tag]
            [eponai.client.report :as report]))

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
  Object
  (initLocalState [_]
    {:input-graph    {:graph/style nil}
     :input-function {:report.function/id :report.function.id/sum}
     :input-report   {:report/group-by :transaction/tags}})
  (render [this]
    (let [{:keys [input-graph] :as state} (om/get-state this)
          {:keys [dashboard
                  on-close
                  save-widget]} (om/get-computed this)]
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
                              (save-widget (-> state
                                               (dissoc :on-close)
                                               (assoc :input-dashboard dashboard)))
                              (on-close))
                  :style    {:margin 5}})
           "Save"]]]))))

(def ->NewWidget (om/factory NewWidget {:keyfn #(-> % :dashboard :dashboard/uuid)}))

(defui Widget
  static om/IQuery
  (query [_]
    [:widget/uuid
     {:widget/graph [:graph/style
                     {:graph/report [:report/uuid
                                     :report/group-by
                                     {:report/functions [:report.function/uuid
                                                         :report.function/attribute
                                                         :report.function/id]}]}]}])

  Object
  (componentWillReceiveProps [this new-props]
    (let [{:keys [widget/graph]} new-props
          {:keys [data-report]} (om/get-computed this)]
      (om/update-state! this assoc :report-data (data-report this (:graph/report graph)))))
  (componentDidMount [this]
    (let [{:keys [widget/graph]} (om/props this)
          {:keys [data-report]} (om/get-computed this)]
      (om/update-state! this assoc :report-data (data-report this (:graph/report graph)))))
  (render [this]
    (let [{:keys [widget/graph] :as widget} (om/props this)
          {:keys [report-data]} (om/get-state this)]
      (html
        [:div
         (str "Data: " report-data)
         (let [{:keys [graph/style]} graph]
           (cond (= style :graph.style/bar)
                 (d3/->BarChart {:data         report-data
                                 :id         (str (:widget/uuid widget))
                                 :width        300
                                 :height       50
                                 :title-axis-y "Amount ($)"})))]))))

(def ->Widget (om/factory Widget))

(defui Dashboard
  static om/IQuery
  (query [_]
    [{:query/dashboard [:dashboard/uuid
                        {:dashboard/widgets (om/get-query Widget)}
                        {:dashboard/budget [:budget/uuid
                                            {:transaction/_budget [:transaction/uuid
                                                                   {:transaction/tags [:tag/name]}
                                                                   :transaction/amount
                                                                   :transaction/date]}]}]}])
  Object
  (initLocalState [_]
    {:add-new false})
  (data-report [this report]
    (let [{:keys [query/dashboard]} (om/props this)
          transactions (-> dashboard
                           :dashboard/budget
                           :transaction/_budget)]
      (report/create report transactions)))
  (render [this]
    (let [{:keys [query/dashboard]} (om/props this)
          {:keys [add-new]} (om/get-state this)]
      (html
        [:div
         (opts {:style {:position :relative}})
         (when add-new
           (->NewWidget (om/computed {}
                                     {:on-close    #(om/update-state! this assoc :add-new false)
                                      :dashboard   dashboard
                                      :save-widget (fn [input-widget]
                                                     (om/transact! this `[(widget/save ~input-widget)]))})))
         [:button
          {:class "btn btn-default btn-md"
           :on-click #(om/update-state! this assoc :add-new true)}
          "New widget"]
         [:a
          {:class "btn btn-default btn-md"
           :href  (routes/inside "/widget/new")}
          "Edit"]

         (prn "Widgets: " (:dashboard/widgets dashboard))
         (map
           (fn [props]
             (->Widget
               (om/computed props
                            {:data-report #(.data-report this %)})
               ))
           (:dashboard/widgets dashboard))
         ;[:p [:span "This is the dashboard for budget "]
          ;[:strong (str (:budget/uuid one-budget))]]
         ;(d3/->AreaChart {:data         [{:key    "All Transactions"
         ;                                 :values (reduce #(conj %1
         ;                                                        {:name (:date/timestamp %2) ;date timestamp
         ;                                                         :value (:date/sum %2)}) ;sum for date
         ;                                                 []
         ;                                                 (sort-by :date/timestamp sum-by-day))}]
         ;                 :width        "100%"
         ;                 :height       400
         ;                 :title-axis-y "Amount ($)"})
         ;(d3/->BarChart {:data         [{:key    "All Transactions"
         ;                                :values (reduce #(conj %1 {:name  (first %2) ;tag name
         ;                                                           :value (second %2)}) ;sum for tag
         ;                                                []
         ;                                                sum-by-tag)}]
         ;                :width        "100%"
         ;                :height       400
         ;                :title-axis-y "Amount ($)"})
         ]))))

(def ->Dashboard (om/factory Dashboard))
