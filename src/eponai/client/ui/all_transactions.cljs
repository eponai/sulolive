(ns eponai.client.ui.all_transactions
  (:require [eponai.client.ui.format :as f]
            [eponai.client.ui.datepicker :refer [->Datepicker]]
            [eponai.client.ui :refer [map-all update-query-params!] :refer-macros [style opts]]
            [eponai.client.ui.utils :as utils]
            [eponai.common.format :as format]
            [garden.core :refer [css]]
            [goog.string :as gstring]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [taoensso.timbre :refer-macros [debug]]
            [datascript.core :as d]))


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

;; ################### Om next components ###################

(defui Transaction
  static om/IQuery
  (query [_]
    [:db/id
     :transaction/uuid
     :transaction/title
     :transaction/amount
     :transaction/created-at
     {:transaction/currency [:currency/code
                             :currency/symbol-native]}
     {:transaction/tags [:tag/name]}
     {:transaction/date [:db/id
                         :date/timestamp
                         :date/ymd
                         :date/day
                         :date/month
                         :date/year]}
     {:transaction/budget [:budget/uuid
                           :budget/name]}
     {:transaction/type [:db/ident]}
     :transaction/conversion])
  Object
  (render [this]
    (let [{:keys [transaction/date
                  transaction/currency
                  transaction/amount
                  transaction/uuid
                  transaction/type
                  transaction/conversion]
           :as   transaction} (om/props this)
          {:keys [user on-select on-deselect is-selected on-tag-click]} (om/get-computed this)]
      (debug "computed: " (om/get-computed this))
      (html
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
                     (utils/tag tag {:on-click #(on-tag-click tag)})))]
         [:td.text-right
          (opts {:key [uuid]
                 :class (if (= (:db/ident type) :transaction.type/expense) "text-danger" "text-success")})
          (str amount " " (or (:currency/symbol-native currency)
                              (:currency/code currency)))]
         [:td.text-right
          (opts {:key [uuid]
                 :class (if (= (:db/ident type) :transaction.type/expense) "text-danger" "text-success")})
          (str (gstring/format (str (:currency/code (:user/currency user)) "%.2f") (/ amount (:conversion/rate conversion))) )]

         [:td
          (if is-selected
            [:button.btn.btn-default.btn-md
             {:on-click #(on-deselect)}
             [:i.fa.fa-plus]]
            [:button.btn.btn-default.btn-md
             {:on-click #(on-select (dissoc transaction :om.next/computed))}
             [:i.fa.fa-pencil]])]]))))

(def ->Transaction (om/factory Transaction))

(defn get-selected-transaction [props]
  (get-in props [[:ui/component :ui.component/transactions]
                 :ui.component.transactions/selected-transaction]))

(defn rename-ns [k ns]
  (keyword (str ns "/" (name k))))

;; TODO: Make the one in add transactions work on maps as well.
(defn delete-tag-fn2 [component tag]
  (fn []
    (om/update-state! component update :input/tags
                      (fn [tags]
                        (into [] (remove #(= (:tag/name %) (:tag/name tag)))
                              tags)))))

(defui SelectedTransaction
  static om/IQuery
  (query [_]
    [{[:ui/component :ui.component/transactions]
      [{:ui.component.transactions/selected-transaction
        [:db/id
         :transaction/uuid
         :transaction/title
         :transaction/amount
         {:transaction/currency [:currency/code :currency/symbol-native]}
         {:transaction/tags [:tag/name]}
         {:transaction/date [:date/ymd]}
         {:transaction/budget [:budget/uuid :budget/name]}
         {:transaction/type [:db/ident]}
         :transaction/conversion]}]}
     {:query/all-currencies [:currency/code]}
     {:query/all-budgets [:budget/uuid
                          :budget/name]}])
  Object
  (init-state [_ props]
    (let [transaction (get-selected-transaction props)]
      (reduce-kv (fn [m k v]
                   (assoc m (rename-ns k "input") v))
                 {:input-tag ""}
                 transaction)))

  (edited? [this]
    (let [input-transaction (om/get-state this)
          val-fn {:transaction/amount #(if (string? %) (cljs.reader/read-string %) %)
                  :transaction/tags #(into [] %)
                  :transaction/type   (fn [m]
                                        (update m :db/ident
                                                #(if (string? %)
                                                  (keyword (str "transaction.type/" %))
                                                  %)))}]
      (not-every? (fn [[k v]]
                (let [input-val (get input-transaction (rename-ns k "input"))
                      input-val ((get val-fn k identity) input-val)
                      eq? (= v input-val)]
                  eq?))
              (get-selected-transaction (om/props this)))))

  (initLocalState [this]
    (.init-state this (om/props this)))

  (componentWillReceiveProps [this next-props]
    (let [props->uuid (comp :transaction/uuid get-selected-transaction)]
      (when (not= (props->uuid (om/props this))
                  (props->uuid next-props))
        (om/set-state! this (.init-state this next-props)))))

  (render [this]
    (let [props (om/props this)
          {:keys [query/all-currencies
                  query/all-budgets]} props
          transaction (get-selected-transaction props)
          {:keys [input/title
                  input/amount
                  input/currency
                  input/budget
                  input/type
                  input/uuid
                  input/date
                  input/tag
                  input/tags]
           :as input-transaction} (om/get-state this)
          edited? (.edited? this)]
      (html
        [:div
         (opts {:style {:width (if transaction "100%" "0%")}})
         (when transaction
           [:table
            [:tbody
             [:tr
              [:td
               [:button.btn.btn-info.btn-md
                (merge {:on-click #(om/transact! this `[(transaction/edit ~(-> input-transaction
                                                                               (assoc :mutation-uuid (d/squuid))))])}
                  (when-not edited?
                    {:disabled :disabled}))
                "Save"]]
              [:td
               [:button.btn.btn-danger.btn-md
                (merge {:on-click #(om/set-state! this (.init-state this props))}
                       (when-not edited?
                         {:disabled :disabled}))
                "Reset"]]]
             [:tr
              [:td "Name:"]
              [:td [:input {:value title :on-change (utils/on-change this :input/title)}]]]
             [:tr
              [:td "Amount:"]
              [:td [:input {:value amount
                            :type "number"
                            :on-change (utils/on-change this :input/amount)}]]]
             [:tr
              [:td "Currency:"]
              [:td
               [:select.form-control
                (opts {:on-change     (utils/on-change-in this [:input/currency :currency/code])
                       ;; What's the difference between default-value and value?
                       :value (name (:currency/code currency))
                       :default-value (name (:currency/code currency))})
                (map-all all-currencies
                         (fn [{:keys [currency/code]}]
                           [:option
                            (opts {:value (name code)
                                   :key   [code]})
                            (name code)]))]]]
             [:tr
              [:td "Sheet:"]
              [:td
               [:select.form-control
                {:on-change     (utils/on-change-in this [:input/budget :budget/name])
                 :type          "text"
                 :default-value (:budget/name budget)}
                (map-all all-budgets
                         (fn [{:keys [budget/uuid budget/name]}]
                           [:option
                            (opts {:value name
                                   :key   [uuid]})
                            name]))]]]
             [:tr
              [:td "Type:"]
              [:td [:select.form-control
                    {:on-change     (utils/on-change-in this [:input/type :db/ident])
                     :default-value (name (:db/ident type))}
                    [:option "expense"]
                    [:option "income"]]]]
             [:tr
              [:td "Date:"]
              [:td (->Datepicker
                     (opts {:key       [uuid]
                            :value     (format/ymd-string->js-date
                                         (:date/ymd date))
                            :on-change #(om/update-state!
                                         this assoc-in [:input/date :date/ymd]
                                         (format/date->ymd-string %))}))]]
             [:tr
              [:td "Tags:"]
              [:td
               (utils/tag-input
                 {:placeholder "Add tag..."
                  :tag         tag
                  :on-change   #(om/update-state! this assoc :input/tag %)
                  :on-add-tag  (fn [tag]
                                 (om/update-state!
                                   this
                                   (fn [state]
                                     (-> state
                                         (assoc :input/tag "")
                                         (update-in [:input/tags] conj
                                                    (or (->> (:transaction/tags transaction)
                                                             (filter #(= (:tag/name %) (:tag/name tag)))
                                                             first)
                                                        tag))))))})
               ;; Copied from ->Transaction
               (map-all tags
                        (fn [tag]
                          (utils/tag tag {:on-delete (delete-tag-fn2 this tag)})))]]]])]))))

