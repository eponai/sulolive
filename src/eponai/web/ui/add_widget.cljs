(ns eponai.web.ui.add-widget
  (:require [datascript.core :as d]
            [eponai.client.ui :refer [update-query-params! map-all] :refer-macros [opts]]
            [eponai.web.ui.add-widget.select-graph :refer [->SelectGraph SelectGraph]]
            [eponai.web.ui.add-widget.chart-settings :refer [->ChartSettings]]
            [eponai.web.ui.datepicker :refer [->Datepicker]]
            [eponai.web.ui.widget :refer [->Widget Widget]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [eponai.web.ui.utils :as utils]
            [eponai.common.report :as report]
            [taoensso.timbre :refer-macros [debug]]
            [cljs-time.format :as f]
            [eponai.web.routes :as routes]
            [cljs-time.core :as time]
            [cljs-time.coerce :as coerce]
            [eponai.common.format :as format]
            [eponai.web.ui.utils.filter :as filter]))

;;; ####################### Actions ##########################

(defn- select-graph-style [component style]
  (let [default-group-by {:graph.style/bar    :transaction/tags
                          :graph.style/area   :transaction/date
                          :graph.style/line   :transaction/date
                          :graph.style/number :default}]
    (om/update-state! component
                      #(-> %
                           (assoc-in [:input-graph :graph/style] style)
                           (assoc-in [:input-report :report/group-by] (get default-group-by style))))))

(defn- change-report-title [component title]
  (om/update-state! component assoc-in [:input-report :report/title] title))

;;;;; ################### UI components ######################

(defn- button-class [field value]
  (if (= field value)
    "button primary"
    "button secondary"))
;;;;;;;; ########################## Om Next components ########################

