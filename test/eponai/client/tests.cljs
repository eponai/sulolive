(ns ^:figwheel-always eponai.client.tests
  (:require [cljs.test :refer-macros [run-tests]]
            [eponai.common.datascript_test]
            [eponai.client.ui.add_transaction_test]
            [eponai.client.app :as app]))

(defn ^:export run []
  (run-tests
    'eponai.common.datascript_test
    'eponai.client.ui.add_transaction_test)
  (app/run))

