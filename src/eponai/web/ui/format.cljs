(ns eponai.web.ui.format
  (:require
    [cljs-time.core :as t]
    [cljs-time.format :as t.format]
    [cljs-time.coerce :as c]
    [taoensso.timbre :refer-macros [debug]]))

(defn days-since [timestamp]
  {:pre [(number? timestamp)]}
  (t/in-days (t/interval (c/from-long timestamp) (t/now))))

(defn days-until [timestamp]
  {:pre [(number? timestamp)]}
  (let [now (c/to-long (t/now))]
    (if (> timestamp now)
      (inc (t/in-days (t/interval (c/from-long now) (c/from-long timestamp)))) ;Increment to include the last day
      (* -1 (t/in-days (t/interval (c/from-long timestamp) (c/from-long now)))))))

(defn to-str [timestamp & [format]]
  {:pre [(number? timestamp)]}
  (if format
    (let [formatter (t.format/formatter format)]
      (t.format/unparse formatter (c/from-long timestamp)))
    (str (c/from-long timestamp))))

;(defn dates-by-year-month [dates]
;  (letfn [(*group-by [key]
;            (fn [m [k v]]
;              (assoc m k (->> v
;                              (group-by key)
;                              (into (sorted-map))))))]
;    (->> dates
;         (group-by :date/year)
;         (reduce (*group-by :date/month) (sorted-map)))))

;(defn transactions-by-year-month-day [transactions]
;  (let [by-date (group-by :transaction/date transactions)]
;
;    (letfn [(*group-date  [ks coll]
;              (reduce
;                (fn [m [date transactions]]
;                  (assoc-in m
;                            (mapv #(get date %) ks)
;                            {:date date
;                             :transactions transactions}))
;                (sorted-map)
;                coll))]
;      (*group-date [:date/year :date/month :date/day] by-date))))

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
