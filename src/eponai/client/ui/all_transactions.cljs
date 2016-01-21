(ns eponai.client.ui.all_transactions
  (:require [eponai.client.ui.format :as f]
            [eponai.common.parser.mutate :as mutate]
            [eponai.client.ui.transaction :as trans]
            [eponai.client.ui.datepicker :refer [->Datepicker]]
            [eponai.client.ui :refer [map-all] :refer-macros [style opts]]
            [garden.core :refer [css]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [eponai.client.ui.tag :as tag]))

;; Transactions grouped by a day

(defn sum
  "Adds transactions amounts together.
  TODO: Needs to convert the transactions to the 'primary currency' some how."
  [transactions]
  {:amount   (transduce (map :transaction/amount) + 0 transactions)
   ;; TODO: Should instead use the primary currency
   :currency (-> transactions first :transaction/currency)})

(defn delete-tag-fn [component name k]
  (fn []
    (om/update-state! component update k
                      (fn [tags]
                        (into #{}
                              (remove #(= name %))
                              tags)))))

(defn on-add-tag-key-down [this input-tag]
  (fn [key]
    (when (and (= 13 (.-keyCode key))
               (seq (.. key -target -value)))
      (.preventDefault key)
      (om/update-state! this
                        #(-> %
                             (assoc :input-tag "")
                             (update :filter-tags conj input-tag))))))

(defn on-change [this k]
  #(om/update-state! this assoc k (.-value (.-target %))))

(defui Filters
  Object
  (initLocalState [_]
    {:filter-tags #{}
     :input-tag ""
     :input-date (js/Date.)})
  (render [this]
    (let [{:keys [input-tag
                  filter-tags
                  input-date]} (om/get-state this)]
      (html
        [:div
         (opts {:style {:display        "flex"
                        :flex-direction "column"}})

         ;[:br]
         [:div.has-feedback
          [:input.form-control
           {:type        "text"
            :value       input-tag
            :on-change   (on-change this :input-tag)
            :on-key-down (on-add-tag-key-down this input-tag)
            :placeholder "Filter tags..."}]
          [:span
           {:class "glyphicon glyphicon-tag form-control-feedback"}]]

         [:div#date-input
          (->Datepicker
            (opts {:value     input-date
                   :on-change #(om/update-state! this assoc :input-date %)}))]

         [:div
          (opts {:style {:display         "flex"
                         :flex-direction  "row"
                         :width           "100%"}})
          (map-all
            filter-tags
            (fn [tagname]
              (tag/->Tag (tag/tag-props tagname
                                        (delete-tag-fn this tagname :filter-tags)))))]

         ]))))

(def ->Filters (om/factory Filters))

(defui AllTransactions
  static om/IQueryParams
  (params [_]
    {:where '[[?e :transaction/uuid]]})
  static om/IQuery
  (query [_]
    [{'(:query/all-transactions {:where ?where})
      [:db/id
       :transaction/uuid
       :transaction/name
       :transaction/amount
       :transaction/details
       :transaction/status
       {:transaction/currency [:currency/code
                               :currency/symbol-native]}
       {:transaction/tags (om/get-query tag/Tag)}
       ::transaction-show-tags?
       {:transaction/date [:db/id
                           :date/ymd
                           :date/day
                           :date/month
                           :date/year
                           ::day-expanded?]}
       {:transaction/budget [:budget/uuid
                             :budget/name]}]}])

  Object
  (initLocalState [_]
    {:active-tab :filter})
  (render [this]
    (let [{transactions :query/all-transactions} (om/props this)
          {:keys [active-tab]} (om/get-state this)]
      (html
        [:div
         (opts {:style {:display "flex"
                        :flex-direction "column"
                        :align-items "flex-start"
                        :width "100%"}})
         [:div
          (opts {:style {:display        "flex"
                         :flex-direction "row"}})
          [:a
           [:button
            {:class "form-control btn btn-default"}
            [:i {:class "glyphicon glyphicon-filter"}]]]
          ;[:button {:class "btn btn-default btn-sm"}]
          ;[:a]
          [:div.has-feedback
           [:input.form-control
            {:type        "text"
             :placeholder "Search..."}]
           [:span {:class "glyphicon glyphicon-search form-control-feedback"}]]
          ]

         [:br]
         [:div.tab-content
          (if (= active-tab :filter)
            (->Filters))]

         [:table
          {:class "table table-striped table-hover"}
          [:thead
           [:tr
            [:td "Date"]
            [:td "Name"]
            [:td "Tags"]
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

                [:td
                 (opts {:key [(:transaction/tags transaction)]})
                 (map tag/->Tag (:transaction/tags transaction))]
                [:td.text-right
                 (opts {:key [amount]})
                 (str amount " " (or (:currency/symbol-native currency)
                                     (:currency/code currency)))]]))]]]))))

(def ->AllTransactions (om/factory AllTransactions))
