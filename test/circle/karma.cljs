(ns circle.karma)

(enable-console-print!)

(defn ^:export run-tests-for-karma []
  (.info js/__karma__ (clj->js {:total 0}))
  (prn "running tests for karma")
  (.complete js/__karma__ (clj->js {})))

