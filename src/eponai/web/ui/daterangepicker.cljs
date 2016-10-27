(ns eponai.web.ui.daterangepicker
  (:require
    [cljs-time.core :as time]
    [cljs-time.periodic :as periodic]
    [cljs-time.format :as t.format]
    [om.dom :as dom]
    [om.next :as om :refer-macros [defui]]
    [eponai.common.format.date :as date]
    [eponai.web.ui.utils :as utils]
    [taoensso.timbre :refer-macros [debug]]))

(defui DateRangePicker
  utils/ISyncStateWithProps
  (props->init-state [_ {:keys [start-date end-date selected-range single-calendar?]}]
    (let [start-date (or start-date (time/minus (date/today) (time/days 7)))
          end-date (if single-calendar?
                     start-date
                     (or end-date (date/today)))
          start-month (time/first-day-of-the-month start-date)]
      {:start-date     start-date
       :end-date       end-date
       :old-start-date start-date
       :old-end-date   end-date
       :left-calendar  {:month start-month}
       :right-calendar {:month (time/plus start-month (time/months 1))}
       :selected-range selected-range}))

  Object
  (initLocalState [this]
    (utils/props->init-state this (om/props this)))

  (componentWillReceiveProps [this next-props]
    (utils/sync-with-received-props this next-props))

  (show [this]
    (let [{:keys [start-date end-date]} (om/get-state this)]
      (om/update-state! this assoc
                        :old-end-date end-date
                        :old-start-date start-date)))

  (update-dates [this start end selected-key]
    (let [month (time/first-day-of-the-month start)]
      (om/update-state! this assoc
                        :selected-range selected-key
                        :start-date start
                        :end-date end
                        :left-calendar {:month month}
                        :right-calendar {:month (time/plus month (time/months 1))})))

  (clickPrev [this _]
    (om/update-state! this (fn [st]
                             (-> st
                                 (update-in [:right-calendar :month] #(time/minus % (time/months 1)))
                                 (update-in [:left-calendar :month] #(time/minus % (time/months 1)))))))

  (clickNext [this _]
    (om/update-state! this (fn [st]
                             (-> st
                                 (update-in [:right-calendar :month] #(time/plus % (time/months 1)))
                                 (update-in [:left-calendar :month] #(time/plus % (time/months 1)))))))
  (clickDate [this date]
    (let [{:keys [start-date end-date]} (om/get-state this)
          {:keys [single-calendar?]} (om/props this)
          {:keys [on-apply]} (om/get-computed this)]
      (cond single-calendar?
            (do
              (om/update-state! this assoc
                                :start-date date
                                :old-start-date date
                                :end-date date
                                :old-end-date date
                                :show-calendars? false
                                :is-showing? false)
              (when on-apply
                (on-apply (date/date-map date))))

            end-date
            ;; Starting a new date range selection, set end date to nil
            (om/update-state! this assoc
                              :start-date date
                              :end-date nil)
            (and start-date (not end-date))
            (if (time/before? date start-date)
              ;; Allow user to select start/end in any order by setting the smallest date to start and largest to end.
              (om/update-state! this assoc
                                :end-date start-date
                                :start-date date)
              (om/update-state! this assoc
                                :end-date date)))))

  (renderCalendar [this calendar-side]
    (let [{:keys [start-date end-date hover-date] :as state} (om/get-state this)
          {:keys [single-calendar?]} (om/props this)
          dt (:month (get state calendar-side))
          days-in-month (time/number-of-days-in-the-month dt)
          first-day (time/first-day-of-the-month dt)
          last-day (time/last-day-of-the-month dt)
          day-of-week (time/day-of-week first-day)          ;Return number for day (Mon = 1, Sun = 7)
          calendar-start-date (time/minus first-day (time/days (dec day-of-week))) ; Subtract 0-indexed weekday to get start date of calendar.
          calendar-range (periodic/periodic-seq calendar-start-date (time/plus last-day (time/days 1)) (time/days 1))

          calendar-end-date (if (zero? (mod (count calendar-range) 7))
                              (time/plus last-day (time/days 1))
                              (time/plus last-day (time/days (inc (- 7 (mod (+ days-in-month (dec day-of-week)) 7))))))

          ; Add days from the next month to fill upp an entire week column, add an extra day for including end date in periodiq seq
          ;calendar-end-date (time/plus last-day (time/days (inc (- 7 (mod (+ days-in-month (dec day-of-week)) 7)))))
          weeks (partition 7 (periodic/periodic-seq calendar-start-date calendar-end-date (time/days 1)))]
      (dom/table
        nil
        (dom/thead
          nil
          (dom/tr
            nil
            (if (or single-calendar? (= calendar-side :left-calendar))
              (dom/th
                #js {:className "prev available"
                     :onClick   #(do
                                  (.clickPrev this %))}
                (dom/a
                  nil
                  (dom/i
                    #js {:className "fa fa-chevron-left"})))
              (dom/th nil))
            (dom/th
              #js {:className "month"
                   :colSpan 5}
              (t.format/unparse (t.format/formatter "MMM yyyy") dt))
            (if (or single-calendar? (= calendar-side :right-calendar))
              (dom/th
                #js {:className "next available"
                     :onClick   #(do
                                  (.clickNext this %))}
                (dom/a
                  nil
                  (dom/i
                    #js {:className "fa fa-chevron-right"})))
              (dom/th nil)))

          (apply dom/tr
            nil
            (map (fn [day]
                   (dom/th
                     #js {:className "week"}
                     day))
                 ["Mo" "Tu" "We" "Th" "Fr" "Sa" "Su"])))

        (apply dom/tbody
               nil
               (map (fn [week]
                      (apply dom/tr
                             nil
                             (map
                               (fn [date]
                                 (let [classes (cond-> ["available"]

                                                       (time/equal? (date/today) date)
                                                       (conj "today")
                                                       (> (time/day-of-week date) 5)
                                                       (conj "weekend")

                                                       (not= (time/month date) (time/month dt))
                                                       (conj "off")

                                                       (and start-date
                                                            (time/equal? start-date date))
                                                       (conj "active" "start-date")

                                                       (and end-date
                                                            (time/equal? end-date date))
                                                       (conj "active" "end-date")

                                                       (and start-date end-date
                                                            (time/after? date start-date)
                                                            (time/before? date end-date))
                                                       (conj "in-range")

                                                       (and hover-date (not end-date) (not single-calendar?)
                                                            (or (and (time/before? hover-date start-date)
                                                                     (time/after? date hover-date)
                                                                     (time/before? date start-date))
                                                                (and (time/after? hover-date start-date)
                                                                     (time/before? date hover-date)
                                                                     (time/after? date start-date))))
                                                       (conj "in-range"))]
                                   (dom/td
                                     #js {:className   (clojure.string/join " " classes)
                                          ;:data-title (str "r" row "c" col)
                                          :onClick     #(.clickDate this date)
                                          :onMouseEnter #(om/update-state! this assoc :hover-date date)}
                                     (dom/small nil (time/day date)))))
                               week)))
                    weeks)))))
  (renderDateRangeSelection [this]
    (let [{:keys [ranges]} (om/props this)
          {:keys [on-apply on-cancel]} (om/get-computed this)
          {:keys [start-date end-date show-calendars? old-start-date old-end-date selected-range]} (om/get-state this)
          ranges [{:name   "Today"
                   :key    :today
                   :action (fn []
                             (let [t (date/today)]
                               (.update-dates this t t :today)))}
                  {:name   "Yesterday"
                   :key    :yesterday
                   :action (fn []
                             (let [yt (time/minus (date/today) (time/days 1))]
                               (.update-dates this yt yt :yesterday)))}
                  {:name   "Last 7 Days"
                   :key    :last-7-days
                   :action (fn []
                             (let [t (date/today)]
                               (.update-dates this (time/minus t (time/days 7)) t :last-7-days)))}
                  {:name   "Last 30 Days"
                   :key    :last-30-days
                   :action (fn []
                             (let [t (date/today)]
                               (.update-dates this (time/minus t (time/days 30)) t :last-30-days)))}
                  {:name   "This Month"
                   :key    :this-month
                   :action (fn []
                             (let [t (date/today)]
                               (.update-dates this (time/first-day-of-the-month t) (time/last-day-of-the-month t) :this-month)))}
                  {:name   "Last Month"
                   :key    :last-month
                   :action (fn []
                             (let [t (time/minus (date/today) (time/months 1))]
                               (.update-dates this (time/first-day-of-the-month t) (time/last-day-of-the-month t) :last-month)))}
                  {:name   "Custom Range"
                   :key    :custom-range
                   :action (fn [] (om/update-state! this assoc
                                                    :show-calendars? true
                                                    :selected-range :custom-range))}]]
      (dom/div
        #js {:className (str "daterangepicker dropdown ")}

        ;(dom/a
        ;  #js {:className "close"}
        ;  "x")
        ; Calendar select from date.
        (dom/div
          #js {:className (str "calendar-container" (when show-calendars? " show"))}
          (dom/div
            #js {:className "calendar left"}
            (dom/div
              #js {:className "date_input"}
              (dom/input
                #js {:className "input-mini"
                     :type      "text"
                     :name      "date_start"
                     :value     (if start-date
                                  (t.format/unparse (t.format/formatter "MM/dd/yyyy") start-date)
                                  "")})
              (dom/i
                #js {:className "fa fa-calendar"}))
            (dom/div
              #js {:className "calendar-table"}
              (.renderCalendar this :left-calendar)))

          ; Calendar select to date.
          (dom/div
            #js {:className "calendar right"}
            (dom/div
              #js {:className "date_input"}
              (dom/input
                #js {:className "input-mini"
                     ;:type      "text"
                     :name      "date_end"
                     :value     (if end-date
                                  (t.format/unparse (t.format/formatter "MM/dd/yyyy") end-date)
                                  "")})
              (dom/i
                #js {:className "fa fa-calendar"}))
            (dom/div
              #js {:className "calendar-table"}
              (.renderCalendar this :right-calendar))))

        (dom/div
          #js {:className "ranges"}
          (apply dom/ul
                 nil
                 (map
                   (fn [range]
                     (dom/li
                       #js {:className (when (= (:key range) selected-range) "active")
                            :onClick   #(when-let [action-fn (:action range)]
                                         (action-fn))}
                       (:name range)))
                   ranges))
          (dom/div
            #js {:className "action_bar float-right"}
            (dom/a
              #js {:className (str "apply button hollow show")
                   ;:disabled  "disabled"
                   :type      "button"
                   :onClick   #(do
                                (om/update-state! this assoc
                                                  :old-start-date start-date
                                                  :old-end-date end-date
                                                  :is-showing? false)
                                (when on-apply
                                  (on-apply {:start-date (date/date-time start-date)
                                             :end-date (date/date-time end-date)
                                             :selected-range selected-range})))}
              "Apply")
            (dom/a
              #js {:className "close"
                   ;:type      "button"
                   :onClick   #(do
                                (om/update-state! this assoc
                                                  :start-date old-start-date
                                                  :end-date old-end-date
                                                  :is-showing? false)
                                (when on-cancel
                                  (on-cancel)))}
              "Cancel"))))))
  (renderSingleCalendar [this]
    (let [{:keys [start-date]} (om/get-state this)]
      (dom/div
        #js {:className (str "daterangepicker menu-horizontal single dropdown")}
        (dom/div
          #js {:className "calendar-container show"}
          (dom/div
            #js {:className "calendar"}
            (dom/div
              #js {:className "date_input"}
              (dom/input
                #js {:className "input-mini"
                     :type      "text"
                     :name      "date_start"
                     :value     (if start-date
                                  (t.format/unparse (t.format/formatter "MM/dd/yyyy") start-date)
                                  "")})
              (dom/i
                #js {:className "fa fa-calendar"}))
            (dom/div
              #js {:className "calendar-table"}
              (.renderCalendar this :left-calendar)))))))
  (render [this]
    (let [{:keys [old-start-date old-end-date is-showing?]} (om/get-state this)
          {:keys [class single-calendar? tabindex]} (om/props this)
          {:keys [format on-open]} (om/get-computed this)]
      (dom/div
        #js {:className class}
        (dom/div
          nil
          (dom/input
            #js {:className "daterange"
                 :type      "text"
                 :value     (if single-calendar?
                              (when old-start-date (t.format/unparse (t.format/formatter (or format "MM/dd/yyyy")) old-start-date))
                              (str (when old-start-date (t.format/unparse (t.format/formatter (or format "MM/dd/yyyy")) old-start-date)) "-"
                                   (when old-end-date (t.format/unparse (t.format/formatter (or format "MM/dd/yyyy")) old-end-date))))
                 :onClick   #(do (om/update-state! this assoc :is-showing? true)
                                 (when on-open
                                   (on-open)))
                 :onFocus #(do (om/update-state! this assoc :is-showing? true)
                               (when on-open
                                 (on-open)))
                 :onBlur #(om/update-state! this assoc :is-showing? false)})

          (when is-showing?
            (dom/div
              nil
              (utils/click-outside-target #(om/update-state! this assoc :is-showing? false :show-calendars? false))
              (if single-calendar?
                (.renderSingleCalendar this)
                (.renderDateRangeSelection this)))))))))

(def ->DateRangePicker (om/factory DateRangePicker {:keyfn :key}))