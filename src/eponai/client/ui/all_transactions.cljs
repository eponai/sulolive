(ns eponai.client.ui.all_transactions
  (:require [eponai.client.ui.format :as f]
            [eponai.client.parser :as parser]
            [eponai.client.ui.transaction :as trans]
            [eponai.client.ui :refer [style]]
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
         {:key "transactions"}
         (->>
           (rseq by-year-month)
           (map
             (fn [[year months]]
               [:div
                {:key   (str "transactions-by-year=" year)
                 :class "row"}
                [:span
                 {:key   (str "transaction-span-year=" year)
                  :class "col-sm-1 network-text"} year]
                [:div
                 {:key (str "transactions-col-year=" year)
                  :class "col-sm-11"}
                 (->>
                   (rseq months)
                   (map
                     (fn [[month dates]]
                       [:div
                        {:key   (str "transactions-by-year=" year "-month=" month)
                         :class "row"}
                        [:span
                         {:key   (str "transactions-h2-year=" year "-month=" month)
                          :class "col-sm-1 network-text"}
                         (f/month-name month)]
                        [:div
                         {:key   (str "transactions-" year "-" month "-col")
                          :class "panel-group col-sm-11"}
                         [:style
                          (css [:#ui-day {:background-color "#fff"}
                                [:&:hover {:background-color "rgb(250,250,250)"}]
                                [:&:active {:background-color "#eee"}]])]
                         (map
                           (fn [date]
                             (let [{transactions :transaction/_date
                                    :keys        [::day-expanded?]} date]
                               [:div
                                {:class "panel panel-default"}
                                [:div
                                 {:class    "panel-heading"
                                  :id       "ui-day"
                                  :on-click #(parser/cas! this
                                                          (:db/id date)
                                                          ::day-expanded?
                                                          (::day-expanded? date)
                                                          (not day-expanded?))}
                                 [:span#weekday
                                  {:class "lead col-md-4"}
                                  (str (f/day-name date) "  " (:date/day date))]

                                 [:span#daily-amount
                                  {:class "lead"}
                                  (let [{:keys [amount currency]} (sum transactions)]
                                    (str amount " " currency))]]

                                (when day-expanded?
                                  [:div#day-transactions {:class "panel-body"}
                                   (map trans/->Transaction transactions)])]))
                           (->> dates
                                (sort-by :date/day)
                                (rseq)))]])))]])))]))))

(def ->AllTransactions (om/factory AllTransactions))
