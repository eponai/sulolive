(ns ^:figwheel-always eponai.client.tests
  (:require [cljs.test :refer-macros [run-tests]]
            [cljsjs.react]
            [eponai.client.ui.add_transaction_test]))

(defn ^:export run []
  (run-tests
    'eponai.client.ui.add_transaction_test))

(enable-console-print!)
(set! *main-cli-fn* run)