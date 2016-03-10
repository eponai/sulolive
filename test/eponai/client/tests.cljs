(ns ^:figwheel-always eponai.client.tests
  (:require [cljs.test]
            [cljsjs.react]
            [eponai.common.datascript_test]
            [eponai.web.ui.add_transaction_test]
            [eponai.web.routes-test]
            [doo.runner :refer-macros [doo-tests]]
            [taoensso.timbre :refer-macros [info error]]))

(defn ^:export run []
  (if-let [test-fn *main-cli-fn*]
    (do
      ;; Override doo.runner's cljs.test/report, as it will call exit
      ;; when we run tests.
      (defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
        (if (cljs.test/successful? m)
          (info "Tests: PASS")
          (error "Tests: FAILED")))

      (test-fn))
    (prn "WARNING: No test function set!")))

(enable-console-print!)
;; doo-tests sets *main-cli-fn* to a function that runs tests.
(doo-tests 'eponai.common.datascript_test
           'eponai.web.ui.add_transaction_test
           'eponai.web.routes-test)
