(ns ^:figwheel-always eponai.client.client_tests
  (:require [cljs.test :refer-macros [run-tests]]
            [eponai.client.datascript_test]))

(defn ^:export run []
  (run-tests
    'eponai.client.datascript_test))

