(ns eponai.client.ui.all_transactions
  (:require [clojure.set :as set]
            [eponai.client.ui.format :as f]
            [eponai.client.ui.datepicker :refer [->Datepicker]]
            [eponai.client.ui :refer [map-all update-query-params!] :refer-macros [style opts]]
            [eponai.client.ui.utils :as utils]
            [eponai.common.format :as format]
            [garden.core :refer [css]]
            [goog.dom :as dom]
            [goog.string :as gstring]
            [goog.style :as style]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [taoensso.timbre :refer-macros [debug]]
            [datascript.core :as d]))

;; ################ Actions #####################

(defn rename-ns [k ns]
  (keyword ns (name k)))

(defn delete-tag [component tag]
  (om/update-state! component update-in [:input-state :input/tags] disj tag))

(defn- add-tag [component tag]
  (om/update-state! component
                    #(-> %
                         (assoc-in [:input-state :input/tag] "")
                         (update-in [:input-state :input/tags] conj tag))))

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
         [:td
          (opts {:key [uuid]
                 :class (if (= (:db/ident type) :transaction.type/expense) "text-danger" "text-success")})
          (str amount " " (or (:currency/symbol-native currency)
                              (:currency/code currency)))]
         [:td
          (opts {:key [uuid]
                 :class (if (= (:db/ident type) :transaction.type/expense) "text-danger" "text-success")
                 :style {:position :relative}})
          (if-let [rate (:conversion/rate conversion)]
            [:span
             (gstring/format (str (:currency/code (:user/currency user)) "%.2f") (/ amount rate))]
            [:span.has-tip.right
             {:on-mouse-over #(om/update-state! this assoc :tooltip-visible? true)
              :on-mouse-out #(om/update-state! this assoc :tooltip-visible? false)}
             "?"])
          ;(when tooltip-visible?
          ;  [:span.tooltip.right
          ;   "This is some tooltip"])
          ]

         [:td
          (if is-selected
            [:a.button.secondary
             {:on-click #(on-deselect)}
             [:i.fa.fa-plus]]
            [:a.button.secondary
             {:on-click #(on-select (dissoc transaction :om.next/computed))}
             [:i.fa.fa-pencil]])]]))))

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
                                     (fn [tags] (map #(assoc % :removed true) tags)))
                                   (remove
                                     #(and (:db/id %) (not (:removed %)))))
                        (vals (select-keys original-tags-by-name removed-tags)))))))))

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
    (let [transaction (get-selected-transaction props)
          st (reduce-kv (fn [m k v]
                       (assoc m (rename-ns k "input") v))
                        {}
                     transaction)
          init-state (update st :input/tags (fn [tags]
                                              (set (map #(select-keys % [:tag/name]) tags))))]
       {:init-state init-state
        :input-state init-state}))

  (initLocalState [this]
    (.init-state this (om/props this)))

  (componentWillReceiveProps [this next-props]
    (let [props->uuid (comp :init-state #(.init-state this %))]
      (when (not= (props->uuid (om/props this))
                  (props->uuid next-props))
        (om/set-state! this (.init-state this next-props)))))

  (edited-input->edited-transaction [this]
    (let [{:keys [init-state input-state]} (om/get-state this)
          changed-values (reduce-kv (fn [m k v]
                                      (let [init-v (get init-state k)
                                            equal? (if (= :input/tags k)
                                                     (= (into #{} v)
                                                        (into #{} init-v))
                                                     (= v init-v))]
                                        (cond-> m
                                                (not equal?) (assoc k v))))
                                    {}
                                    (dissoc input-state :input/tag))]
      (reduce-kv #(assoc %1 (rename-ns %2 "transaction") %3)
                 {}
                 changed-values)))
  
  (render [this]
    (let [props (om/props this)
          {:keys [query/all-currencies
                  query/all-budgets]} props
          {:keys [transaction/uuid]
           :as transaction} (get-selected-transaction props)
          {:keys [input-state
                  init-state]} (om/get-state this)
          {:keys [input/title
                  input/amount
                  input/currency
                  input/budget
                  input/type
                  input/date
                  input/tag
                  input/tags]
           :as input-transaction} input-state
          edited? (not= init-state (dissoc input-state :input/tag))]
      (debug "Selected transactions found: " (map #(count (:transaction/_budget %)) all-budgets))
      (html
        [:div
         (opts {:style {:width (if transaction "100%" "0%")}})
         (when transaction
           [:div.callout.secondary
            (opts {:style {:max-width "35rem"}})
            [:input {:value     title
                     :type      "text"
                     :on-change (utils/on-change-in this [:input-state :input/title])}]
            [:input {:value     amount
                     :type      "number"
                     :on-change #(om/update-state! this assoc-in [:input-state :input/amount]
                                                   (cljs.reader/read-string (.. % -target -value)))}]

            [:select.form-control
             (opts {:on-change     (utils/on-change-in this [:input-state :input/currency :currency/code])
                    ;; What's the difference between default-value and value?
                    :value         (name (:currency/code currency))
                    :default-value (name (:currency/code currency))})
             (map-all all-currencies
                      (fn [{:keys [currency/code]}]
                        [:option
                         (opts {:value (name code)
                                :key   [code]})
                         (name code)]))]

            [:select.form-control
             {:on-change     (utils/on-change-in this [:input-state :input/budget :budget/name])
              :type          "text"
              :default-value (:budget/name budget)}
             (map-all all-budgets
                      (fn [{:keys [budget/uuid budget/name]}]
                        [:option
                         (opts {:value name
                                :key   [uuid]})
                         name]))]

            (->Datepicker
              (opts {:key       [uuid]
                     :value     (format/ymd-string->js-date
                                  (:date/ymd date))
                     :on-change #(om/update-state!
                                  this assoc-in [:input-state :input/date :date/ymd]
                                  (format/date->ymd-string %))}))
            (utils/tag-input
              {:placeholder   "Add tag..."
               :input-tag     tag
               :selected-tags tags
               :on-change     #(om/update-state! this assoc-in [:input-state :input/tag] %)
               :on-delete-tag #(delete-tag this %)
               :on-add-tag    #(add-tag this %)})
            [:div
             (opts {:style {:display        :flex
                            :flex-direction :row-reverse}})
             [:a.button.primary
              (merge {:on-click #(om/transact! this `[(transaction/edit ~(-> (.edited-input->edited-transaction this)
                                                                             (mark-removed-tags transaction)
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
                       {:disabled :disabled}))
              "Save"]
             [:a.button.secondary
              (merge {:on-click #(om/set-state! this (.init-state this props))}
                     (when-not edited?
                       {:disabled :disabled}))
              "Reset"]]])]))))

(def ->SelectedTransaction (om/factory SelectedTransaction))

(defn select-transaction [this transaction]
  (om/transact! this `[(transactions/select ~{:transaction transaction})]))

(defn deselect-transaction [this]
  (om/transact! this `[(transactions/deselect)]))


(defui AllTransactions
  static om/IQueryParams
  (params [_]
    {:filter {:filter/include-tags #{}}
     :transaction (om/get-query Transaction)})
  static om/IQuery
  (query [_]
    ['({:query/transactions ?transaction} {:filter ?filter})
     {:query/current-user [:user/uuid
                           {:user/currency [:currency/code]}]}
     {:proxy/selected-transaction (om/get-query SelectedTransaction)}])

  Object
  (initLocalState [_]
    {:input-tag    ""
     :input-filter {:filter/include-tags #{}}})

  (render [this]
    (let [{transactions          :query/transactions
           user                  :query/current-user
           sel-transaction-props :proxy/selected-transaction} (om/props this)
          selected-transaction (get-selected-transaction sel-transaction-props)
          {:keys [input-filter]} (om/get-state this)]
      (debug "Rendering transactions: " transactions)
      (html
        [:div
         [:div.callout.secondary
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
         [:div (opts {:style {:display        :flex
                              :flex-direction :row}})
          [:div
           (opts {:style {:width "100%"}})

           [:table
            (opts {:style {:width "100%"}})
            [:thead
             [:tr
              [:th "Date"]
              [:th "Name"]
              [:th "Tags"]
              [:th
               "Amount"]
              [:th
               "Cost"]
              [:th]]]
            [:tbody
             (map (fn [props]
                    (->Transaction
                      (om/computed props
                                   {:user         user
                                    :on-select    #(select-transaction this %)
                                    :on-deselect  #(deselect-transaction this)
                                    :is-selected  (= (:db/id selected-transaction)
                                                     (:db/id props))
                                    :on-tag-click #(utils/add-tag-filter this %)})))
                  (sort-by :transaction/created-at > transactions))]]]
          [:div

           (->SelectedTransaction sel-transaction-props)]]]))))

(def ->AllTransactions (om/factory AllTransactions))