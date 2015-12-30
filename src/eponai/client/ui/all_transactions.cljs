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
   :currency (-> transactions first :transaction/currency :currency/code)})

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
         {:class "container"}
         [:div
          {:class "panel-group"}
          (map
            (fn [[year months]]
              [:div#year-panel
               {:class "row"}

               [:span#year
                {:class "col-xs-1 network-name"}
                year]

               [:div#months
                {:class "col-xs-11"}
                (map
                  (fn [[month days]]
                    [:div#month

                     [:span#month-name
                      {:class "col-xs-3 col-sm-1 network-name"}
                      (f/month-name month)]

                     [:div#days {:class "col-xs-9 col-sm-11 network-text"}
                      (map
                        (fn [date]
                          [:div
                           [:div#day
                            (opts {:class "row panel panel-default"})

                            [:div#weekday
                             {:class    "col-xs-4 col-sm-2"
                              :on-click #(parser/cas!
                                          this
                                          (:db/id date)
                                          ::day-expanded?
                                          (::day-expanded? date)
                                          (not (::day-expanded? date)))}
                             [:span#dayname
                              (opts {:style {:margin-right "0.3em"}})
                              (f/day-name date)]

                             [:span#date
                              (opts {:style {:margin-right "1.0em"}})
                              (:date/day date)]

                             [:p#daysum
                              {:class "text-success"}
                              (let [{:keys [currency
                                            amount]} (sum (:transaction/_date date))]
                                (str currency " " amount))]]

                            [:style
                             (css [:#weekday {:background-color "#fff"}
                                   [:&:hover {:background-color "rgb(250,250,250)"}]
                                   [:&:active {:background-color "#eee"}]])]

                            [:div#transactions
                             {:class "col-xs-8 col-sm-10"}
                             (map trans/->Transaction
                                  (:transaction/_date date))]]])
                        days)]])
                  (rseq months))]])
            (rseq by-year-month))]]))))
(def ->AllTransactions (om/factory AllTransactions))
