(ns eponai.common.format.date
  (:require
    #?(:clj [clj-time.core :as t]
       :cljs [cljs-time.core :as t])
    #?(:clj [clj-time.coerce :as c]
       :cljs [cljs-time.coerce :as c])))


(defn today []
  (let [t (t/today)]
    (t/date-time (t/year t) (t/month t) (t/day t))))

(defn day->long [d]
  (c/to-long (t/date-time (t/year d) (t/month d) (t/day d))))

(defn month->long [d]
  (c/to-long (t/date-time (t/year d) (t/month d))))