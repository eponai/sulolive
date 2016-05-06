(ns eponai.common.format.date-test
  (:require
    [eponai.common.format.date :as date]
    #?(:clj [clojure.test :refer :all]
       :cljs [cljs.test :refer-macros [deftest testing is are]])
    #?(:clj [clj-time.core :as t]
       :cljs [cljs-time.core :as t])
    #?(:clj [clj-time.coerce :as c]
       :cljs [cljs-time.coerce :as c])))


;; clj:  (c/to-long (c/to-date-time "2015-10-10"))       => 1444435200000
;; cljs: (c/to-long (c/to-date-time "2015-10-10"))       => 1444435200000
;; clj:  (c/to-long (c/to-local-date-time "2015-10-10")) => 1444435200000
;; cljs: (c/to-long (c/to-local-date-time "2015-10-10")) => 1444406400000 !!
;; clj:  (c/to-long (fo/parse-local "2015-10-10")) => 1444435200000
;; cljs: (c/to-long (fo/parse-local "2015-10-10")) => 1444406400000 !!

(def test-date {:date/ymd       "2015-10-03"
                :date/timestamp 1443830400000
                :date/day       3
                :date/month     10
                :date/year      2015})

(deftest test-date-formatting-results-same
  (testing "Formatting dates results in the same values in clj and cljs."
    (let [t1 (date/today)]
      ;; Verify that formatting today to and from long gives back the same day
      ;; (can fail using cljs-time's t/today instead of date/today).
      ;; Also verify date-time returns correctly if passed a t/today object.
      (are [in out] (t/equal? in out)
                    t1 (c/from-long (c/to-long t1))
                    t1 (date/date-time (t/today)))

      ;; Verify that creating a date with a string and a map will give the same exact long value in clj and cljs
      ;; (this can fail using local dates in cljs-time).
      ;; Also verify that date->long works and gives the same result when passed the same string and map.
      (is (= (:date/timestamp test-date)
             (c/to-long (date/date-time test-date))
             (c/to-long (date/date-time (:date/ymd test-date)))
             (date/date->long test-date)
             (date/date->long (:date/ymd test-date))))

      ;; Verify that given a js/Date a date will be created with the exact same long in clj and cljs.
      ;; ALso verify date->long works for js/Dates.
      #?(:cljs (is (= (:date/timestamp test-date)
                      (c/to-long (date/date-time (js/Date. (:date/ymd test-date))))
                      (date/date->long (js/Date. (:date/ymd test-date))))))

      ;; Verify that given a string, date-map will return a correct map representing the date
      (is (= (date/date-map (:date/ymd test-date)) test-date))

      ;; Verify that given a js/Date, date-map will return a correct map representing the date
      #?(:cljs (is (= (date/date-map (js/Date. (:date/ymd test-date))) test-date))))))