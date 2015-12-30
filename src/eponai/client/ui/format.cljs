(ns eponai.client.ui.format
  (:require
    [cljs-time.core :as t]
    [cljs-time.format :as t.format]))

(defn dates-by-year-month [dates]
  (letfn [(*group-by [key]
            (fn [m [k v]]
              (assoc m k (->> v
                              (group-by key)
                              (into (sorted-map))))))]
    (->> dates
         (group-by :date/year)
         (reduce (*group-by :date/month) (sorted-map)))))

(defn day-name
  "Given date, returns name of the date and it's number with the appropriate suffix.
  Examples:
  2015-10-16 => Friday 16th
  2015-10-21 => Wednesday 21st"
  [{:keys [date/year date/month date/day]}]
  ((get t.format/date-formatters "EEE")
    (t/date-time year month day)))

(defn month-name [month]
  ((get t.format/date-formatters "MMM")
    (t/date-time 0 month)))
