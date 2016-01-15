(ns ^:figwheel-always eponai.client.tests
  (:require [cljs.test :refer-macros [run-tests]]
            [cljsjs.react]
            [eponai.common.datascript_test]
            [eponai.client.ui.add_transaction_test]))

(def test-result (atom {}))

(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (reset! test-result {:success (cljs.test/successful? m)})
  (prn "test report:")
  (prn m))

(defn ^:export run []
  (run-tests
    'eponai.client.ui.add_transaction_test
    'eponai.common.datascript_test))

(defn ^:export node-run-tests []
  (run)
  (let [s (:success @test-result)]
    (prn "All tests passed=" s)
    ;; Node specific code to exit the process with exit code 1
    (when-not s
      (.exit js/process 1))))

(enable-console-print!)
(set! *main-cli-fn* node-run-tests)
