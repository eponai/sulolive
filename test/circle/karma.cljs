(ns circle.karma)

(enable-console-print!)

(defn ^:export run-tests-for-karma []
  (.info js/__karma__ (clj->js {:total 1}))
  (.result js/__karma__ (clj->js {:id "no-op"
                                  :description "test test"
                                  :suite "circle.karma"
                                  :success true
                                  :skipped nil
                                  :time 1
                                  :log ["testing" "the" "thing"]}))
  (prn "running tests for karma")
  (.complete js/__karma__ (clj->js {})))

