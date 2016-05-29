(ns eponai.web.ui.utils.filter
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.common.format.date :as date]
    [eponai.common.prefixlist :as pl]
    [eponai.web.ui.datepicker :refer [->Datepicker]]
    [eponai.web.ui.utils :as utils]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug]]
    [eponai.common.format :as f]
    [goog.events :as events]))

;; --------- WIP: Tag suggestion dropdown -------
(comment
  "Takes a list of tags (tag-list) and helps an input tag with suggesting tags."
  [:div.dropdown
   (let [tag-dd (into [] (comp (map-indexed
                                 (fn [i {:keys [tag/name tag/count] :as tag}]
                                   [:a
                                    {:key         name
                                     :tab-index   -1
                                     :ref         (tag-idx->ref i)
                                     :style       {:display "flex" :flex-direction "row" :justify-content "space-between"}
                                     :on-click    #(.add-tag this tag)
                                     :on-key-down (fn [e]
                                                    (let [matched (condp = (.-keyCode e)
                                                                    events/KeyCodes.DOWN (utils/focus-ref this (tag-idx->ref (inc i)))
                                                                    events/KeyCodes.UP (utils/focus-ref this (tag-idx->ref (dec i)))
                                                                    events/KeyCodes.ENTER (.add-tag this tag)
                                                                    nil)]
                                                      (when matched
                                                        (.preventDefault e))))}
                                    [:span name]
                                    [:span count]]))
                               (take 5))
                      (pl/filter-prefix tag-list (:tag/name input-tag)))]
     tag-dd)])

;; ----------------

(defui AmountFilter
  utils/ISyncStateWithProps
  (props->init-state [_ props]
    (assert (contains? props :amount-filter)
            (str "No amount filter in props for AmountFilter. Was: " props))
    (:amount-filter props))

  Object
  (initLocalState [this]
    (utils/props->init-state this (om/props this)))

  (componentWillReceiveProps [this new-props]
    (utils/sync-with-received-props this new-props))

  (render [this]
    (let [{:keys [filter/min-amount filter/max-amount] :as filters} (om/get-state this)
          {:keys [on-change]} (om/get-computed this)]
      (html
        [:div.row
         ;[:div.columns.small-1.text-right
         ; "Min:"]
         [:div.columns.small-6
          [:input
           {:value       (or min-amount "")
            :type        "number"
            :on-change   #(let [v (.-value (.-target %))]
                           (if (seq v)
                             (om/update-state! this assoc :filter/min-amount (f/str->number v))
                             (om/update-state! this dissoc :filter/min-amount)))
            :on-key-down #(utils/on-enter-down % (fn [_]
                                                   (on-change filters)))
            :placeholder "Min"}]]
         ;[:div.columns.small-1.text-right
         ; "Max:"]
         [:div.columns.small-6
          [:input
           {:value       (or max-amount "")
            :type        "number"
            :on-change   #(let [v (.-value (.-target %))]
                           (if (seq v)
                             (om/update-state! this assoc :filter/max-amount (f/str->number v))
                             (om/update-state! this dissoc :filter/max-amount)))
            :on-key-down #(utils/on-enter-down % (fn [_]
                                                   (on-change filters)))
            :placeholder "Max"}]]]))))

(def ->AmountFilter (om/factory AmountFilter))

