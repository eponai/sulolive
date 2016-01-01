(ns eponai.client.ui.all_transactions
  (:require [eponai.client.ui.format :as f]
            [eponai.client.parser :as parser]
            [eponai.client.ui.transaction :as trans]
            [eponai.client.ui :refer-macros [style opts]]
            [garden.core :refer [css]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]))

;; Transactions grouped by a day

(defn sum
  "Adds transactions amounts together.
  TODO: Needs to convert the transactions to the 'primary currency' some how."
  [transactions]
  {:amount   (transduce (map :transaction/amount) + 0 transactions)
   ;; TODO: Should instead use the primary currency
   :currency (-> transactions first :transaction/currency)})

(defui AllTransactions
  static om/IQuery
  (query [_]
    [{:query/all-dates
      ['*
       {:transaction/_date (om/get-query trans/Transaction)}
       ::day-expanded?]}])

  Object
  (render [this]
    (let [{:keys [query/all-dates]} (om/props this)
          by-year-month (f/dates-by-year-month all-dates)]
      (html
        [:div
         (map
           (fn [[year months]]
             [:div#year-panel
              (opts {:key   [year]
                     :style {:display         "flex"
                             :flex-direction  "row"
                             :justify-content "flex-start"
                             :align-items     "flex-start"
                             :width           "100%"}})

              [:span#year
               (opts {:key   [year]
                      :class "network-name"
                      :style {:margin-right "0.5em"}})
               year]

              [:div#months
               (opts {:key   [year]
                      :style {:width "100%"}})
               (map
                 (fn [[month days]]
                   [:div#month
                    (opts {:key   [year month]
                           :style {:display        "flex"
                                   :flex-direction "row"
                                   :align-items    "flex-start"
                                   :width          "100%"}})

                    [:p#month-name
                     (opts {:key   [year month]
                            :class "network-name"})
                     (f/month-name month)]

                    [:div#days
                     (opts {:key   [year month]
                            :class "panel-group"
                            :style {:width "100%"}})
                     (map
                       (fn [date]
                         [:div#day
                          (opts {:key   [year month date]
                                 :class "panel panel-info"})

                          [:div#weekday
                           (opts {:key      [year month date]
                                  :class    "panel-heading list-group-item"
                                  :style    {:display         "flex"
                                             :flex-direction  "row"
                                             :justify-content "space-between"}
                                  :on-click #(parser/cas!
                                              this
                                              (:db/id date)
                                              ::day-expanded?
                                              (::day-expanded? date)
                                              (not (::day-expanded? date)))})
                           [:span#dayname
                            (opts {:key   [year month date]
                                   :style {:margin-right "0.3em"}})
                            (str (f/day-name date) " " (:date/day date))]

                           [:span#daysum
                            (opts {:key [year month date]})
                            (let [{:keys [currency amount]} (sum (:transaction/_date date))]
                              (str amount " " (or (:currency/symbol-native currency)
                                                  (:currency/code currency))))]]

                          (when (::day-expanded? date)
                            [:div#transactions
                             (opts {:key   [year month date]
                                    :class "panel-body"})
                             (map trans/->Transaction
                                  (:transaction/_date date))])])
                       (rseq days))]])
                 (rseq months))]])
           (rseq by-year-month))]))))

(def ->AllTransactions (om/factory AllTransactions))
