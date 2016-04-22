(ns eponai.web.ui.add-widget.add-track
  (:require
    [eponai.client.ui :refer [update-query-params!] :refer-macros [opts]]
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
    {:step 1
     :selected-transactions :include-tags})
  (set-filters [this props]
    (let [{:keys [widget]} (::om/computed props)]
      (om/update-state! this assoc
                        :tag-filter (select-keys (:widget/filter widget) [:filter/include-tags
                                                                          :filter/exclude-tags])
                        :graph-filter (select-keys (get-in widget [:widget/graph :graph/filter]) [:filter/include-tags
                                                                                                  :filter/exclude-tags])
                        :date-filter (select-keys (:widget/filter widget) [:filter/start-date
                                                                           :filter/end-date
                                                                           :filter/last-x-days]))))
  (get-filters [this]
    (let [{:keys [tag-filter date-filter]} (om/get-state this)]
      (merge tag-filter date-filter)))

  (update-selected-transactions [this selected]
    (let [{:keys [widget
                  on-change]} (om/get-computed this)]
      (when (= selected :all-transactions)
        (on-change (assoc widget :widget/filter {}) {:update-data? true}))
      (om/update-state! this (fn [st]
                               (cond-> (assoc st :selected-transactions selected)

                                       (= selected :all-transactions)
                                       (assoc :tag-filter {}))))))
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
                   date-filter
                   graph-filter
                   selected-transactions]} (om/get-state this)
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
             [:span "Style"]]
            [:li
             [:a
              {:on-click #(om/update-state! this assoc :step 1)}
              [:span "Style"]]])
          (if (= 2 step)
            [:li
             [:span "Settings"]]
            [:li
             [:a
              {:on-click #(om/update-state! this assoc :step 2)}
              [:span "Settings"]]])]

         (cond (= step 1)
               (->SelectGraph (om/computed {:graph (:widget/graph widget)}
                                           {:on-change    #(when on-change
                                                            (on-change (change-graph-style widget %)))
                                            :transactions transactions
                                            :on-next      #(om/update-state! this assoc :step 2)
                                            :styles       [:graph.style/bar
                                                           :graph.style/line
                                                           :graph.style/number]}))

               (= step 2)
               [:div
                ;[:fieldset.fieldset
                ; [:legend "Select transactions"]]
                [:h4 "Settings"]

                (when (some #{:graph.style/area :graph.style/line} [(:graph/style graph)])
                  [:div.row.column.small-12.medium-6
                   [:select
                    {:value (str (:graph/style graph))
                     :on-change #(when on-change
                                  (on-change (change-graph-style widget (keyword (subs (.-value (.-target %)) 1)))))}
                    [:option
                     {:value (str :graph.style/line)}
                     "Line"]
                    [:option
                     {:value (str :graph.style/area)}
                     "Area"]]])
                [:div.row
                 [:div.columns.small-12.medium-6
                  ;(opts {:style {:height "15em"}})
                  (->Widget (assoc widget :widget/data (report/generate-data (:widget/report widget) transactions {:data-filter (get-in widget [:widget/graph :graph/filter])})))]

                 [:div.columns.small-12.medium-6
                  [:fieldset.fieldset
                   (opts {:style {:margin 0}})
                   [:legend "Transactions"]
                   [:div.row
                    ;[:div.columns.small-2.text-right
                    ; [:label "Transactions:"]]
                    [:div.columns.small-10
                     [:select
                      {:value     (name selected-transactions)
                       :on-change #(.update-selected-transactions this (keyword (.-value (.-target %))))}
                      [:option
                       {:value (name :all-transactions)}
                       "All transactions"]
                      [:option
                       {:value (name :include-tags)}
                       "Transactions with tags"]]
                     (when (= selected-transactions :include-tags)
                       (filter/->TagFilter (om/computed {:tags        (:filter/include-tags tag-filter)
                                                         :placeholder "Hit enter to add tag..."}
                                                        {:on-change (fn [tags]
                                                                      (let [new-filters (if (seq tags)
                                                                                          (merge tag-filter {:filter/include-tags tags})
                                                                                          (dissoc tag-filter :filter/include-tags))]
                                                                        (om/update-state! this assoc :tag-filter new-filters)
                                                                        (on-change (assoc widget :widget/filter new-filters) {:update-data? true})))})))]]]
                  ;[:fieldset.fieldset
                  ; [:legend "Settings"]]
                  ;(->ChartSettings (om/computed {}
                  ;                              {:graph        graph
                  ;                               :report       (:widget/report widget)
                  ;                               :transactions transactions
                  ;                               :on-change    #(when on-change
                  ;                                               (on-change (assoc widget :widget/report %)))}))
                  ]]
                [:fieldset
                 [:legend "Filters"]
                 [:div.row
                  [:div.columns.small-12.medium-3
                   "Time"]]
                 [:div.row
                  [:div.columns.small-12.medium-9
                   (filter/->DateFilter (om/computed {:filter date-filter}
                                                     {:on-change #(let [new-filters (merge date-filter %)]
                                                                   (om/update-state! this assoc :date-filter %)
                                                                   (on-change (assoc widget :widget/filter new-filters) {:update-data? true}))}))]]
                 (when (= :graph.style/bar (:graph/style graph))

                   [:div.row
                    [:div.columns.small-12.medium-3
                     "Show tags"]
                    [:div.columns.small-12.medium-3.end
                     "Hide tags"]])
                 (when (= :graph.style/bar (:graph/style graph))
                   [:div.row
                    [:div.columns.small-12.medium-3
                     (filter/->TagFilter (om/computed {:tags        (:filter/include-tags graph-filter)
                                                       :placeholder "Hit enter to add tag..."}
                                                      {:on-change (fn [tags]
                                                                    (let [new-filters (if (seq tags)
                                                                                        (merge graph-filter {:filter/include-tags tags})
                                                                                        (dissoc graph-filter :filter/include-tags))]
                                                                      (om/update-state! this assoc :graph-filter new-filters)
                                                                      (on-change (assoc-in widget [:widget/graph :graph/filter] new-filters) {:update-data? false})))}))]
                    [:div.columns.small-12.medium-3.end
                     (filter/->TagFilter (om/computed {:tags        (:filter/exclude-tags graph-filter)
                                                       :placeholder "Hit enter to add tag..."}
                                                      {:on-change (fn [tags]
                                                                    (let [new-filters (if (seq tags)
                                                                                        (merge graph-filter {:filter/exclude-tags tags})
                                                                                        (dissoc graph-filter :filter/exclude-tags))]
                                                                      (om/update-state! this assoc :graph-filter new-filters)
                                                                      (on-change (assoc-in widget [:widget/graph :graph/filter] new-filters) {:update-data? false})))}))]])]])

         ]))))

(def ->NewTrack (om/factory NewTrack))
