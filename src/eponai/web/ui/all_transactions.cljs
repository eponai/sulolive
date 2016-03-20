(ns eponai.web.ui.all-transactions
  (:require [clojure.set :as set]
            [eponai.web.ui.format :as f]
            [eponai.web.ui.datepicker :refer [->Datepicker]]
            [eponai.client.ui :refer [map-all update-query-params!] :refer-macros [style opts]]
            [eponai.web.ui.utils :as utils]
            [eponai.common.format :as format]
            [garden.core :refer [css]]
            [goog.string :as gstring]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [taoensso.timbre :refer-macros [debug]]
            [datascript.core :as d]))

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
          {:keys [user on-select on-deselect is-selected on-tag-click]} (om/get-computed this)
          {:keys [tooltip-visible?]} (om/get-state this)]
      (html
        [:tr
         (opts {:key      [uuid]
                :class (if is-selected "is-selected" "")})
         ;[:td
         ; [:a.secondary.move-transaction
         ;  [:i.fa.fa-th]]]

         [:td
          (opts {:key [uuid]})
          [:span (str (f/month-name (:date/month date)) " " (:date/day date))]]

         [:td
          (if-let [rate (:conversion/rate conversion)]
            [:div
             [:small
              (str (or (:currency/code (:user/currency user))
                       (:currency/symbol-native (:user/currency user))) " ")]
             (if (= (:db/ident type) :transaction.type/expense)
               [:strong.label.alert
                (gstring/format (str "-%.2f") (/ amount rate))]
               [:strong.label.success
                (gstring/format (str "%.2f") (/ amount rate))])]
            ;[:strong
            ;
            ; (gstring/format (str (or (:currency/code (:user/currency user))
            ;                          (:currency/symbol-native (:user/currency user))) " %.2f") (/ amount rate))]
            [:span.has-tip.right
             ; {:on-mouse-over #(om/update-state! this assoc :tooltip-visible? true)
             ;  :on-mouse-out #(om/update-state! this assoc :tooltip-visible? false)}
             "?"]
            )]
         [:td
          [:small (str (or (:currency/symbol-native currency)
                           (:currency/code currency)) " " amount)]]

         [:td
          (opts {:key [uuid]})
          (:transaction/title transaction)]

         [:td
          (opts {:key [uuid]})
          (map-all (:transaction/tags transaction)
                   (fn [tag]
                     (utils/tag tag {:on-click #(on-tag-click tag)})))]


         [:td
          (if is-selected
            [:a
             {:on-click #(on-deselect)}
             [:i.fa.fa-chevron-right]]
            [:a.edit-transaction.secondary
             {:on-click #(on-select (dissoc transaction :om.next/computed))}
             [:i.fa.fa-chevron-right]])]]))))

(def ->Transaction (om/factory Transaction))

(defn get-selected-transaction [props]
  (get-in props [:query/selected-transaction
                 :ui.component.transactions/selected-transaction]))

(defn mark-removed-tags
  "Takes an edited transaction and an original transaction, marks every removed tag
  with :removed true."
  [edited original]
  (let [original-tags-by-name (group-by :tag/name (:transaction/tags original))]
    (cond-> edited
            (or (seq (:transaction/tags original))
                (seq (:transaction/tags edited)))
            (update :transaction/tags
              (fn [tags]
                (let [edited-tags-names (into #{} (map :tag/name) tags)
                      removed-tags (set/difference (set (keys original-tags-by-name))
                                                   edited-tags-names)]
                  (into tags (comp (mapcat
                                     (fn [tags] (map #(assoc % :tag/removed true) tags)))
                                   (remove
                                     #(and (:db/id %) (not (:tag/removed %)))))
                        (vals (select-keys original-tags-by-name removed-tags)))))))))

(defn filter-changed-fields [edited original]
  (let [tag-set-fn (fn [tags] (set (map #(select-keys % [:tag/name :tag/removed]) tags)))
        changed-fields (reduce-kv 
                         (fn [m k v]
                           (let [init-v (get original k)
                                 equal? (if (= :transaction/tags k)
                                          (= (tag-set-fn v)
                                             (tag-set-fn init-v))
                                          (= v init-v))]
                             (cond-> m
                                     (not equal?) (assoc k v))))
                         {}
                         edited)]
    changed-fields))

(defui SelectedTransaction
  static om/IQuery
  (query [_]
    [{:query/selected-transaction
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
                          :budget/name
                          :transaction/_budget]}])
  Object
  (init-state [_ props]
    (let [transaction (-> (get-selected-transaction props)
                          (update :transaction/tags (fn [tags] (set (map #(select-keys % [:tag/name]) tags)))))]
       {:init-state transaction
        :input-state transaction}))

  (initLocalState [this]
    (.init-state this (om/props this)))

  (componentWillReceiveProps [this next-props]
    (let [props->uuid (comp :init-state #(.init-state this %))]
      (when (not= (props->uuid (om/props this))
                  (props->uuid next-props))
        (om/set-state! this (.init-state this next-props)))))

  (toggle-input-type [this]
    (let [{:keys [input-state]} (om/get-state this)
          type (:db/ident (:transaction/type input-state))]
      (cond (= type :transaction.type/expense)
            (om/update-state! this assoc-in [:input-state :transaction/type :db/ident] :transaction.type/income)

            (= type :transaction.type/income)
            (om/update-state! this assoc-in [:input-state :transaction/type :db/ident] :transaction.type/expense))))

  (ui-config [this]
    (let [{:keys [input-state]} (om/get-state this)
          type (:db/ident (:transaction/type input-state))]
      (cond (= type :transaction.type/expense)
            {:btn-class "button alert small"
             :i-class "fa fa-minus"}

            (= type :transaction.type/income)
            {:btn-class "button success small"
             :i-class "fa fa-plus"})))

  (delete-tag [this tag]
    (om/update-state! this update-in [:input-state :transaction/tags] disj tag))

  (add-tag [this tag]
    (om/update-state! this
                      #(-> %
                           (assoc :input-tag "")
                           (update-in [:input-state :transaction/tags] conj tag))))
  
  (render [this]
    (let [props (om/props this)
          {:keys [query/all-currencies
                  query/all-budgets]} props
          {:keys [transaction/uuid]
           :as transaction} (get-selected-transaction props)
          {:keys [input-state
                  init-state
                  input-tag]} (om/get-state this)
          {:keys [transaction/title
                  transaction/amount
                  transaction/currency
                  transaction/budget
                  transaction/type
                  transaction/date
                  transaction/tags]} input-state
          edited? (not= init-state input-state)
          {:keys [on-close]} (om/get-computed this)
          {:keys [i-class btn-class]} (.ui-config this)]
      (debug "Selected transactions found: " (map #(count (:transaction/_budget %)) all-budgets))
      (html
        [:div
         (opts {:style {:width "100%"
                        :height "100%"}})
         (when transaction
           [:div.callout
            ;(opts {:style {:max-width "35rem"}})
            [:a.close-button
             {:on-click on-close}
             [:small "x"]]

            [:h3 title]
            [:input {:value     title
                     :type      "text"
                     :on-change (utils/on-change-in this [:input-state :transaction/title])}]


            [:div.input-group
             [:a.input-group-button
              (opts {:on-click #(.toggle-input-type this)
                     :class    btn-class})
              [:i
               {:class i-class}]]
             [:input.input-group-field
              (opts {:type        "number"
                     :placeholder "0.00"
                     :min         "0"
                     :value       amount
                     :on-change   (utils/on-change-in this [:input-transaction :transaction/amount])})]

             [:select.input-group-field
              (opts {:on-change     (utils/on-change-in this [:input-transaction :transaction/currency :currency/code])
                     :default-value currency})
              (map-all all-currencies
                       (fn [{:keys [currency/code]}]
                         [:option
                          (opts {:value (name code)
                                 :key   [code]})
                          (name code)]))]]

            [:select
             {:on-change     (utils/on-change-in this [:input-state :transaction/budget :budget/uuid])
              :type          "text"
              :default-value (:budget/name budget)}
             (map-all all-budgets
                      (fn [{:keys [budget/uuid budget/name]}]
                        [:option
                         (opts {:value uuid
                                :key   [uuid]})
                         name]))]

            (->Datepicker
              (opts {:key       [uuid]
                     :value     (format/ymd-string->js-date
                                  (:date/ymd date))
                     :on-change #(om/update-state!
                                  this assoc-in [:input-state :transaction/date :date/ymd]
                                  (format/date->ymd-string %))}))
            (utils/tag-input
              {:placeholder   "Add tag..."
               :input-tag     input-tag
               :selected-tags tags
               :on-change     #(om/update-state! this assoc :input-tag %)
               :on-delete-tag #(.delete-tag this %)
               :on-add-tag    #(.add-tag this %)})
            [:div
             (opts {:style {:display        :flex
                            :flex-direction :row-reverse}})
             [:a.button.primary
              (merge {:on-click #(om/transact! this `[(transaction/edit ~(-> (mark-removed-tags input-state init-state)
                                                                             (filter-changed-fields init-state)
                                                                             (assoc :transaction/uuid uuid)
                                                                             (assoc :db/id (:db/id transaction))
                                                                             (assoc :mutation-uuid (d/squuid))))
                                                      ;; TODO: Are all these needed?
                                                      ;; Copied from AddTransaction.
                                                      :query/selected-transaction
                                                      :query/dashboard
                                                      :query/all-budgets
                                                      :query/transactions])}
                     (when-not edited?
                       {:class "disabled"}))
              "Save"]
             [:a.button.secondary
              (merge {:on-click #(om/update-state! this assoc :input-state init-state)}
                     (when-not edited?
                       {:disabled :disabled}))
              "Reset"]]])]))))

(def ->SelectedTransaction (om/factory SelectedTransaction))


(defui AllTransactions
  static om/IQueryParams
  (params [_]
    {:filter {:filter/include-tags #{}}
     :transaction (om/get-query Transaction)})
  static om/IQuery
  (query [_]
    ['({:query/transactions ?transaction} {:filter ?filter})
     {:query/current-user [:user/uuid
                           {:user/currency [:currency/code
                                            :currency/symbol-native]}]}
     {:proxy/selected-transaction (om/get-query SelectedTransaction)}])

  Object
  (initLocalState [_]
    {:input-tag    ""
     :input-filter {:filter/include-tags #{}}})

  (componentWillUnmount [this]
    (.deselect-transaction this))

  (select-transaction [this transaction]
    (om/transact! this `[(transactions/select ~{:transaction transaction})]))

  (deselect-transaction [this]
    (om/transact! this `[(transactions/deselect)]))

  (render [this]
    (let [{transactions          :query/transactions
           user                  :query/current-user
           sel-transaction-props :proxy/selected-transaction} (om/props this)
          selected-transaction (get-selected-transaction sel-transaction-props)
          {:keys [input-filter]} (om/get-state this)]
      (html
        [:div
         [:div.callout.secondary.transaction-filters
          [:div.row.small-up-1.medium-up-2.large-up-3
           [:div.column
            (utils/tag-filter this (:filter/include-tags input-filter))]
           [:div.column
            (->Datepicker
              (opts {:key         ["From date..."]
                     :placeholder "From date..."
                     :value       (:filter/start-date input-filter)
                     :on-change   #(utils/select-date-filter this :filter/start-date %)}))]
           [:div.column
            (->Datepicker
              (opts {:key         ["To date..."]
                     :placeholder "To date..."
                     :value       (:filter/end-date input-filter)
                     :on-change   #(utils/select-date-filter this :filter/end-date %)}))]]]
         [:div.transactions
          (opts {:class (if selected-transaction "transaction-selected" "")
                 :style {:display        :flex
                         :flex-direction :row}})
          [:div.transaction-list
           [:table
            (opts {:style {:width "100%"}})
            [:tbody
             (map (fn [props]
                    (->Transaction
                      (om/computed props
                                   {:user         user
                                    :on-select    #(.select-transaction this %)
                                    :on-deselect  #(.deselect-transaction this)
                                    :is-selected  (= (:db/id selected-transaction)
                                                     (:db/id props))
                                    :on-tag-click #(utils/add-tag-filter this %)})))
                  (sort-by :transaction/created-at > transactions))]]]
          [:div.edit-transaction-form

           (->SelectedTransaction (om/computed sel-transaction-props
                                               {:on-close #(.deselect-transaction this)}))]]]))))

(def ->AllTransactions (om/factory AllTransactions))