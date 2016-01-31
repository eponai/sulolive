(ns eponai.client.ui.format
  (:require
    [cljs-time.core :as t]
    [cljs-time.format :as t.format]
    [cljs-time.coerce :as c]))

(defn days-since [timestamp]
  (t/in-days (t/interval (c/from-long timestamp) (t/now))))

(defn dates-by-year-month [dates]
  (letfn [(*group-by [key]
            (fn [m [k v]]
              (assoc m k (->> v
                              (group-by key)
                              (into (sorted-map))))))]
    (->> dates
         (group-by :date/year)
         (reduce (*group-by :date/month) (sorted-map)))))

(defn transactions-by-year-month-day [transactions]
  (let [by-date (group-by :transaction/date transactions)]

    (letfn [(*group-date  [ks coll]
              (reduce
                (fn [m [date transactions]]
                  (assoc-in m
                            (mapv #(get date %) ks)
                            {:date date
                             :transactions transactions}))
                (sorted-map)
                coll))]
      (*group-date [:date/year :date/month :date/day] by-date))))

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