(defui NewWidget
  static om/IQueryParams
  (params [_]
    {:filter {:filter/include-tags #{}}})
  static om/IQuery
  (query [_]
    ['{(:query/transactions {:filter ?filter}) [:transaction/uuid
                                                :transaction/amount
                                                :transaction/conversion
                                                {:transaction/currency [:currency/code]}
                                                {:transaction/tags [:tag/name]}
                                                {:transaction/date [:date/ymd
                                                                    :date/timestamp]}]}
     {:query/active-widget (om/get-query Widget)}])
  Object
  (save-widget [this widget]
    (om/transact! this `[(widget/save ~(assoc widget :mutation-uuid (d/squuid)))
                         :query/dashboard]))
  (delete-widget [this widget]
    (om/transact! this `[(widget/delete ~(assoc (select-keys widget [:widget/uuid]) :mutation-uuid (d/squuid)))
                         :query/dashboard]))

  (data-set [this]
    (let [{:keys [data-set]} (om/get-state this)]
      (merge (:tag-filter data-set)
             (:date-filter data-set))))
  (graph-filter [this]
    (let [{:keys [graph-filter]} (om/get-state this)]
      (merge (:tag-filter graph-filter)
             (:date-filter graph-filter))))
  (initLocalState [this]
    (let [{:keys [index]} (om/get-computed this)
          {:keys [query/active-widget]} (om/props this)]
      (if active-widget
        (let [data-set (:widget/filter active-widget)
              graph-filter (get-in active-widget [:widget/graph :graph/filter])]
          (update-query-params! this assoc :filter data-set)
          {:step         2
           :input-graph  (:widget/graph active-widget)
           :input-report (:widget/report active-widget)
           :input-widget (dissoc active-widget :widget/data)
           :data-set     {:date-filter (select-keys data-set [:filter/end-date :filter/start-date :filter/last-x-days])
                          :tag-filter  (select-keys data-set [:filter/include-tags :filter/exclude-tags])}
           :graph-filter {:date-filter (select-keys graph-filter [:filter/end-date :filter/start-date :filter/last-x-days])
                          :tag-filter  (select-keys graph-filter [:filter/include-tags :filter/exclude-tags])}})
        {:step 1
         :input-graph    {:graph/style :graph.style/bar}
         :input-report   {:report/group-by :transaction/tags
                          :report/functions [{:report.function/id :report.function.id/sum}]}
         :input-widget   {:widget/index  index
                          :widget/width  25
                          :widget/height 2}})))
  (render [this]
    (let [{:keys [query/transactions
                  query/active-widget]} (om/props this)
          {:keys [input-graph
                  input-report
                  step
                  data-set
                  graph-filter] :as state} (om/get-state this)
          {:keys [dashboard]} (om/get-computed this)]
      (debug "Add widget render: " active-widget)
      (html
        [:div
         [:ul.breadcrumbs
          [:li.disabled
           [:span (if active-widget "Edit widget" "New widget")]]
          [:li
           (if (> step 1)
             [:a
              {:on-click #(om/update-state! this assoc :step 1)}
              [:span "Select Graph"]]
             [:span "Select Graph"])]
          (cond (> step 1)
                [:li
                 [:span "Data set"]]
                (some? active-widget)
                [:li
                 [:a
                  {:on-click #(om/update-state! this assoc :step 2)}
                  [:span "Data set"]]])]
         (cond (= step 1)
               (->SelectGraph (om/computed {:graph input-graph}
                                           {:on-change    #(select-graph-style this %)
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
                 (->Widget {:widget/graph input-graph
                            :widget/data  (report/generate-data input-report (.graph-filter this) transactions)})]

                [:fieldset.fieldset
                 [:legend "Settings"]
                 (->ChartSettings (om/computed {}
                                               {:graph        input-graph
                                                :report       input-report
                                                :transactions transactions
                                                :on-change    (fn [new-report]
                                                                (debug "New report: " new-report)
                                                                (om/update-state! this assoc :input-report new-report))}))
                 [:fieldset
                  [:legend "Filters"]
                  [:div.row
                   [:div.columns.small-12.large-3
                    (filter/->TagFilter (om/computed {:tags (get-in graph-filter [:tag-filter :filter/include-tags])}
                                                     {:on-change #(do
                                                                   (om/update-state! this assoc-in [:graph-filter :tag-filter] {:filter/include-tags %}))}))]
                   [:div.columns.small-12.large-9
                    (filter/->DateFilter (om/computed {:filter (:date-filter graph-filter)}
                                                      {:on-change #(do
                                                                    (om/update-state! this assoc-in [:graph-filter :date-filter] %))}))]]]]
                [:fieldset.fieldset
                 [:legend "Dataset"]
                 [:div.row
                  [:div.columns.small-12.medium-3
                   "Transactions with tags"]
                  [:div.columns.small-12.medium-9
                   "Filter date"]]
                 [:div.row
                  [:div.columns.small-12.medium-3
                   (filter/->TagFilter (om/computed {:tags (get-in data-set [:tag-filter :filter/include-tags])}
                                                    {:on-change #(do
                                                                  (om/update-state! this assoc-in [:data-set :tag-filter] {:filter/include-tags %})
                                                                  (update-query-params! this assoc :filter (.data-set this)))}))]

                  [:div.columns.small-12.medium-9
                   (filter/->DateFilter (om/computed {:filter (:date-filter data-set)}
                                                     {:on-change #(do
                                                                   (om/update-state! this assoc-in [:data-set :date-filter] %)
                                                                   (update-query-params! this assoc :filter (.data-set this)))}))]]]
                [:div.float-right
                 [:a.button.secondary
                  ;{:on-click on-close}
                  "Cancel"]
                 [:a.button.primary
                  {:href "#"
                   ;(when (:db/id (:dashboard/project dashboard))
                   ;        (routes/key->route :route/project->dashboard
                   ;                           {:route-param/project-id (:db/id (:dashboard/project dashboard))}))
                   :on-click
                         #(.save-widget this
                                        (cond->
                                          (-> state
                                              (assoc :input-dashboard {:dashboard/uuid (:dashboard/uuid dashboard)}))

                                          (seq (.graph-filter this))
                                          (assoc-in [:input-graph :graph/filter] (.graph-filter this))

                                          (seq (.data-set this))
                                          (assoc-in [:input-widget :widget/filter] (.data-set this))

                                          (not (:widget/uuid (:input-widget state)))
                                          (assoc-in [:input-widget :widget/uuid] (d/squuid))

                                          (not (:graph/uuid (:input-graph state)))
                                          (assoc-in [:input-graph :graph/uuid] (d/squuid))

                                          (not (:report/uuid (:input-report state)))
                                          (assoc-in [:input-report :report/uuid] (d/squuid))))}
                  "Save"]]])
         (when (some? active-widget)
           [:div.float-left
            [:a.button.alert
             {:on-click #(.delete-widget this active-widget)}
             "Delete"]])

         ;(render [this]
         ;  (let [{:keys [query/transactions
         ;                query/active-widget]} (om/props this)
         ;        {:keys [input-graph input-report data-set graph-filter] :as state} (om/get-state this)
         ;        {:keys [on-close
         ;                on-save
         ;                on-delete
         ;                dashboard
         ;                widget]} (om/get-computed this)
         ;        data (report/generate-data input-report (merge (:tag-filter graph-filter)
         ;                                                       (:date-filter graph-filter)) transactions)]
         ;    (debug "Active widget: " active-widget)
         ;    (html
         ;      [:div.clearfix
                ;[:ul.breadcrumbs
                ; [:li
                ;  [:a
                ;   {:href (if (:db/id (:dashboard/project dashboard))
                ;            (routes/key->route :route/project->dashboard
                ;                               {:route-param/project-id (:db/id (:dashboard/project dashboard))})
                ;            "#")}
                ;   "Dashboard"]]
                ; [:li
                ;  [:span (if widget "Edit widget" "New widget")]]]
                ;[:fieldset.fieldset
                ; [:legend "Dataset"]
                ; [:div.row
                ;  [:div.columns.small-12.medium-3
                ;   "Transactions with tags"]
                ;  [:div.columns.small-12.medium-9
                ;   "Filter date"]]
                ; [:div.row
                ;  [:div.columns.small-12.medium-3
                ;   (filter/->TagFilter (om/computed {:tags (get-in data-set [:tag-filter :filter/include-tags])}
                ;                                    {:on-change #(do
                ;                                                  (om/update-state! this assoc-in [:data-set :tag-filter] {:filter/include-tags %})
                ;                                                  (update-query-params! this assoc :filter (.data-set this)))}))]
                ;
                ;  [:div.columns.small-12.medium-9
                ;   (filter/->DateFilter (om/computed {:filter (:date-filter data-set)}
                ;                                     {:on-change #(do
                ;                                                   (om/update-state! this assoc-in [:data-set :date-filter] %)
                ;                                                   (update-query-params! this assoc :filter (.data-set this)))}))]]]
                ;[:div.input-group
                ; [:select.input-group-field
                ;  (opts {:on-change #(do
                ;                      (debug "selected chart: " (keyword (.-value (.-target %))))
                ;                      (select-graph-style this (keyword (.-value (.-target %)))))})
                ;  [:option
                ;   (opts {:value (subs (str :graph.style/bar) 1)})
                ;   "Bar Chart"]
                ;  [:option
                ;   (opts {:value (subs (str :graph.style/area) 1)})
                ;   "Area Chart"]
                ;  [:option
                ;   (opts {:value (subs (str :graph.style/line) 1)})
                ;   "Line Chart"]
                ;  [:option
                ;   (opts {:value (subs (str :graph.style/number) 1)})
                ;   "Number Chart"]]
                ;
                ; [:div.row.small-up-1.medium-up-2
                ;  [:div.column
                ;   ;(opts {:style {:height "15em"}})
                ;   [:fieldset.fieldset
                ;    [:legend "Preview"]
                ;    (->Widget (om/computed {:widget/graph  input-graph
                ;                            :widget/report input-report
                ;                            :widget/data   data}
                ;                           {:data transactions}))]]
                ;  [:div.column
                ;   [:fieldset.fieldset
                ;    [:legend "Settings"]
                ;    [:input
                ;     {:value       (or (:report/title input-report) "")
                ;      :type        "text"
                ;      :placeholder "Untitled"
                ;      :on-change   #(change-report-title this (.-value (.-target %)))}]
                ;    (->ChartSettings (om/computed {}
                ;                                  {:graph     input-graph
                ;                                   :report    input-report
                ;                                   :on-change (fn [new-report]
                ;                                                (debug "New report: " new-report)
                ;                                                (om/update-state! this assoc :input-report new-report))}))]]]
                ; [:fieldset
                ;  [:legend "Filters"]
                ;  [:div.row
                ;   [:div.columns.small-12.large-3
                ;    (filter/->TagFilter (om/computed {:tags (get-in graph-filter [:tag-filter :filter/include-tags])}
                ;                                     {:on-change #(do
                ;                                                   (om/update-state! this assoc-in [:graph-filter :tag-filter] {:filter/include-tags %}))}))]
                ;   [:div.columns.small-12.large-9
                ;    (filter/->DateFilter (om/computed {:filter (:date-filter graph-filter)}
                ;                                      {:on-change #(do
                ;                                                    (om/update-state! this assoc-in [:graph-filter :date-filter] %))}))]]]]





         ])))

;]
  ; )))
  )

(def ->NewWidget (om/factory NewWidget))