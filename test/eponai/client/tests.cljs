(ns ^:figwheel-always eponai.client.tests
  (:require [cljs.test]
            [cljsjs.react]
            [eponai.common.datascript_test]
            [eponai.common.diff-test]
            [eponai.common.format.date-test]
            [doo.runner :refer-macros [doo-tests]]
            [taoensso.timbre :refer-macros [info error]]))

(defn ^:export run []
  (if-let [test-fn *main-cli-fn*]
    (let [test-report (atom nil)]
      ;; Override doo.runner's cljs.test/report, as it will call exit
      ;; when we run tests.
      (defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
        (reset! test-report m)
        (if (cljs.test/successful? m)
          (info "Tests: PASSED <3")
          (error "Tests: FAILED")))

      (test-fn)
      @test-report)
    (prn "WARNING: No test function set!")))

(enable-console-print!)
;; doo-tests sets *main-cli-fn* to a function that runs tests.
(doo-tests 'eponai.common.datascript_test
           'eponai.common.format.date-test
           'eponai.common.diff-test
           )

