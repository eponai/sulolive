(ns circle.karma
  (:require [cljs.test :as test :refer-macros [is deftest run-tests]]))

(defmethod test/report :default [m]
  (prn "no report method for type: " (:type m)))

(defmethod test/report [::reporter :summary] [m]
  (prn "SUMMARY: " m)
  (.result js/__karma__ (clj->js {:id "no-op"
                                  :description "test test"
                                  :suite "circle.karma"
                                  :success true
                                  :skipped nil
                                  :time 1
                                  :log ["testing" "the" "thing"]})))

(deftest foo
  (is (= 1 1)
      (= 1 1)))

(defn ^:export run-tests-for-karma []
  (enable-console-print!)
  (run-tests (assoc (test/empty-env) :reporter ::reporter))
  (prn "running tests for karma")
  (.complete js/__karma__ (clj->js {})))

