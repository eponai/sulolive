(ns eponai.web.ui.all-transactions
  (:require [clojure.set :as set]
            [eponai.web.ui.add-transaction :refer [->AddTransaction AddTransaction]]
            [eponai.web.ui.format :as f]
            [eponai.web.ui.datepicker :refer [->Datepicker]]
            [eponai.web.ui.utils.filter :as filter]
            [eponai.client.lib.transactions :as lib.t]
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
            [cljs-time.coerce :as coerce]
            [eponai.web.routes :as routes]))

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
     {:transaction/project [:db/id :project/uuid :project/name]}
     {:transaction/type [:db/ident]}
     :transaction/conversion])
  Object
  (render [this]
    (let [{:keys [transaction/date
                  transaction/currency
                  transaction/amount
                  transaction/uuid
                  transaction/type
                  transaction/conversion
                  transaction/project]
           :as   transaction} (om/props this)
          {:keys [user is-selected on-tag-click]} (om/get-computed this)]
      (html
        [:tr
         (opts {:key         [uuid]
                :id          (str uuid)
                :class       (if is-selected "is-selected" "")
                :draggable   true
                :onDragStart #(utils/on-drag-transaction-start this (str uuid) %)})
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
            [:span.has-tip.right
             "?"])]
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
             {:href (routes/key->route :route/project->txs
                                       {:route-param/project-id (:db/id project)})}
             [:i.fa.fa-chevron-right]]
            [:a.edit-transaction.secondary
             {:href (routes/key->route :route/project->txs->tx
                                       {:route-param/project-id (:db/id project)
                                        :route-param/transaction-id (:db/id transaction)})}
             [:i.fa.fa-chevron-right]])]]))))

(def ->Transaction (om/factory Transaction))

