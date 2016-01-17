(ns eponai.client.ui.all_transactions
  (:require [eponai.client.ui.format :as f]
            [eponai.common.parser.mutate :as mutate]
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

(defn render-day [this day {:keys [date transactions]}]
  [:div#day
   (opts {:key   [day]
          :class "panel panel-info"})

   [:div#weekday
    (opts {:key      [day]
           :class    "panel-heading list-group-item"
           :style    {:display         "flex"
                      :flex-direction  "row"
                      :justify-content "space-between"}
           :on-click #(mutate/cas!
                       this
                       (:db/id date)
                       ::day-expanded?
                       (::day-expanded? date)
                       (not (::day-expanded? date)))})
    [:span#dayname
     (opts {:key   [day]
            :style {:margin-right "0.3em"}})
     (str (f/day-name date) " " day)]

    [:span#daysum
     (opts {:key [day]})
     (let [{:keys [currency amount]} (sum transactions)]
       (str amount " " (or (:currency/symbol-native currency)
                           (:currency/code currency))))]]

   (when (::day-expanded? date)
     [:div#transactions
      (opts {:key   [day]
             :class "panel-body"})

      (map trans/->Transaction transactions)])])

(defn render-month [this month days]
  [:div#month
   (opts {:key   [month]
          :style {:display        "flex"
                  :flex-direction "row"
                  :align-items    "flex-start"
                  :width          "100%"}})

   [:p#month-name
    (opts {:key   [month]
           :class "network-name"})
    (f/month-name month)]

   [:div#days
    (opts {:key   [month]
           :class "panel-group"
           :style {:width "100%"}})
    (for [[day transactions] (rseq (into (sorted-map) days))]
      (render-day this day transactions))]])

(defn render-year [this year months]
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
    (for [[month days] (rseq (into (sorted-map) months))]
      (render-month this month days))]])

(defui AllTransactions
  static om/IQuery
  (query [_]
    (vec (concat [{:transaction/date [:db/id
                                      :date/ymd
                                      :date/day
                                      :date/month
                                      :date/year
                                      ::day-expanded?]}]
                 (om/get-query trans/Transaction))))

  Object
  (render [this]
    (let [transactions (om/props this)
          by-year-month-day (f/transactions-by-year-month-day transactions)]
      (html
        [:div
         (for [[year months] (rseq by-year-month-day)]
           (render-year this year months))]))))

(def ->AllTransactions (om/factory AllTransactions))
