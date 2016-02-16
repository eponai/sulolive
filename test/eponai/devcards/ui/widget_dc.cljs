(ns eponai.devcards.ui.widget-dc
  (:require [eponai.client.ui.dashboard :as dashboard]
            [om.next :as om])
  (:require-macros [devcards.core :refer [defcard]]))

(defn props [{:keys [input-style input-group-by input-function]}]
  {:widget/graph {:graph/style input-style
                  :graph/report {:report/group-by input-group-by
                                 :report/function input-function}}})
(defn data-sum-number []
  {:key    "Number Chart"
   :values [0 1435]})

(defn data-bar-chart-sum []
  [{:key "Bar chart"
    :values [{:name "one" :value 123}
             {:name "two" :value 432}
             {:name "three" :value 232}]}])

(defn data-area-chart-sum []
  [{:key "Area chart"
    :values [{:name 1040000 :value 123}
             {:name 2041000 :value 432}
             {:name 3052000 :value 123}
             {:name 4063000 :value 324}
             {:name 5074000 :value 234}
             {:name 6084000 :value 234}
             {:name 7094000 :value 543}
             {:name 8074000 :value 624}
             {:name 9074000 :value 765}
             {:name 10074000 :value 234}
             {:name 11074000 :value 543}]}])

(defcard
  number-chart-widget
  (dashboard/->Widget (om/computed (props {:input-style    :graph.style/number
                                           :input-group-by :default
                                           :input-function {:report.function/id        :report.function.id/sum
                                                            :report.function/attribute :transaction/amount}})
                                   {:data-report (fn [_] (data-sum-number))})))

(defcard
  bar-chart-widget
  (dashboard/->Widget (om/computed (props {:input-style    :graph.style/bar
                                           :input-group-by :transaction/tags
                                           :input-function {:report.function/id        :report.function.id/sum
                                                            :report.function/attribute :transaction/amount}})
                                   {:data-report (fn [_] (data-bar-chart-sum))})))

(defcard
  area-chart-widget
  (dashboard/->Widget (om/computed (props {:input-style    :graph.style/area
                                           :input-group-by :transaction/tags
                                           :input-function {:report.function/id        :report.function.id/sum
                                                            :report.function/attribute :transaction/amount}})
                                   {:data-report (fn [_] (data-area-chart-sum))})))