(defui DateFilter
  Object
  (init-filters [_ filters]
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
      (.init-filters this filter)))
  (componentDidMount [this]
    (om/set-state! this (.init-filters this (:filter (om/props this)))))

  (componentWillReceiveProps [this new-props]
    (om/update-state! this assoc :filter (:filter new-props)))

  (set-this-month-filter [this]
    (om/update-state! this
                      #(-> %
                           (update :filter dissoc :filter/end-date :filter/last-x-days)
                           (assoc-in [:filter :filter/start-date] (date/date-map (date/first-day-of-this-month))))))

  (set-last-x-days-filter [this span]
    (om/update-state! this
                      #(-> %
                           (update :filter dissoc :filter/end-date :filter/start-date)
                           (assoc-in [:filter :filter/last-x-days] span))))

  (reset-date-filters [this]
    (om/update-state! this
                      #(update % :filter dissoc :filter/start-date :filter/end-date :filter/last-x-days)))

  (set-date-range [this {:keys [filter/end-date
                                filter/start-date] :as f}]
    (let [{state-filter :filter} (om/get-state this)
          state-end (:filter/end-date state-filter)
          state-start (:filter/start-date state-filter)]
      (cond start-date
            (if (and state-end
                     (< (:date/timestamp state-end)
                        (:date/timestamp start-date)))
              (om/update-state! this update :filter merge (assoc f :filter/end-date (date/date-map start-date)))
              (om/update-state! this update :filter merge f))

            end-date
            (if (and state-start
                     (> (:date/timestamp state-start)
                        (:date/timestamp end-date)))
              (om/update-state! this update :filter merge (assoc f :filter/start-date (date/date-map end-date)))
              (om/update-state! this update :filter merge f)))))

  (update-date-filter [this value]
    (let [time-type (keyword value)]
        (om/update-state! this assoc :type time-type)
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
      (.should-notify-change this)))

  (should-notify-change [this]
    (let [{:keys [on-change]} (om/get-computed this)
          input-filter (:filter (om/get-state this))]
      (when on-change
        (on-change input-filter))))

  (render [this]
    (let [{:keys [filter type]} (om/get-state this)]
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
                      :placeholder "From..."
                      :value       (:filter/start-date filter)
                      :on-change   #(do
                                     (.set-date-range this {:filter/start-date %})
                                     (.should-notify-change this))}))])
          (when (= :date-range type)
            [:div.column
             (->Datepicker
               (opts {:key         ["To date..."]
                      :placeholder "To..."
                      :value       (:filter/end-date filter)
                      :on-change   #(do
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
      (debug "tagfilter: new tags (added): " new-tags)
      (om/update-state! this assoc :tags new-tags)
      (when on-change
        (on-change new-tags))
      true))

  (delete-tag [this tag]
    (let [{:keys [on-change]} (om/get-computed this)
          {:keys [tags]} (om/get-state this)
          new-tags (utils/delete-tag tags tag)]
      (om/update-state! this assoc :tags new-tags)
      (debug "tagfilter: new tags (deleted): " new-tags)
      (when on-change
        (on-change new-tags))))

  (initLocalState [this]
    (let [{:keys [tags placeholder]} (om/props this)]
      {:tags        (or tags [])
       :placeholder placeholder}))
  (componentWillReceiveProps [this new-props]
    (let [{state-tags :tags} (om/get-state this)
          {props-tags :tags} new-props]
      (when (not= (seq props-tags) (seq state-tags))
        (om/update-state! this assoc
                          :tags (or props-tags [])
                          :placeholder (:placeholder new-props)))))

  (render [this]
    (let [{:keys [input-tag tags placeholder]} (om/get-state this)
          {:keys [type input-only? tag-list]} (om/get-computed this)
          tag-idx->ref (fn [i] (str "tag-suggestion-" i))]
      (html
        (cond
          (nil? type)
          [:div
           (opts {:class "tagfilter"
                  :style {:display        :flex
                          :flex-direction :column}})

           (utils/tag-input {:input-tag     input-tag
                             :selected-tags tags
                             :ref           (tag-idx->ref -1)
                             :on-change     #(om/update-state! this assoc :input-tag %)
                             :on-add-tag    #(do (om/update-state! this assoc :input-tag "")
                                                 (.add-tag this %))
                             :on-delete-tag #(.delete-tag this %)
                             :on-key-down   (fn [e]
                                              (when (= events/KeyCodes.DOWN (.-keyCode e))
                                                (.preventDefault e)
                                                (utils/focus-ref this (tag-idx->ref 0))))
                             :input-only?   input-only?
                             :placeholder   (or placeholder "Enter to add tag...")})])))))

(def ->TagFilter (om/factory TagFilter))