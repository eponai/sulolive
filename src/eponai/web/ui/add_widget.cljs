(ns eponai.web.ui.add-widget
  (:require
    [cljs-time.coerce :as c]
    [cljs-time.core :as t]
    [datascript.core :as d]
    [eponai.client.ui :refer [update-query-params! map-all] :refer-macros [opts]]
    [eponai.web.ui.add-widget.add-goal :refer [->NewGoal]]
    [eponai.web.ui.add-widget.add-track :refer [->NewTrack]]
    [eponai.web.ui.widget :as widget :refer [Widget]]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug]]
    [eponai.web.routes :as routes]))

;;; ####################### Actions ##########################


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
  (params [this]
    {:filter {}})
  static om/IQuery
  (query [_]
    ['({:query/transactions [:transaction/uuid
                             :transaction/amount
                             :transaction/conversion
                             {:transaction/type [:db/ident]}
                             {:transaction/currency [:currency/code]}
                             {:transaction/tags [:tag/name]}
                             {:transaction/date [:date/ymd
                                                 :date/timestamp]}]}
        {:filter ?filter})
     :query/tags
     ;{:query/active-widget (om/get-query Widget)}
     ;{:query/widget-type [:ui.component.widget/type]}
     ])
  Object
  (save-widget [this widget]
    (if (some? (:db/id widget))
      (om/transact! this `[(widget/edit ~(assoc widget :mutation-uuid (d/squuid)))
                           :query/dashboard])
      (om/transact! this `[(widget/create ~(assoc widget :mutation-uuid (d/squuid)))
                           :query/dashboard])))
  (delete-widget [this widget]
    (om/transact! this `[(widget/delete ~(assoc (select-keys widget [:widget/uuid]) :mutation-uuid (d/squuid)))
                         :query/dashboard]))

  (default-report [_ props]
    (let [{:keys [widget-type]} (::om/computed props)]
      (cond (= widget-type :track)
            {:report/uuid  (d/squuid)
             :report/track {:track/functions [{:track.function/id       :track.function.id/sum
                                               :track.function/group-by :transaction/tags}]}}

            (= widget-type :goal)
            {:report/uuid (d/squuid)
             :report/goal {:goal/value 50
                           :goal/cycle {:cycle/repeat       0
                                        :cycle/period       :cycle.period/day
                                        :cycle/period-count 1
                                        :cycle/start        (c/to-long (t/today))}}})))

  (default-graph [_ props]
    (let [{:keys [widget-type]} (::om/computed props)]
      (cond (= widget-type :track)
            {:graph/uuid  (d/squuid)
             :graph/style :graph.style/bar}

            (= widget-type :goal)
            {:graph/uuid (d/squuid)
             :graph/style :graph.style/progress-bar})))

  (data-set [this]
    (let [{:keys [data-set]} (om/get-state this)]
      (merge (:tag-filter data-set)
             (:date-filter data-set))))
  (graph-filter [this]
    (let [{:keys [graph-filter]} (om/get-state this)]
      (merge (:tag-filter graph-filter)
             (:date-filter graph-filter))))
  (init-state [this props]
    (let [{:keys [index]} (::om/computed props)
          {:keys [query/active-widget]} props]
      ;(if active-widget
      ;  (do
      ;    (om/set-query! this {:params {:filter (or (:widget/filter active-widget) {})}})
      ;    {:input-widget active-widget}))
      (let [g (.default-graph this props)
            dim (widget/dimensions g)]
        {:input-widget {:widget/uuid   (d/squuid)
                        :widget/index  index
                        :widget/width  (:minW dim)
                        :widget/height (:minH dim)
                        :widget/graph  g
                        :widget/report (.default-report this props)}})))
  (initLocalState [this]
    (merge (.init-state this (om/props this))
           {:computed/new-track-on-change (fn [new-widget & [{:keys [update-data?]}]]
                                            (om/update-state! this assoc :input-widget new-widget)
                                            (when update-data?
                                              (om/update-query! this assoc-in [:params :filter] (:widget/filter new-widget))))
            :computed/new-goal-on-change  (fn [new-widget & [{:keys [update-data?]}]]
                                            (om/update-state! this assoc :input-widget new-widget)
                                            ;(when update-data?
                                            ;  (om/update-query! this assoc-in [:params :filter] (:widget/filter new-widget)))
                                            )}))

  (componentWillReceiveProps [this new-props]
    (om/set-state! this (.init-state this new-props)))
  (render [this]
    (let [{:keys [query/transactions query/tags]} (om/props this)
          {:keys [input-widget
                  computed/new-track-on-change
                  computed/new-goal-on-change]} (om/get-state this)
          {:keys [dashboard-id widget-type on-save]} (om/get-computed this)]
      (html
        [:div
         (opts {:style {:padding "1em"}})
         ;[:ul.breadcrumbs
         ; [:li
         ;  [:a
         ;   {:href (when project-id
         ;            (routes/key->route :route/project->dashboard
         ;                               {:route-param/project-id project-id}))}
         ;   "Dashboard"]]
         ; [:li (cond (= :track widget-type)
         ;            "Track"
         ;            (= :goal widget-type)
         ;            "Goal")]]
         ;[:hr]

         (cond
           (= :track widget-type)
           (->NewTrack (om/computed {}
                                    {:widget       input-widget
                                     :tags         tags
                                     :transactions transactions
                                     :on-change    new-track-on-change}))

           (= :goal widget-type)
           (->NewGoal (om/computed {}
                                   {:widget       input-widget
                                    :transactions transactions
                                    :on-change    new-goal-on-change})))
         ;[:div.row.column.small-12.medium-10
         ; [:hr]]
         [:div.float-right
          (opts {:style {:display :inline-block}})
          ;(when (some? active-widget)
          ;  [:a.button.alert
          ;   {:href     (when (:db/id (:dashboard/project dashboard))
          ;                (routes/key->route :route/project->dashboard
          ;                                   {:route-param/project-id (:db/id (:dashboard/project dashboard))}))
          ;    :on-click #(.delete-widget this active-widget)}
          ;   "Delete"])
          [:a.button.primary
           {:on-click #(do
                        (.save-widget this (assoc input-widget :widget/dashboard dashboard-id))
                        (when on-save
                          (on-save)))}
           "Save"]]]))))

(def ->NewWidget (om/factory NewWidget))