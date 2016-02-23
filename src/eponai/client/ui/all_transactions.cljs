(ns eponai.client.ui.all_transactions
  (:require [eponai.client.ui.format :as f]
            [eponai.client.ui.datepicker :refer [->Datepicker]]
            [eponai.client.ui :refer [map-all update-query-params!] :refer-macros [style opts]]
            [eponai.common.format :as format]
            [garden.core :refer [css]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [eponai.client.ui.tag :as tag]))

(defn sum
  "Adds transactions amounts together.
  TODO: Needs to convert the transactions to the 'primary currency' some how."
  [transactions]
  {:amount   (transduce (map :transaction/amount) + 0 transactions)
   ;; TODO: Should instead use the primary currency
   :currency (-> transactions first :transaction/currency)})

(defn update-tags [component action tag-name]
  (update-query-params! component update :filter-tags
                        (fn [tags]
                          (action tags tag-name))))

(defn delete-tag-fn [component name]
  (fn []
    (update-tags component disj name)))

(defn add-tag [component name]
  (update-tags component conj name))

(defn on-add-tag-key-down [this input-tag]
  (fn [e]
    (when (and (= 13 (.-keyCode e))
               (seq (.. e -target -value)))
      (.preventDefault e)
      (om/update-state! this assoc :input-tag "")
      (add-tag this input-tag))))

(defn tag-filter [component filter-tags]
  (let [{:keys [input-tag]} (om/get-state component)]
    (html
      [:div
       (opts {:style {:display        :flex
                      :flex-direction :column
                      :min-width "400px"
                      :max-width "400px"}})

       [:div.has-feedback
        [:input.form-control
         {:type        "text"
          :value       input-tag
          :on-change   #(om/update-state! component assoc :input-tag (.. % -target -value))
          :on-key-down (on-add-tag-key-down component input-tag)
          :placeholder "Filter tags..."}]
        [:span
         {:class "glyphicon glyphicon-tag form-control-feedback"}]]

       [:div
        (opts {:style {:display        :flex
                       :flex-direction :row
                       :flex-wrap      :wrap
                       :width          "100%"}})
        (map-all
          filter-tags
          (fn [tagname]
            (tag/->Tag (tag/tag-props tagname
                                      (delete-tag-fn component tagname)))))]])))

(defn date-picker [component placeholder key]
  [:div
   [:div#date-input
    (->Datepicker
      (opts {:key         [placeholder key]
             :placeholder placeholder
             :value       (get (om/get-params component) key)
             :on-change   #(update-query-params! component assoc key %)}))]])

(def initial-params {:filter-tags #{} :start-date nil :end-date nil})

(defui ^:once AllTransactions
  static om/IQueryParams
  (params [_] initial-params)
  static om/IQuery
  (query [_]
    [{'(:query/all-transactions {:filter-tags ?filter-tags
                                 :start-date  ?start-date
                                 :end-date    ?end-date})
      [:db/id
       :transaction/uuid
       :transaction/title
       :transaction/amount
       :transaction/details
       :transaction/status
       :transaction/created-at
       {:transaction/currency [:currency/code
                               :currency/symbol-native]}
       {:transaction/tags (om/get-query tag/Tag)}
       ::transaction-show-tags?
       {:transaction/date [:db/id
                           :date/timestamp
                           :date/ymd
                           :date/day
                           :date/month
                           :date/year]}
       {:transaction/budget [:budget/uuid
                             :budget/name]}
       {:transaction/type [:db/ident]}]}])

  Object
  (initLocalState [_]
    {:input-tag   ""})
  (render [this]
    (let [{transactions :query/all-transactions} (om/props this)
          {:keys [filter-tags]} (om/get-params this)]
      (html
        [:div
         (opts {:style {:display        :flex
                        :flex-direction :column
                        :align-items    :flex-start
                        :width          "100%"}})


         [:div
          (opts {:style {:display        :flex
                         :flex-direction :row
                         :flex-wrap :wrap-reverse}})
          (tag-filter this filter-tags)
          (date-picker this "From date..." :start-date)
          (date-picker this "To date..." :end-date)]

         [:table.table.table-striped.table-hover
          [:thead
           [:tr
            [:th "Date"]
            [:th "Name"]
            [:th "Tags"]
            [:th.text-right
             "Amount"]]]
          [:tbody
           (map-all
             (sort-by :transaction/created-at #(> %1 %2) transactions)
             (fn [{:keys [transaction/date
                          transaction/currency
                          transaction/amount
                          transaction/uuid
                          transaction/type]
                   :as   transaction}]
               [:tr
                (opts {:key [uuid]})

                [:td
                 (opts {:key [uuid]})
                 (str (f/month-name (:date/month date)) " " (:date/day date))]

                [:td
                 (opts {:key [uuid]})
                 (:transaction/title transaction)]

                [:td
                 (opts {:key [uuid]})
                 (map-all (:transaction/tags transaction)
                   (fn [tag]
                        (tag/->Tag (merge tag
                                          {:on-click #(add-tag this (:tag/name tag))}))))]
                [:td.text-right
                 (opts {:key [uuid]
                        :class (if (= (:db/ident type) :transaction.type/expense) "text-danger" "text-success")})
                 (str amount " " (or (:currency/symbol-native currency)
                                     (:currency/code currency)))]]))]]]))))

(def ->AllTransactions (om/factory AllTransactions))
