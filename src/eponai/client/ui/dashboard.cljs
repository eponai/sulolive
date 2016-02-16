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
            [eponai.client.report :as report]
            [eponai.client.ui.utils :as utils]))

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

(defn chart-function [component input-function]
  (let [function-id (:report.function/id input-function)]
    [:div
     (opts {:style {:width "50%"}})
     [:h6 "Calculate"]
     [:button
      {:class    (button-class function-id :report.function.id/sum)
       :on-click #(om/update-state! component assoc-in [:input-function :report.function/id] :report.function.id/sum)}

      [:span "Sum"]]
     [:button
      {:class    (button-class function-id :report.function.id/mean)
       :on-click #(om/update-state! component assoc-in [:input-function :report.function/id] :report.function.id/mean)}
      [:span
       "Mean"]]]))

(defn chart-group-by [component {:keys [report/group-by]} groups]
  [:div
   (opts {:style {:width "50%"}})
   [:h6 "Group by"]
   [:div
    (map
      (fn [k]
        (let [conf {:transaction/tags     {:icon "fa fa-tag"
                                           :text "All Tags"}
                    :transaction/currency {:icon "fa fa-usd"
                                           :text "All Currencies"}
                    :transaction/date     {:icon "fa fa-calendar"
                                           :text "All Dates"}}]
          [:button
           (opts {:key [k]
                  :class    (button-class group-by k)
                  :on-click #(om/update-state! component assoc-in [:input-report :report/group-by] k)})
           [:i
            (opts {:key [(get-in conf [k :icon])]
                   :class (get-in conf [k :icon])
                   :style {:margin-right 5}})]
           [:span
            (opts {:key [(get-in conf [k :text])]})
            (get-in conf [k :text])]]))
      groups)]])


(defn chart-settings [component {:keys [input-graph input-function input-report] :as state}]
  (let [{:keys [graph/style]} input-graph]
    [:div
     [:h5 "Configuration"]
     (cond
       (= style :graph.style/bar)
       [:div
        (opts {:style {:display        "flex"
                       :flex-direction "row"}})
        (chart-function component input-function)
        (chart-group-by component input-report [:transaction/tags :transaction/currency])]

       (= style :graph.style/area)
       [:div
        (opts {:style {:display        "flex"
                       :flex-direction "row"}})
        (chart-function component input-function)
        (chart-group-by component input-report [:transaction/date])]

       (= style :graph.style/number)
       [:div
        (opts {:style {:display        "flex"
                       :flex-direction "row"}})
        (chart-function component input-function)])]))

(defn header []
  "Add new widget")

(defui NewWidget
  Object
  (initLocalState [_]
    {:input-graph    {:graph/style :graph.style/bar}
     :input-function {:report.function/id :report.function.id/sum}
     :input-report   {:report/group-by :transaction/tags}})

  (select-graph-style [this style]
    (let [default-group-by {:graph.style/bar    :transaction/tags
                            :graph.style/area   :transaction/date
                            :graph.style/number :default}]
      (om/update-state! this
                        #(-> %
                             (assoc-in [:input-graph :graph/style] style)
                             (assoc-in [:input-report :report/group-by] (get default-group-by style))))))
  (render [this]
    (let [{:keys [input-graph] :as state} (om/get-state this)

          {:keys [on-close
                  on-save]} (om/get-computed this)]
      (html
        [:div
         [:div.modal-header
          [:button.close
           {:on-click on-close}
           "x"]
          [:h4 "New widget"]]
         [:div.modal-body
          [:h4
           (opts {:style {:margin-right "1em"}})
           "Graph"]
          
          (let [style (:graph/style input-graph)]

            [:div
             (opts {:style {:display        "flex"
                            :flex-direction "row"
                            :align-items    :center}})

             [:div.btn-group
              [:button
               (opts {:class    (button-class style :graph.style/bar)
                      :on-click #(.select-graph-style this :graph.style/bar)})
               "Bar Chart"]
              [:button
               (opts {:class    (button-class style :graph.style/area)
                      :on-click #(.select-graph-style this :graph.style/area)})
               "Area Chart"]
              [:button
               (opts {:class    (button-class style :graph.style/number)
                      :on-click #(.select-graph-style this :graph.style/number)})
               "Number"]]])
          (chart-settings this state)
          [:div (str "State: " state)]]
         [:div.modal-footer
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
                              (on-save (-> state
                                           (dissoc :on-close)))
                              (on-close))
                  :style    {:margin 5}})
           "Save"]]]))))

(def ->NewWidget (om/factory NewWidget))

(defui Widget
  static om/IQuery
  (query [_]
    [:widget/uuid
     :widget/width
     :widget/height
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
      (om/update-state! this assoc :report-data (data-report (:graph/report graph)))))
  (componentDidMount [this]
    (let [{:keys [widget/graph]} (om/props this)
          {:keys [data-report]} (om/get-computed this)]
      (om/update-state! this assoc :report-data (data-report (:graph/report graph)))))
  (render [this]
    (let [{:keys [widget/graph] :as widget} (om/props this)
          {:keys [report-data]} (om/get-state this)
          {:keys [on-delete]} (om/get-computed this)]
      (html
        [:div.widget
         (opts {:style {:border        "1px solid #e7e7e7"
                        :border-radius "0.5em"
                        :padding       "50px 0 0 0"
                        :width         "50%"
                        :height        300
                        :position      :relative
                        :box-sizing    :border-box}})
         [:div
          (opts {:style {:position :absolute
                         :top      0
                         :height   50
                         :width "100%"
                         :display :flex
                         :flex-direction :row
                         :justify-content :space-between
                         :align-items :flex-start}})
          [:p
           (str "Data: " report-data)]
          [:a.close
           {:on-click #(on-delete widget)}
           "x"]]
         (let [{:keys [graph/style]} graph
               settings {:data         report-data
                         :id           (str (:widget/uuid widget))
                         :width        "100%"
                         :height       "100%"
                         :title-axis-y "Amount ($)"}]
           (cond (= style :graph.style/bar)
                 (d3/->BarChart settings)

                 (= style :graph.style/area)
                 (d3/->AreaChart settings)

                 (= style :graph.style/number)
                 (d3/->NumberChart settings)))]))))

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
                                                                   {:transaction/date [:date/ymd
                                                                                       :date/timestamp]}]}]}]}])
  Object
  (initLocalState [_]
    {:add-widget true})
  (data-report [this report]
    (let [{:keys [query/dashboard]} (om/props this)
          transactions (-> dashboard
                           :dashboard/budget
                           :transaction/_budget)]
      (report/create report transactions)))

  (show-add-widget [this visible]
    (om/update-state! this assoc :add-widget visible))

  (save-widget [this widget]
    (om/transact! this `[(widget/save ~widget)
                         :query/dashboard]))
  (delete-widget [this widget]
    (om/transact! this `[(widget/delete ~(select-keys widget [:widget/uuid]))
                         :query/dashboard]))

  (render [this]
    (let [{:keys [query/dashboard]} (om/props this)
          {:keys [add-widget]} (om/get-state this)]
      (prn "Dashboard; " dashboard)
      (html
        [:div
         (opts {:style {:position :relative
                        :box-sizing :border-box}})

         [:button
          {:class    "btn btn-default btn-md"
           :on-click #(.show-add-widget this true)}
          "New widget"]
         (when add-widget
           (utils/modal {:content (->NewWidget (om/computed {}
                                                            {:on-save #(.save-widget this (assoc % :input-dashboard dashboard))
                                                             :on-close #(.show-add-widget this false)}))
                         :on-close #(.show-add-widget this false)}))
         ;[:a
         ; {:class "btn btn-default btn-md"
         ;  :href  (routes/inside "/widget/new")}
         ; "Edit"]

         (map
           (fn [widget-props]
             (->Widget
               (om/computed widget-props
                            {:data-report #(.data-report this %)
                             :on-delete #(.delete-widget this %)})))
           (:dashboard/widgets dashboard))]))))

(def ->Dashboard (om/factory Dashboard))