(def ->SelectedTransaction (om/factory SelectedTransaction))

(defn select-transaction [this transaction]
  (om/transact! this `[(transactions/select-transaction ~{:transaction transaction})]))

(defn deselect-transaction [this]
  (om/transact! this `[(transactions/deselect-transaction)]))

(defui AllTransactions
  static om/IQueryParams
  (params [_]
    {:filter {:filter/include-tags #{}}})
  static om/IQuery
  (query [_]
    [{'(:query/all-transactions {:filter ?filter})
      (om/get-query Transaction)}
     {:query/current-user [:user/uuid
                           {:user/currency [:currency/code]}]}
     {:proxy/selected-transaction (om/get-query SelectedTransaction)}])

  Object
  (initLocalState [_]
    {:input-tag    ""
     :input-filter {:filter/include-tags #{}}})

  (render [this]
    (let [{transactions :query/all-transactions
           user :query/current-user
           sel-transaction-props :proxy/selected-transaction} (om/props this)
          selected-transaction (get-selected-transaction sel-transaction-props)
          {:keys [input-filter]} (om/get-state this)]
      (html
        [:div (opts {:style {:display :flex
                             :flex-direction :row}})
         [:div
          (opts {:style {:display        :flex
                         :flex-direction :column
                         :align-items    :flex-start
                         :width          "100%"}})


          [:div
           (opts {:style {:display        :flex
                          :flex-direction :row
                          :flex-wrap      :wrap-reverse}})
           (tag-filter this (:filter/include-tags input-filter))
           (utils/date-picker {:value (:filter/start-date input-filter)
                               :on-change #(select-date this :filter/start-date %)
                               :placeholder "From date..."})
           (utils/date-picker {:value (:filter/end-date input-filter)
                               :on-change #(select-date this :filter/end-date %)
                               :placeholder "To date..."})]

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
            (map (fn [props]
                   (->Transaction
                     (om/computed props
                                  {:user        user
                                   :on-select   #(select-transaction this %)
                                   :on-deselect #(deselect-transaction this)
                                   :is-selected (= (:db/id selected-transaction)
                                                   (:db/id props))
                                   :on-tag-click #(add-tag this %)})))
                 (sort-by :transaction/created-at > transactions))]]]
         [:div

          (->SelectedTransaction sel-transaction-props)]]))))

(def ->AllTransactions (om/factory AllTransactions))