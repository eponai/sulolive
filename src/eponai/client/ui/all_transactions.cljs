(ns eponai.client.ui.all_transactions
  (:require [eponai.client.ui.format :as f]
            [eponai.client.ui.datepicker :refer [->Datepicker]]
            [eponai.client.ui :refer [map-all update-query-params!] :refer-macros [style opts]]
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

(defn on-enter-pressed [f]
  (fn [e]
    (when (and (= 13 (.-keyCode e)))
      (.preventDefault e)
      (f e))))

(defn filters [component filter-tags]
  (let [{:keys [input-tag input-date]} (om/get-state component)]
    (html
      [:div
       (opts {:style {:display        "flex"
                      :flex-direction "column"}})

       [:div.has-feedback
        [:input.form-control
         {:type        "text"
          :value       input-tag
          :on-change   #(om/update-state! component assoc :input-tag (.. % -target -value))
          :on-key-down (on-enter-pressed (fn [e]
                                           (when (seq (.. e -target -value))
                                             (om/update-state! component assoc :input-tag "")
                                             (add-tag component input-tag))))
          :placeholder "Filter tags..."}]
        [:span
         {:class "glyphicon glyphicon-tag form-control-feedback"}]]

       [:div#date-input
        (->Datepicker
          (opts {:value     input-date
                 :on-change #(om/update-state! component assoc :input-date %)}))]

       [:div
        (opts {:style {:display        "flex"
                       :flex-direction "row"
                       :width          "100%"}})
        (map-all
          filter-tags
          (fn [tagname]
            (tag/->Tag (tag/tag-props tagname
                                      (delete-tag-fn component tagname)))))]])))

(defui ^:once AllTransactions
  static om/IQueryParams
  (params [_] {:filter-tags #{} :search-query ""})
  static om/IQuery
  (query [_]
    [{'(:query/all-transactions {:filter-tags  ?filter-tags
                                 :search-query ?search-query})
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
                           :date/ymd
                           :date/day
                           :date/month
                           :date/year
                           ::day-expanded?]}
       {:transaction/budget [:budget/uuid
                             :budget/name]}]}])

  Object
  (initLocalState [_]
    {:input-query nil
     :input-tag   ""
     :input-date  (js/Date.)})
  (render [this]
    (let [{transactions :query/all-transactions} (om/props this)
          {:keys [filter-tags search-query]} (om/get-params this)
          {:keys [input-query]} (om/get-state this)
          query (or input-query search-query)]
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
            {:value       query
             :type        "text"
             :placeholder "Search..."
             :on-change   #(om/update-state! this assoc :input-query (.. % -target -value))
             :on-key-down (on-enter-pressed
                            #(update-query-params! this assoc :search-query input-query))}]
           [:span {:class "glyphicon glyphicon-search form-control-feedback"}]]
          ]

         [:br]
         [:div.tab-content
          (filters this filter-tags)]

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
             (sort-by :transaction/created-at #(> %1 %2) transactions)
             (fn [{:keys [transaction/date
                          transaction/currency
                          transaction/amount
                          transaction/uuid]
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
                 (opts {:key [uuid]})
                 (str amount " " (or (:currency/symbol-native currency)
                                     (:currency/code currency)))]]))]]]))))

(def ->AllTransactions (om/factory AllTransactions))
