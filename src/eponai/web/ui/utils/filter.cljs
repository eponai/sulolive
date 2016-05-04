(ns eponai.web.ui.utils.filter
  (:require
    [cljs-time.core :as time]
    [cljs-time.coerce :as coerce]
    [eponai.client.ui :refer-macros [opts]]
    [eponai.common.format :as format]
    [eponai.common.format.date :as date]
    [eponai.web.ui.datepicker :refer [->Datepicker]]
    [eponai.web.ui.utils :as utils]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug]]
    ))

(defui DateFilter
  Object
  (init-filters [this filters]
    (debug "Will init filter: " filters)
    (cond
      (some? (:filter/last-x-days filters))
      {:filter filters
       :type (cond (= 30 (:filter/last-x-days filters))
                   :last-30-days
                   (= 7 (:filter/last-x-days filters))
                   :last-7-days)}

      (or (:filter/end-date filters)
          (:filter/start-date filters))
      {:filter filters
       :type   :date-range}

      :else
      {:filter filters
       :type :all-time}))

  (initLocalState [this]
    (let [{:keys [filter]} (om/props this)]
      (let [new-filters (.init-filters this filter)]
        (debug "Init filter " new-filters)
        new-filters)))

  (componentWillReceiveProps [this new-props]
    (om/set-state! this (.init-filters this (:filter new-props))))

  (set-this-month-filter [this]
    (let [today (time/today)
          year (time/year today)
          month (time/month today)
          start-date (coerce/to-date (time/date-time year month 1))]
      (om/update-state! this
                        #(-> %
                             (update :filter dissoc :filter/end-date :filter/last-x-days)
                             (assoc-in [:filter :filter/start-date] start-date)))
      ;(.should-notify-change this)
      ))

  (set-last-x-days-filter [this span]
    (om/update-state! this
                      #(-> %
                           (update :filter dissoc :filter/end-date :filter/start-date)
                           (assoc-in [:filter :filter/last-x-days] span)))
    ;(.should-notify-change this)
    )

  (reset-date-filters [this]
    (om/update-state! this
                      #(update % :filter dissoc :filter/start-date :filter/end-date :filter/last-x-days))
    ;(.should-notify-change this)
    )

  (set-date-range [this date-filter]
    (om/update-state! this update :filter merge date-filter)
    ;(.should-notify-change this)
    )

  (update-date-filter [this value]
    (let [time-type (keyword (cljs.reader/read-string value))]
      (debug "Filter: Updateing date filter: " value)
      (cond
        (= time-type :all-time)
        (.reset-date-filters this)
        (= time-type :last-7-days)
        (.set-last-x-days-filter this 7)
        (= time-type :last-30-days)
        (.set-last-x-days-filter this 30)
        (= time-type :this-month)
        (.set-this-month-filter this)
        :else
        (om/update-state! this #(update % :filter dissoc :filter/last-x-days)))
      (om/update-state! this assoc :type time-type)
      (.should-notify-change this)))

  (should-notify-change [this]
    (let [{:keys [on-change]} (om/get-computed this)
          input-filter (:filter (om/get-state this))]

      (when on-change
        (on-change input-filter))))

  (render [this]
    (let [{:keys [filter type]} (om/get-state this)]
      (debug "Filter: state: " filter)
      (html
        [:div.filters
         [:div.row.small-up-1.medium-up-3

          [:div.column
           [:select
            (opts {:value (name type)
                   :on-change #(.update-date-filter this (.-value (.-target %)))})
            [:option
             {:value (name :all-time)}
             "all time"]
            [:option
             {:value (name :this-month)}
             "this month"]
            [:option
             {:value (name :last-7-days)}
             "last 7 days"]
            [:option
             {:value (name :last-30-days)}
             "last 30 days"]
            [:option
             {:value (name :date-range)}
             "custom..."]]]
          (when (= :date-range type)
            [:div.column
             (->Datepicker
               (opts {:key         ["From date..."]
                      :placeholder "From date..."
                      :value       (:filter/start-date filter)
                      :on-change   #(do
                                     (debug "Filter: Start-date: " %)
                                     (.set-date-range this {:filter/start-date %})
                                     (.should-notify-change this))}))])
          (when (= :date-range type)
            [:div.column
             (->Datepicker
               (opts {:key         ["To date..."]
                      :placeholder "To date..."
                      :value       (:filter/end-date filter)
                      :on-change   #(do
                                     (debug "Filter: Update date: " %)
                                     (.set-date-range this {:filter/end-date %})
                                     (.should-notify-change this))
                      :min-date    (:filter/start-date filter)}))])]]))))

(def ->DateFilter (om/factory DateFilter))

(defui TagFilter
  Object
  (add-tag [this tag]
    (let [{:keys [on-change]} (om/get-computed this)
          {:keys [tags]} (om/get-state this)
          new-tags (utils/add-tag tags tag)]
      (om/update-state! this assoc
                        :input-tag ""
                        :tags new-tags)
      (when on-change
        (on-change new-tags))))
  (delete-tag [this tag]
    (let [{:keys [on-change]} (om/get-computed this)
          {:keys [tags]} (om/get-state this)
          new-tags (utils/delete-tag tags tag)]
      (om/update-state! this assoc :tags new-tags)
      (when on-change
        (on-change new-tags))))

  (initLocalState [this]
    (let [{:keys [tags placeholder]} (om/props this)]
      {:tags        (or tags [])
       :placeholder placeholder}))
  (componentWillReceiveProps [this new-props]
    (let [{:keys [tags placeholder]} new-props]
      (om/update-state! this assoc :tags tags :placeholder placeholder)))

  (render [this]
    (let [{:keys [input-tag tags placeholder]} (om/get-state this)
          {:keys [type]} (om/get-computed this)]
      (debug "Rendering tags: " tags)
      (html
        (cond
          (nil? type)
          [:div
           (opts {:style {:display        :flex
                          :flex-direction :column}})

           (utils/tag-input {:input-tag     input-tag
                             :selected-tags tags
                             :on-change     #(om/update-state! this assoc :input-tag %)
                             :on-add-tag    #(.add-tag this %)
                             :on-delete-tag #(.delete-tag this %)
                             :placeholder   (or placeholder "Enter to add tag...")})])))))

(def ->TagFilter (om/factory TagFilter))