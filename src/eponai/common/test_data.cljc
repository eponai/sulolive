(ns eponai.common.test-data
  #?(:cljs (:require-macros
             [eponai.common.test-data :refer [inline-test-data]])))

#?(:clj
   (defmacro inline-test-data
     []
     (clojure.edn/read-string (slurp (clojure.java.io/resource "private/mocked-data.edn")))))

(def mocked-data (inline-test-data))