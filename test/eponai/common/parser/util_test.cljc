(ns eponai.common.parser.util_test
  (:require
    [eponai.common.parser.util :as u]
    [clojure.test :as test :refer [are is deftest]])
  #?(:clj
     (:import [clojure.lang ExceptionInfo])))

(deftest graph-read-at-basis-t-tests
  (let [graph (u/graph-read-at-basis-t)
        set-many (fn [graph setter-params]
                   (reduce (fn [g {:keys [basis-t params]}]
                             {:pre [(every? vector? params)]}
                             (u/set-basis-t g :foo basis-t params))
                           graph
                           setter-params))]
    (is (= nil (u/get-basis-t graph :foo [])))
    (test/testing "single set, can get basis t with same or fewer params"
      (are [set-params get-params] (= 47 (-> graph
                                             (u/set-basis-t :foo 47 set-params)
                                             (u/get-basis-t :foo get-params)))
        [] nil
        [] []
        [[:bar 1]] nil
        [[:bar 1]] []
        [[:bar 1]] {:bar 1}
        [[:bar 1]] [[:bar 1]]
        [[:bar 1] [:baz 2]] []
        [[:bar 1] [:baz 2]] {:bar 1}
        [[:bar 1] [:baz 2]] [[:bar 1]]
        [[:bar 1] [:baz 2]] {:bar 1 :baz 2}
        [[:bar 1] [:baz 2]] [[:bar 1] [:baz 2]]))
    (test/testing "multiple sets, can get basis-t with fewer params iff there's only one path"
      (are [setters get-params basis-t-or-msg]
           (try
             (and (= basis-t-or-msg
                     (-> (set-many graph setters)
                         (u/get-basis-t :foo get-params))))
             (catch #?@(:clj [Exception e] :cljs [ExceptionInfo e])
                    (or (re-find basis-t-or-msg #?(:cljs (.-message e)
                                                      :clj (.getMessage e)))
                        (throw e))))
        [{:basis-t 1 :params [[:bar 1]]}
         {:basis-t 2 :params [[:bar 2]]}]
        []
        #"multiple values"

        [{:basis-t 47 :params [[:bar 1]]}
         {:basis-t 11 :params [[:bar 2]]}]
        {:bar 1}
        47

        [{:basis-t 47 :params [[:bar 1]]}
         {:basis-t 11 :params [[:bar 2]]}]
        {:bar "foo"}
        nil

        ;; Need to set params in the same order, always.
        [{:basis-t 47 :params [[:bar 1] [:baz 1]]}
         {:basis-t 11 :params [[:baz 2]]}]
        {}
        #"order"

        ;; Cannot add a key once a param has been set.
        [{:basis-t 47 :params [[:bar 1]]}
         {:basis-t 11 :params [[:bar 1] [:baz 1]]}]
        {:bar 1 :baz 1}
        #"additional keys"

        ))))