(defn get-selected-transaction [props]
  (get-in props [:query/selected-transaction
                 :ui.component.transactions/selected-transaction]))

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
         {:transaction/project [:project/uuid :project/name]}
         {:transaction/type [:db/ident]}
         :transaction/conversion]}]}
     {:query/all-currencies [:currency/code]}
     {:query/all-projects [:project/uuid
                          :project/name
                          :transaction/_project]}])
  Object
  (init-state [_ props]
    (let [transaction (-> (get-selected-transaction props)
                          (update :transaction/tags (fn [tags] (into [] (map #(select-keys % [:tag/name])) tags))))]
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
    (om/update-state! this update-in [:input-state :transaction/tags]
                      (fn [tags]
                        (into [] (remove #(= (:tag/name %) (:tag/name tag))) tags))))

  (add-tag [this tag]
    (om/update-state! this
                      (fn [st] (-> st
                                   (assoc :input-tag "")
                                   (update-in [:input-state :transaction/tags]
                                              (fn [tags]
                                                (if-not (some #(= (:tag/name %) (:tag/name tag)) tags)
                                                  (if (empty? tags)
                                                    [tag]
                                                    (conj tags tag))
                                                  tags)))))))
  
  (render [this]
    (let [props (om/props this)
          {:keys [query/all-currencies
                  query/all-projects]} props
          {:keys [transaction/uuid]
           :as transaction} (get-selected-transaction props)
          {:keys [input-state
                  init-state
                  input-tag]} (om/get-state this)
          {:keys [transaction/title
                  transaction/amount
                  transaction/currency
                  transaction/project
                  transaction/type
                  transaction/date
                  transaction/tags]} input-state
          edited? (not= init-state input-state)
          {:keys [i-class btn-class]} (.ui-config this)]
      (html
        [:div
         (opts {:style {:width "100%"
                        :height "100%"}})
         (when transaction
           [:div.callout
            ;(opts {:style {:max-width "35rem"}})
            [:a.close-button
             {:href (routes/key->route :route/project->txs
                                       {:route-param/project-id (:db/id project)})}
             [:small "x"]]

            [:h3 title]
            [:input {:value     (or title "")
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
                     :value       (or amount "")
                     :on-change   (utils/on-change-in this [:input-state :transaction/amount])})]

             [:select.input-group-field
              (opts {:on-change     (utils/on-change-in this [:input-state :transaction/currency :currency/code])
                     :value (name (:currency/code currency))})
              (map-all all-currencies
                       (fn [{:keys [currency/code]}]
                         [:option
                          (opts {:value (name code)
                                 :key   [code]})
                          (name code)]))]]

            [:select
             {:on-change     (utils/on-change-in this [:input-state :transaction/project :project/uuid])
              :type          "text"
              :default-value (:project/name project)}
             (map-all all-projects
                      (fn [{:keys [project/uuid project/name]}]
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
              (merge {:on-click #(om/transact! this `[(transaction/edit ~(-> input-state
                                                                             (lib.t/diff-transaction init-state)
                                                                             (assoc :transaction/uuid uuid)
                                                                             (assoc :db/id (:db/id transaction))
                                                                             (assoc :mutation-uuid (d/squuid))))
                                                      ;; TODO: Are all these needed?
                                                      ;; Copied from AddTransaction.
                                                      :query/selected-transaction
                                                      :query/dashboard
                                                      :query/all-projects
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
  (let [{:keys [tag-filter date-filter]} (om/get-state component)]
    (html
      [:div.transaction-filters
       [:div.row
        (opts {:style {:padding "1em 0"}})
        [:div.columns.small-3
         (filter/->TagFilter (om/computed {:tags (:filter/include-tags tag-filter)}
                                          {:on-change #(do
                                                        (om/update-state! component assoc :tag-filter {:filter/include-tags %})
                                                        (om/update-query! component assoc-in [:params :filter] (.filter component)))}))]
        [:div.columns.small-9
         (filter/->DateFilter (om/computed {:filter date-filter}
                                           {:on-change #(do
                                                         (om/update-state! component assoc :date-filter %)
                                                         (om/update-query! component assoc-in [:params :filter] (.filter component)))}))]]])))

(defui AllTransactions
  static om/IQueryParams
  (params [_]
    {:filter {}
     :transaction (om/get-query Transaction)})
  static om/IQuery
  (query [_]
    ['({:query/transactions ?transaction} {:filter ?filter})
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

  (filter [this]
    (let [{:keys [date-filter tag-filter]} (om/get-state this)]
      (merge tag-filter date-filter)))

  (render [this]
    (let [{transactions          :query/transactions
           user                  :query/current-user
           sel-transaction-props :proxy/selected-transaction
           add-transaction       :proxy/add-transaction} (om/props this)
          selected-transaction (get-selected-transaction sel-transaction-props)
          {:keys [add-transaction?]} (om/get-state this)
          input-filter (.filter this)]
      (println "Input-filter: " input-filter)
      (prn "Has filter: " (some? (or (seq (:filter/include-tags input-filter))
                                     (seq (:filter/exclude-tags input-filter))
                                     (:filter/start-date input-filter)
                                     (:filter/end-date input-filter)
                                     (:filter/last-x-days input-filter))))
      (html
        [:div
         (if (or (seq transactions)
                 (some? (or (seq (:filter/include-tags input-filter))
                            (seq (:filter/exclude-tags input-filter))
                            (:filter/start-date input-filter)
                            (:filter/end-date input-filter)
                            (:filter/last-x-days input-filter))))
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
                                          :is-selected  (= (:db/id selected-transaction)
                                                           (:db/id props))
                                          :on-tag-click #(do
                                                          (om/update-state! this update-in [:tag-filter :filter/include-tags] filter/add-tag %)
                                                          (om/update-query! this assoc-in [:params :filter] (.filter this)))})))
                        (sort-by :transaction/created-at > transactions))]]]
                [:div.edit-transaction-form

                 (->SelectedTransaction sel-transaction-props)]]
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