(ns eponai.web.ui.all-transactions
  (:require [clojure.set :as set]
            [eponai.web.ui.add-transaction :refer [->AddTransaction AddTransaction]]
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
            [datascript.core :as d]
            [cljs-time.core :as time]
            [cljs-time.coerce :as coerce]))

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
         (opts {:key         [uuid]
                :id          (str uuid)
                :class       (if is-selected "is-selected" "")
                :draggable   true
                :onDragStart #(utils/on-drag-transaction-start this (str uuid) %)})

         ;[:td
         ; [:a.secondary.move-transaction
         ;  {:draggable   true
         ;   :onDragStart (fn [e]
         ;                  (debug "Drag id: " (str uuid))
         ;                  (.. e -dataTransfer (setData "text" uuid)))}
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
                     :on-change   (utils/on-change-in this [:input-state :transaction/amount])})]

             [:select.input-group-field
              (opts {:on-change     (utils/on-change-in this [:input-state :transaction/currency :currency/code])
                     :value (name (:currency/code currency))
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

(defn filter-settings [component]
  (let [{:keys [input-filter date-filter]} (om/get-state component)]
    (html
      [:div.transaction-filters
       [:div.row.small-up-1.medium-up-3.large-up-4
        (opts {:style {:padding "1em 0"}})
        [:div.column
         (utils/tag-filter component (:filter/include-tags input-filter))]

        [:div.column
         [:select
          (opts {:on-change #(.update-date-filter component (.-value (.-target %)))})
          [:option
           {:value :all-time}
           "all time"]
          [:option
           {:value :this-month}
           "this month"]
          [:option
           {:value :last-7-days}
           "last 7 days"]
          [:option
           {:value :last-30-days}
           "last 30 days"]
          [:option
           {:value :date-range}
           "custom..."]]]
        (when (= :date-range date-filter)
          [:div.column
           (->Datepicker
             (opts {:key         ["From date..."]
                    :placeholder "From date..."
                    :value       (:filter/start-date input-filter)
                    :on-change   #(utils/select-date-filter component :filter/start-date %)}))])
        (when (= :date-range date-filter)
          [:div.column
           (->Datepicker
             (opts {:key         ["To date..."]
                    :placeholder "To date..."
                    :value       (:filter/end-date input-filter)
                    :on-change   #(utils/select-date-filter component :filter/end-date %)
                    :min-date    (:filter/start-date input-filter)}))])]])))

(defui AllTransactions
  static om/IQueryParams
  (params [_]
    {:filter {:filter/include-tags #{}}
     :transaction (om/get-query Transaction)})
  static om/IQuery
  (query [_]
    ['({:query/transactions ?transaction} {:filter ?filter})
     {:query/conversions [:conversion/currency
                          :conversion/rate
                          :conversion/date]}
     {:query/current-user [:user/uuid
                           {:user/currency [:currency/code
                                            :currency/symbol-native]}]}
     {:proxy/selected-transaction (om/get-query SelectedTransaction)}
     {:proxy/add-transaction (om/get-query AddTransaction)}])

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

  (set-this-month-filter [this]
    (let [update-filter-fn (fn [filter]
                             (-> filter
                                 (dissoc :filter/end-date :filter/last-x-days)
                                 (assoc :filter/start-date (let [today (time/today)
                                                                 year (time/year today)
                                                                 month (time/month today)]
                                                             (coerce/to-date (time/date-time year month 1))))))]
      (om/update-state! this update :input-filter update-filter-fn)
      (utils/update-filter this (:input-filter (om/get-state this)))))
  
  (set-last-x-days-filter [this span]
    (om/update-state! this update :input-filter (fn [filter]
                                                  (-> filter
                                                      (dissoc :filter/end-date :filter/start-date)
                                                      (assoc :filter/last-x-days span))))
    (utils/update-filter this (:input-filter (om/get-state this))))

  (reset-date-filters [this]
    (om/update-state! this update :input-filter dissoc :filter/start-date :filter/end-date :filter/last-x-days)
    (utils/update-filter this (:input-filter (om/get-state this))))

  (update-date-filter [this value]
    (let [time-type (cljs.reader/read-string value)]
      (cond
        (= time-type :all-time) (.reset-date-filters this)
        (= time-type :last-7-days) (.set-last-x-days-filter this 7)
        (= time-type :last-30-days) (.set-last-x-days-filter this 30)
        (= time-type :this-month) (.set-this-month-filter this)
        true (om/update-state! this update :input-filter dissoc :filter/last-x-days))
      (om/update-state! this assoc :date-filter time-type)))

  (render [this]
    (let [{transactions          :query/transactions
           user                  :query/current-user
           sel-transaction-props :proxy/selected-transaction
           add-transaction       :proxy/add-transaction} (om/props this)
          selected-transaction (get-selected-transaction sel-transaction-props)
          {:keys [input-filter add-transaction?]} (om/get-state this)]
      (println "Input-filter: " input-filter)
      (prn "Has filter: " (some? (or (seq (:filter/include-tags input-filter))
                                     (seq (:filter/exclude-tags input-filter))
                                     (:filter/start-date input-filter)
                                     (:filter/end-date input-filter))))
      (html
        [:div
         (if (or (seq transactions)
                 (some? (or (seq (:filter/include-tags input-filter))
                            (seq (:filter/exclude-tags input-filter))
                            (:filter/start-date input-filter)
                            (:filter/end-date input-filter))))
           [:div
            (filter-settings this)
            [:div#all-transactions
             (if (seq transactions)
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
                                                     {:on-close #(.deselect-transaction this)}))]]
               [:div.empty-message
                [:div.lead
                 [:i.fa.fa-search.fa-fw]
                 "No transactions found with filters."]])]]
           [:div
            [:a.button
             (opts {:style {:visibility :hidden}})
             "Button"]
            [:div.empty-message.text-center
             [:i.fa.fa-usd.fa-4x]
             [:i.fa.fa-eur.fa-4x]
             [:i.fa.fa-yen.fa-4x]
             ;[:i.fa.fa-th-list.fa-5x]
             [:div.lead
              "Information underload, no transactions are added."
              [:br]
              [:br]
              "Start tracking your money and "
              [:a.link
               {:on-click #(om/update-state! this assoc :add-transaction? true)}
               "add a transaction"]
              "."]]])
         (when add-transaction?
           (let [on-close #(om/update-state! this assoc :add-transaction? false)]
             (utils/modal {:content  (->AddTransaction
                                       (om/computed add-transaction
                                                    {:on-close on-close}))
                           :on-close on-close})))]))))

(def ->AllTransactions (om/factory AllTransactions))