(ns eponai.client.ui.all_transactions
  (:require [eponai.client.ui.format :as f]
            [eponai.common.parser.mutate :as mutate]
            [eponai.client.ui.transaction :as trans]
            [eponai.client.ui :refer [map-all] :refer-macros [style opts]]
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

(defui Filters
  Object
  )

(defui AllTransactions
  static om/IQuery
  (query [_]
    [{:query/all-transactions
      (conj
        (om/get-query trans/Transaction)
        {:transaction/date [:db/id
                            :date/ymd
                            :date/day
                            :date/month
                            :date/year
                            ::day-expanded?]}
        {:transaction/budget [:budget/uuid
                              :budget/name]})}])

  Object
  (render [this]
    (let [{transactions :query/all-transactions} (om/props this)]
      (html
        [:div
         [:table
          {:class "table table-striped"}
          [:thead
           [:tr
            [:td "Date"]
            [:td "Name"]
            [:td.text-center "Budget"]
            [:td.text-right
             "Amount"]]]
          [:tbody
           (map-all
             (reverse transactions)
             (fn [{:keys [transaction/date
                          transaction/currency
                          transaction/amount]
                   :as   transaction}]
               [:tr
                (opts {:key [(:transaction/uuid transaction)]})

                [:td
                 (opts {:key [(:date/ymd date)]})
                 (str (f/month-name (:date/month date)) " " (:date/day date))]

                [:td
                 (opts {:key [(:transaction/name transaction)]})
                 (:transaction/name transaction)]

                [:td.text-center
                 (opts {:key [(:budget/name (:transaction/budget transaction))]})

                 (or (:budget/name (:transaction/budget transaction))
                     "Untitled")]
                [:td.text-right
                 (opts {:key [amount]})
                 (str amount " " (or (:currency/symbol-native currency)
                                     (:currency/code currency)))]
                ;[:td
                ; (opts {:style {:display "flex"
                ;                :flex-direction "row-reverse"}})
                ;
                ; [:button {:class "btn btn-default btn-xs"}
                ;  [:span {:class "glyphicon glyphicon-edit"}]]]
                ]
               ;[:div#year-panel
               ; (opts {:key   [year]
               ;        :style {:display         "flex"
               ;                :flex-direction  "row"
               ;                :justify-content "flex-start"
               ;                :align-items     "flex-start"
               ;                :width           "100%"}})
               ;
               ; [:span#year
               ;  (opts {:key   [year]
               ;         :class "network-name"
               ;         :style {:margin-right "0.5em"}})
               ;  year]
               ;
               ; [:div#months
               ;  (opts {:key   [year]
               ;         :style {:width "100%"}})
               ;  (map-all (rseq (into (sorted-map) months))
               ;    (fn [[month days]]
               ;      [:div#month
               ;       (opts {:key   [year month]
               ;              :style {:display        "flex"
               ;                      :flex-direction "row"
               ;                      :align-items    "flex-start"
               ;                      :width          "100%"}})
               ;
               ;       [:p#month-name
               ;        (opts {:key   [year month]
               ;               :class "network-name"})
               ;        (f/month-name month)]
               ;
               ;       [:div#days
               ;        (opts {:key   [year month]
               ;               :class "panel-group"
               ;               :style {:width "100%"}})
               ;        (map-all (rseq (into (sorted-map) days))
               ;          (fn [[day {:keys [date transactions]}]]
               ;            [:div#day
               ;             (opts {:key   [year month day]
               ;                    :class "panel panel-info"})
               ;
               ;             [:div#weekday
               ;              (opts {:key      [year month day]
               ;                     :class    "panel-heading list-group-item"
               ;                     :style    {:display         "flex"
               ;                                :flex-direction  "row"
               ;                                :justify-content "space-between"}
               ;                     :on-click #(mutate/cas!
               ;                                 this
               ;                                 (:db/id date)
               ;                                 ::day-expanded?
               ;                                 (::day-expanded? date)
               ;                                 (not (::day-expanded? date)))})
               ;              [:span#dayname
               ;               (opts {:key   [year month day]
               ;                      :style {:margin-right "0.3em"}})
               ;               (str (f/day-name date) " " day)]
               ;
               ;              [:span#daysum
               ;               (opts {:key [year month day]})
               ;               (let [{:keys [currency amount]} (sum transactions)]
               ;                 (str amount " " (or (:currency/symbol-native currency)
               ;                                     (:currency/code currency))))]]
               ;
               ;             (when (::day-expanded? date)
               ;               [:div#transactions
               ;                (opts {:key   [year month day]
               ;                       :class "panel-body"})
               ;
               ;                (map trans/->Transaction transactions)])]
               ;            ))]]))]]
               ))]]]))))

(def ->AllTransactions (om/factory AllTransactions))
