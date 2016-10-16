(ns eponai.common.diff-test
  (:require [eponai.common.diff :as diff]
            [clojure.test #?(:clj  :refer
                             :cljs :refer-macros) [is]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop #?@(:cljs [:include-macros true])]
            [clojure.test.check.clojure-test #?(:clj  :refer
                                                :cljs :refer-macros) [defspec]]))

(def gen-nested-boolean-structure
  (gen/recursive-gen (fn [inner-type]
                       (gen/one-of [(gen/not-empty (gen/vector inner-type))
                                    (gen/not-empty (gen/list inner-type))
                                    (gen/not-empty (gen/set inner-type))
                                    (gen/not-empty (gen/map inner-type
                                                            inner-type))]))
                     gen/boolean))

(defspec diff-anything-can-be-undiffed
         100
         (prop/for-all [[a b] (gen/tuple gen-nested-boolean-structure
                                         gen-nested-boolean-structure)]
                       (let [diff (diff/diff a b)
                             a' (diff/undiff b diff)]
                         (is (= a a')
                             (str "a: " a
                                  " was not a': " a'
                                  " with b: " b)))))
