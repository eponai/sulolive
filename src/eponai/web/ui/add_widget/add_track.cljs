(ns eponai.web.ui.add-widget.add-track
  (:require
    [eponai.client.ui :refer [update-query-params!]]
    [eponai.common.report :as report]
    [eponai.web.ui.add-widget.chart-settings :refer [->ChartSettings]]
    [eponai.web.ui.add-widget.select-graph :refer [->SelectGraph]]
    [eponai.web.ui.utils.filter :as filter]
    [eponai.web.ui.widget :refer [->Widget]]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug]]))

(defn- change-graph-style [widget style]
  (let [default-group-by {:graph.style/bar    :transaction/tags
                          :graph.style/area   :transaction/date
                          :graph.style/line   :transaction/date
                          :graph.style/number :default}]
    (-> widget
        (assoc-in [:widget/graph :graph/style] style)
        (update-in [:widget/report :report/track :track/functions]
                   (fn [fns]
                     (map (fn [f]
                            (assoc f
                              :track.function/id :track.function.id/sum
                              :track.function/group-by (get default-group-by style)))
                          fns))))
    ;(om/update-state! component
    ;                  #(-> %
    ;                       (assoc-in [:input-graph :graph/style] style)
    ;                       (assoc-in [:input-report :report/track :track/functions] [{:track.function/id :track.function.id/sum
    ;                                                                                  :track.function/group-by (get default-group-by style)}])))
    ))

(defui NewTrack
  Object
  (initLocalState [this]
    {:step 1})
  (set-filters [this props]
    (let [{:keys [widget]} (::om/computed props)]
      (om/update-state! this assoc
                        :tag-filter (select-keys (:widget/filter widget) [:filter/include-tags
                                                                          :filter/exclude-tags])
                        :date-filter (select-keys (:widget/filter widget) [:filter/start-date
                                                                           :filter/end-date
                                                                           :filter/last-x-days]))))
  (get-filters [this]
    (let [{:keys [tag-filter date-filter]} (om/get-state this)]
      (merge tag-filter date-filter)))
  (componentDidMount [this]
    (.set-filters this (om/props this)))
  (componentWillReceiveProps [this new-props]
    (.set-filters this new-props))

  (render [this]
    (let [{:keys [widget
                  transactions
                  on-change]} (om/get-computed this)
           {:keys [step
                   tag-filter
                   date-filter]} (om/get-state this)
          {:keys [widget/graph]} widget]
      (html
        [:div
         [:ul.breadcrumbs
          [:li
           [:a.disabled
            (if (:db/id widget)
              "Edit Track"
              "New Track")]]
          (if (= 1 step)
            [:li
             [:span "Select Graph"]]
            [:li
             [:a
              {:on-click #(om/update-state! this assoc :step 1)}
              [:span "Select Graph"]]])
          (if (= 2 step)
            [:li
             [:span "Data set"]]
            [:li
             [:a
              {:on-click #(om/update-state! this assoc :step 2)}
              [:span "Data set"]]])]

         (cond (= step 1)
               (->SelectGraph (om/computed {:graph (:widget/graph widget)}
                                           {:on-change    #(when on-change
                                                            (on-change (change-graph-style widget %)))
                                            :transactions transactions
                                            :on-next      #(om/update-state! this assoc :step 2)
                                            :styles       [:graph.style/bar
                                                           :graph.style/area
                                                           :graph.style/line
                                                           :graph.style/number]}))

               (= step 2)
               [:div
                [:div.row.column.small-12.medium-6
                 ;(opts {:style {:height "15em"}})
                 (->Widget (assoc widget :widget/data (report/generate-data (:widget/report widget) transactions)))]

                [:fieldset.fieldset
                 [:legend "Settings"]
                 (->ChartSettings (om/computed {}
                                               {:graph        graph
                                                :report       (:widget/report widget)
                                                :transactions transactions
                                                :on-change    #(when on-change
                                                                (on-change (assoc widget :widget/report %)))}))
                 ;[:fieldset
                 ; [:legend "Filters"]
                 ; [:div.row
                 ;  [:div.columns.small-12.large-3
                 ;   (filter/->TagFilter (om/computed {:tags (get-in graph-filter [:tag-filter :filter/include-tags])}
                 ;                                    {:on-change #(do
                 ;                                                  (om/update-state! this assoc-in [:graph-filter :tag-filter] {:filter/include-tags %}))}))]
                 ;  [:div.columns.small-12.large-9
                 ;   (filter/->DateFilter (om/computed {:filter (:date-filter graph-filter)}
                 ;                                     {:on-change #(do
                 ;                                                   (om/update-state! this assoc-in [:graph-filter :date-filter] %))}))]]]
                 ]
                [:fieldset.fieldset
                 [:legend "Dataset"]
                 [:div.row
                  [:div.columns.small-12.medium-3
                   "Transactions with tags"]
                  [:div.columns.small-12.medium-9
                   "Filter date"]]
                 [:div.row
                  [:div.columns.small-12.medium-3
                   (filter/->TagFilter (om/computed {:tags (:filter/include-tags tag-filter)}
                                                    {:on-change (fn [tags]
                                                                  (let [new-tag-filters {:filter/include-tags tags}
                                                                        new-filters (merge new-tag-filters date-filter)]
                                                                    (om/update-state! this assoc :tag-filter new-tag-filters)
                                                                    (on-change (assoc widget :widget/filter new-filters) {:update-data? true})))}))]

                  [:div.columns.small-12.medium-9
                   (filter/->DateFilter (om/computed {:filter date-filter}
                                                     {:on-change #(let [new-filters (merge tag-filter %)]
                                                                   (om/update-state! this assoc :date-filter %)
                                                                   (on-change (assoc widget :widget/filter new-filters) {:update-data? true}))}))]]]])

         ]))))

(def ->NewTrack (om/factory NewTrack))
