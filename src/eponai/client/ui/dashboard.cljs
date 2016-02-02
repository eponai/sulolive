(ns eponai.client.ui.dashboard
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [eponai.client.ui :refer [map-all] :refer-macros [style opts]]
            [eponai.client.ui.d3 :as d3]
            [cljs.core.async :as c :refer [chan]]
            [garden.core :refer [css]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]))

(defn sum-by-day [transactions]
  (let [grouped (group-by :transaction/date transactions)
        sum-fn (fn [[day ts]]
                 (assoc day :date/sum (reduce #(+ %1 (:transaction/amount %2)) 0 ts)))]
    (map sum-fn grouped)))

(defn sum-by-tag [transactions]
  (let [sum-fn (fn [m transaction]
                 (let [tags (:transaction/tags transaction)]
                   (reduce (fn [m2 tagname]
                             (update m2 tagname
                                     #(if %
                                       (+ % (:transaction/amount transaction))
                                       (:transaction/amount transaction))))
                           m
                           (if (empty? tags)
                             ["no tags"]
                             (map :tag/name tags)))))]
    (reduce sum-fn {} transactions)))

(defui Dashboard
  static om/IQuery
  (query [_]
    ['{:query/one-budget [:budget/uuid
                          {:transaction/_budget
                           [:transaction/uuid
                            {:transaction/date
                             [:date/ymd
                              :date/timestamp]}
                            :transaction/amount
                            {:transaction/tags [:tag/name]}]}]}])
  Object
  (render [this]
    (let [{:keys [query/one-budget]} (om/props this)
          sum-by-day (sum-by-day (:transaction/_budget one-budget))
          sum-by-tag (sum-by-tag (:transaction/_budget one-budget))]
      (html
        [:div
         [:p
          [:span "This is the dashboard for budget "]
          [:strong (str (:budget/uuid one-budget))]]
         (d3/->AreaChart {:data   (map #(clojure.set/rename-keys %
                                                                {:date/ymd :name
                                                                 :date/sum :value})
                                      (sort-by :date/timestamp sum-by-day))
                         :width  900
                         :height 300
                         :title-axis-y "Amount ($)"})
         (d3/->BarChart {:data   (reduce #(conj %1 {:name (first %2) :value (second %2)}) [] sum-by-tag)
                         :width  900
                         :height 300
                         :title-axis-y "Amount ($)"})]))))

(def ->Dashboard (om/factory Dashboard))
