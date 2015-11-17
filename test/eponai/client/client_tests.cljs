(ns ^:figwheel-always flipmunks.budget.client_tests
  (:require [cljs.test :refer-macros [run-tests]]
            [flipmunks.budget.datascript_test]))

(defn ^:export run []
  (run-tests
    'flipmunks.budget.datascript_test))

