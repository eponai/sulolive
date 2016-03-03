(ns eponai.client.ui.all_transactions
  (:require [eponai.client.ui.format :as f]
            [eponai.client.ui.datepicker :refer [->Datepicker]]
            [eponai.client.ui :refer [map-all update-query-params!] :refer-macros [style opts]]
            [eponai.client.ui.utils :as utils]
            [garden.core :refer [css]]
            [goog.string :as gstring]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [taoensso.timbre :refer-macros [debug]]))


;; ################ Actions ######################

(defn- update-filter [component input-filter]
  (update-query-params! component assoc :filter input-filter))

(defn- select-date [component k date]
  (let [{:keys [input-filter]} (om/get-state component)
        new-filters (assoc input-filter k date)]

    (om/update-state! component assoc
                      :input-filter new-filters)
    (update-filter component new-filters)))

(defn- delete-tag-fn [component tag]
  (let [{:keys [input-filter]} (om/get-state component)
        new-filters (update input-filter :filter/include-tags #(disj % tag))]

    (om/update-state! component assoc
                      :input-filter new-filters)
    (update-filter component new-filters)))

(defn- add-tag [component tag]
  (let [{:keys [input-filter]} (om/get-state component)
        new-filters (update input-filter :filter/include-tags #(conj % tag))]
    (om/update-state! component assoc
                      :input-filter new-filters
                      :input-tag "")
    (update-filter component new-filters)))

;; ############################# UI components #################
(defn tag-filter [component include-tags]
  (let [{:keys [input-tag]} (om/get-state component)]
    (html
      [:div
       (opts {:style {:display        :flex
                      :flex-direction :column
                      :min-width "400px"
                      :max-width "400px"}})

       (utils/tag-input {:tag         input-tag
                         :placeholder "Filter tags..."
                         :on-change   #(om/update-state! component assoc :input-tag %)
                         :on-add-tag  #(add-tag component %)})

       [:div
        (opts {:style {:display        :flex
                       :flex-direction :row
                       :flex-wrap      :wrap
                       :width          "100%"}})
        (map-all
          include-tags
          (fn [tag]
            (utils/tag tag
                 {:on-delete #(delete-tag-fn component tag)})))]])))

(defn date-picker [component placeholder key]
  [:div
   [:div#date-input
    (->Datepicker
      (opts {:key         [placeholder key]
             :placeholder placeholder
             :value       (get-in (om/get-state component) [:input-filter key])
             :on-change   #(select-date component key %)}))]])

;; ################### Om next components ###################

(defui AllTransactions
  static om/IQueryParams
  (params [_]
    {:filter {:filter/include-tags #{}}})
  static om/IQuery
  (query [_]
    [{'(:query/all-transactions {:filter ?filter})
      [:db/id
       :transaction/uuid
       :transaction/title
       :transaction/amount
       :transaction/details
       :transaction/status
       :transaction/created-at
       {:transaction/currency [:currency/code
                               :currency/symbol-native]}
       {:transaction/tags [:tag/name]}
       ::transaction-show-tags?
       {:transaction/date [:db/id
                           :date/timestamp
                           :date/ymd
                           :date/day
                           :date/month
                           :date/year]}
       {:transaction/budget [:budget/uuid
                             :budget/name]}
       {:transaction/type [:db/ident]}
       :transaction/conversion]}
     {:query/current-user [:user/uuid
                           {:user/currency [:currency/code]}]}])

  Object
  (initLocalState [_]
    {:input-tag    ""
     :input-filter {:filter/include-tags #{}}})

  (render [this]
    (let [{transactions :query/all-transactions
           user :query/current-user} (om/props this)
          {:keys [input-filter]} (om/get-state this)]
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
          (tag-filter this (:filter/include-tags input-filter))
          (date-picker this "From date..." :filter/start-date)
          (date-picker this "To date..." :filter/end-date)]

         [:table.table.table-striped.table-hover
          [:thead
           [:tr
            [:th "Date"]
            [:th "Name"]
            [:th "Tags"]
            [:th.text-right
             "Amount"]
            [:th.text-right
             "Cost"]]]
          [:tbody
           (map-all
             (sort-by :transaction/created-at #(> %1 %2) transactions)
             (fn [{:keys [transaction/date
                          transaction/currency
                          transaction/amount
                          transaction/uuid
                          transaction/type
                          transaction/conversion]
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
                     (utils/tag tag
                                {:on-click #(add-tag this tag)})))]
                [:td.text-right
                 (opts {:key [uuid]
                        :class (if (= (:db/ident type) :transaction.type/expense) "text-danger" "text-success")})
                 (str amount " " (or (:currency/symbol-native currency)
                                     (:currency/code currency)))]
                [:td.text-right
                 (opts {:key [uuid]
                        :class (if (= (:db/ident type) :transaction.type/expense) "text-danger" "text-success")})
                 (str (gstring/format (str (:currency/code (:user/currency user)) "%.2f") (/ amount (:conversion/rate conversion))) )]
                ]))]]]))))

(def ->AllTransactions (om/factory AllTransactions))