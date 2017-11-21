(ns eponai.common.format.date
  (:require
    #?(:cljs [datascript.impl.entity :as entity])
    #?(:clj [clj-time.core :as t]
       :cljs [cljs-time.core :as t])
    #?(:clj [clj-time.coerce :as c]
       :cljs [cljs-time.coerce :as c])
    #?(:clj [clj-time.format :as f]
       :cljs [cljs-time.format :as f])
    #?(:cljs [goog.date.DateTime])
    [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [debug]])
  (:import
    #?(:clj (org.joda.time DateTime))
    #?(:clj
            (datomic.query EntityMap))))

(defn- entity->date-time
  "Return DateTime instance given a date entity map.
  Will first try to parse :date/timestamp long if it exists. If not will try to parse :date/ymd string.

  If neither :date/timestamp or :date/ymd exists in the map, ExceptionInfo is thrown."
  [e]
  (cond
    ;; Parse a UTC long (in milliseconds) into a UTC DateTime. The time for the date will just be 000000.
    (some? (:date/timestamp e))
    (c/from-long (:date/timestamp e))

    ;; Parse yyyy-MM-dd string into a UTC DateTime. Time won't matter cause we have not time in this string.
    (some? (:date/ymd e))
    (f/parse (:date/ymd e))

    :else
    (throw (ex-info (str "Needs :date/timestamp or :date/ymd to format date entity. Got: " e)
                    {:code  :illegal-argument
                     :input e}))))

(defn- date-time? [d]
  #?(:cljs (t/date? d)
     :clj (satisfies? t/DateTimeProtocol d)))

(defn entity-map? [obj]
  #?(:cljs (entity/entity? obj)
     :clj (instance? EntityMap obj)))

(defn date-time
  "Return DateTime instance that's time agnostic, and will sett the time to 000000000 on the given date.
  Formats an input object that's one of the following:

   * A map representing a date (entity) that contains either :date/timestamp or :date/ymd. (If both, :date/timestamp will be used.)
   * A js/Date instance.
   * A 'yyyy-MM-dd' string.
   * An object satisfying the DateTimeProtocol.

  If input is any other type, ExceptionInfo is thrown.
  Note: cljs-time and clj-time behaviors are sometimes incosistent (https://github.com/eponai/budget/wiki/cljs-time-and-clj-time).
  This function is an attempt to those cases and align the behavior on both sides.
  Always use this function when creating or formatting dates."
  [obj]
  (cond
    (or (map? obj)
        (entity-map? obj))
    ;; This is a date entity that we use in our DB.
    (entity->date-time obj)

    (number? obj)
    (c/from-long obj)

    ;; Any string representing a date.
    (string? obj)
    (do
      (some-> obj
              f/parse))

    ;; A JS date.
    #?@(:cljs [(instance? js/Date obj)
               (t/date-time (.getUTCFullYear obj) (inc (.getUTCMonth obj)) (.getUTCDate obj))])

    ;; We already have a clj-time/cljs-time DateTime instance.
    (date-time? obj)
    (t/date-time (t/year obj) (t/month obj) (t/day obj))

    (nil? obj)
    nil

    :else
    (throw (ex-info (str "Trying to format unexpected input to DateTime. Expected map, js/Date or DateTime. Got: " (into {} obj))
                    {:code :illegal-argument
                     :input obj}))))

#?(:cljs
   (defn js-date [obj]
     (if (instance? js/Date obj)
       obj
       ;; Run object through our own date-time that removes issues with timezones and aligns the behavior over clj-time and cljs-time.
       (c/to-date (date-time obj)))))

(defn date-map
  "Return a map representing a DB entity given a date input.
  Takes any input as date-time accepts, as that will be called in this function."
  [obj]
  (let [d (date-time obj)]
    {:date/ymd       (f/unparse (f/formatters :date) d)
     :date/timestamp (c/to-long d)
     :date/year      (t/year d)
     :date/month     (t/month d)
     :date/day       (t/day d)}))

(defn today []
  (let [t (t/today)]
    (t/date-time (t/year t) (t/month t) (t/day t))))

(defn tomorrow []
  (let [t (today)]
    (t/plus t (t/days 1))))

(defn first-day-of-this-month []
  (c/to-date-time (t/first-day-of-the-month (today))))

(defn first-day-of-month [n]
  (let [t (today)]
    (c/to-date-time (t/first-day-of-the-month (t/year t) n))))

(defn last-day-of-this-month []
  (c/to-date-time (t/last-day-of-the-month (today))))

(defn last-day-of-month [n]
  (let [t (today)]
    (c/to-date-time (t/last-day-of-the-month (t/year t) n))))

(defn first-day-of-this-year []
  (let [t (today)]
    (t/date-time (t/year t) 1 1)))

(defn last-day-of-this-year []
  (let [t (today)]
    (t/date-time (t/year t) 12 31)))

(defn now []
  (t/now))

(defn days-ago [n]
  (let [t (today)]
    (t/minus t (t/days n))))

(defn days-from-now [n]
  (let [t (today)]
    (t/plus t (t/days n))))

(defn date-time->long [obj]
  (let [d (date-time obj)]
    (c/to-long d)))

(defn date->long [obj]
  (if (some? (:date/timestamp obj))
    (:date/timestamp obj)
    (let [d (date-time obj)]
      (c/to-long (t/date-time (t/year d) (t/month d) (t/day d))))))

(defn month->long [obj]
  (let [d (date-time obj)]
    (c/to-long (t/date-time (t/year d) (t/month d)))))

(defn date->string [obj & [format-str]]
  (let [d (date-time obj)]
    (debug "Got date: " d)
    (f/unparse (f/formatter (or format-str "MM/dd/yyyy")) d)))

(defn current-millis []
  (c/to-long (now)))

(defn current-secs []
  (let [now (c/to-long (now))]
    #?(:clj  (int (with-precision 10
                    (/ now 1000)))
       :cljs (int (/ now 1000)))))

(defn interval [a b]
  (t/interval (date-time a) (date-time b)))

(defn in-minutes [inter]
  (t/in-minutes inter))

(defn in-hours [inter]
  (t/in-hours inter))




