(ns eponai.client.ui.all_transactions
  (:require [eponai.client.ui.format :as f]
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

(defn update-query [component]
  (let [{:keys [filter-tags
                search-query]} (om/get-state component)
        query-params (cond-> {:find-query '{:find  [[?e ...]]
                                            :in    [$]
                                            :where []}
                              :values     []}
                             (not-empty filter-tags)
                             (->
                                 (update-in [:find-query :where] conj '[?e :transaction/tags ?tag] '[?tag :tag/name ?tagname])
                                 (update-in [:find-query :in] conj '[?tagname ...])
                                 (update :values conj filter-tags))

                             (not-empty search-query)
                             (->
                                 (update-in [:find-query :where] conj '[(fulltext $ :transaction/title ?search) [?e _ _ _]])
                                 (update-in [:find-query :in] conj '?search)
                                 (update :values conj search-query))

                             (and (empty? search-query) (empty? filter-tags))
                             (->
                                 (assoc-in [:find-query :where] '[[?e :transaction/uuid]])))]
    (om/set-query! component
                   {:params query-params})))

(defn delete-tag-fn [component name k]
  (fn []
    (om/update-state! component update k
                      (fn [tags]
                        (into #{}
                              (remove #(= name %))
                              tags)))
    (update-query component)))

(defn on-add-tag-key-down [this input-tag]
  (fn [key]
    (when (and (= 13 (.-keyCode key))
               (seq (.. key -target -value)))
      (.preventDefault key)
      (om/update-state! this
                        #(-> %
                             (assoc :input-tag "")
                             (update :filter-tags conj input-tag)))
      (update-query this))))

(defn on-change [this k]
  (fn [e]
    (om/update-state! this assoc k (.-value (.-target e)))
    (update-query this)))

(defn filters [component]
  (let [{:keys [input-tag
                filter-tags
                input-date]} (om/get-state component)]
    (html
      [:div
       (opts {:style {:display        "flex"
                      :flex-direction "column"}})

       [:div.has-feedback
        [:input.form-control
         {:type        "text"
          :value       input-tag
          :on-change   #(om/update-state! component assoc :input-tag (.-value (.-target %)))
          :on-key-down (on-add-tag-key-down component input-tag)
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
                                      (delete-tag-fn component tagname :filter-tags)))))]])))

(defui AllTransactions
  static om/IQueryParams
  (params [_]
    {:values     []
     :find-query '[:find [?e ...]
                   :where [?e :transaction/uuid]]})
  static om/IQuery
  (query [_]
    [{'(:query/all-transactions {:find-query ?find-query
                                 :values ?values})
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
    {:filter-tags #{}
     :input-tag ""
     :input-date (js/Date.)
     :active-tab :filter})
  (render [this]
    (let [{transactions :query/all-transactions} (om/props this)
          {:keys [active-tab
                  search-query]} (om/get-state this)]
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
            {:value search-query
             :type        "text"
             :placeholder "Search..."
             :on-change (on-change this :search-query)}]
           [:span {:class "glyphicon glyphicon-search form-control-feedback"}]]
          ]

         [:br]
         [:div.tab-content
          (if (= active-tab :filter)
            (filters this))]

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
                 (map tag/->Tag (:transaction/tags transaction))]
                [:td.text-right
                 (opts {:key [uuid]})
                 (str amount " " (or (:currency/symbol-native currency)
                                     (:currency/code currency)))]]))]]]))))

(def ->AllTransactions (om/factory AllTransactions))
