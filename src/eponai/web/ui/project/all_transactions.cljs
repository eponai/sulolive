(ns eponai.web.ui.project.all-transactions
  (:require
    [cljs.reader :as reader]
    [eponai.client.lib.transactions :as lib.t]
    [eponai.common.report :as report]
    [eponai.client.ui :refer [map-all update-query-params!] :refer-macros [style opts]]
    [eponai.common.format.date :as date]
    [eponai.web.ui.project.add-transaction :as at :refer [->AddTransaction AddTransaction]]
    [eponai.web.ui.daterangepicker :refer [->DateRangePicker]]
    [eponai.web.ui.utils :as utils]
    [garden.core :refer [css]]
    [eponai.web.ui.select :as sel]
    [goog.string :as gstring]
    [om.dom :as dom]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug warn]]))

(defn- trim-decimals [n] (int n))

(defn select-first-digit
  "Selects the first digit (most valuable digit) in the input dom node."
  [dom-node]
  ;; make sure we're in text mode
  (when (not= "text" (.-type dom-node))
    (warn "Dom node was not in type text. Was: " (.-type dom-node) " for dom node: " dom-node))
  (.setSelectionRange dom-node 0 0))

(defn two-decimal-string [s]
  (gstring/format "%.2f" (str s)))

(defn empty-sorted-tag-set []
  (sorted-set-by #(compare (:tag/name %) (:tag/name %2))))

(defn total-amount [tx]
  (report/amount-with-fees (update tx :transaction/amount
                                   #(cond-> % (string? %) (reader/read-string)))))

;; ################### Om next components ###################

(defui Transaction
  static om/IQuery
  (query [_] lib.t/full-transaction-pull-pattern)

  utils/ISyncStateWithProps
  (props->init-state [_ props]
    (let [transaction (-> props
                          (->> (into {} (filter #(or (= "transaction" (namespace (key %)))
                                                     (= :db/id (key %))))))
                          (update :transaction/amount two-decimal-string)
                          (update :transaction/tags
                                  (fn [tags]
                                    (into (empty-sorted-tag-set)
                                          (map #(select-keys % [:tag/name]))
                                          tags))))]
      {:input-transaction transaction
       :init-state transaction}))

  Object
  (initLocalState [this]
    (utils/props->init-state this (om/props this)))

  (componentWillReceiveProps [this props]
    (utils/sync-with-received-props this props))

  (render [this]
    (let [{:keys [input-transaction]} (om/get-state this)
          {:keys [db/id
                  transaction/date
                  transaction/currency
                  transaction/amount
                  transaction/type
                  transaction/conversion
                  transaction/category
                  transaction/title]
           :as   transaction} (or input-transaction (om/props this))
          {:keys [menu-open?]} (om/get-state this)
          {:keys [currencies edit-transaction-fn delete-transaction-fn]} (om/get-computed this)]
      (dom/li
        #js {:className "row collapse align-middle is-collapse-child"
             :id        id}
        ;; Amount in main currency
        (dom/div
          #js {:className "amount"}
          (if-let [converted-amount (total-amount transaction)]
            (if (= (:db/ident type) :transaction.type/expense)
              (dom/strong #js {:className "expense"}
                          (str "-" (two-decimal-string converted-amount)))
              (dom/strong #js {:className "income"}
                          (two-decimal-string converted-amount)))
            (dom/i #js {:className "fa fa-spinner fa-spin"})))

        ;; Date
        (dom/div
          #js {:className "date"}
          (date/date->string date "MMM dd"))
        (dom/div
          #js {:className "category"}
          (:category/name category))

        ;; Title
        (dom/div
          #js {:className "note"}
          title)

        ;; Tags
        (dom/div
          #js {:className "tags"}
          (sel/->SelectTags {:value    (map (fn [t]
                                              {:label (:tag/name t)
                                               :value (:db/id t)})
                                            (:transaction/tags transaction))
                             :disabled true}))

        ;; Amount in local currency
        (dom/div
          #js {:className "local-amount text-right"}
          amount)
        (dom/div
          #js {:className "currency"}
          (sel/->Select {:value    {:label (:currency/code currency) :value (:db/id currency)}
                         :options  (map (fn [c]
                                          {:label (:currency/code c)
                                           :value (:db/id c)})
                                        currencies)
                         :disabled true}))
        (dom/div
          #js {:className "more-menu text-right"}
          (dom/a
            #js {:className ""
                 :onClick #(om/update-state! this assoc :menu-open? true)}
            (dom/i
              #js {:className "fa fa-ellipsis-v fa-fw"}))
          (when menu-open?
            (dom/div #js {:className "text-left"}
              (let [close-dropdown #(om/update-state! this assoc :menu-open? false)]
                (utils/dropdown {:on-close #(do
                                             (close-dropdown)
                                             (.stopPropagation %))}
                                [:li
                                 {:key (str "transaction-edit-" (:db/id input-transaction))}
                                 [:a {:on-click #(do
                                                      (close-dropdown)
                                                      (edit-transaction-fn input-transaction))} "Edit"]]
                                [:li
                                 {:key (str "transaction-delete-" (:db/id input-transaction))}
                                 [:a {:on-click #(do
                                                      (close-dropdown)
                                                      (delete-transaction-fn input-transaction))} "Delete"]])))))))))

(def ->Transaction (om/factory Transaction {:keyfn :db/id}))

(defui AllTransactions
  static om/IQueryParams
  (params [_]
    {:filter {}})
  static om/IQuery
  (query [_]
    [`({:query/transactions ~(om/get-query Transaction)} {:filter ~'?filter})
     {:query/current-user [:user/uuid
                           {:user/currency [:currency/code
                                            :currency/symbol-native]}]}
     {:query/all-currencies [:currency/code]}
     {:query/all-projects [:project/uuid
                           :project/name]}
     {:query/all-categories [:category/name]}
     {:query/all-tags [:tag/name]}
     {:proxy/add-transaction (om/get-query AddTransaction)}
     {:proxy/quick-add-transaction (om/get-query at/QuickAddTransaction)}])

  Object
  (initLocalState [this]
    {:list-size                           0

     :computed/infinite-scroll-node-fn    (fn []
                                            ;; TODO: Un-hack this.
                                            ;; HACK: I don't know how we can get the page-content div in any other way.
                                            (some-> (om/get-reconciler this)
                                                    (om/app-root)
                                                    (om/react-ref (str :eponai.web.ui.root/page-content-ref))
                                                    (js/ReactDOM.findDOMNode)))
     :computed/select-tags-options-fn     (fn []
                                            (sel/tags->options (:query/all-tags (om/props this))))
     :computed/edit-transaction-fn        (fn [t]
                                            (om/update-state! this assoc :edit-transaction t))
     :computed/delete-transaction-fn      (fn [t]
                                            (om/transact! this `[(transaction/delete ~{:transaction t})
                                                                 :query/transactions]))})

  (ensure-list-size [this]
    (when (> 20 (:list-size (om/get-state this)))
      (debug "Updating list-size!")
      (om/update-state! this update :list-size + 10)))

  (componentDidMount [this _ _]
    (.ensure-list-size this))

  (componentDidUpdate [this _ _]
    (.ensure-list-size this))

  (has-filter [this]
    (some #(let [v (val %)] (if (coll? v) (seq v) (some? v)))
          (:filter (om/get-params this))))

  (render-empty-message [this]
    (html
      [:div
       [:a.button
        (opts {:style {:visibility :hidden}})
        "Button"]
       [:div.empty-message.text-center
        ;[:i.fa.fa-usd.fa-4x]
        ;[:i.fa.fa-eur.fa-4x]
        ;[:i.fa.fa-yen.fa-4x]
        [:i.fa.fa-th-list.fa-5x]
        [:div.lead
         "Information underload, no transactions are added."
         [:br]
         [:br]
         "Start tracking your money and "
         [:a.link
          {:on-click #(om/update-state! this assoc :add-transaction? true)}
          "add a transaction"]
         "."]]]))

  (render-transaction-list [this transactions]
    (let [{currencies      :query/all-currencies
           user            :query/current-user} (om/props this)
          {:keys [computed/edit-transaction-fn
                  computed/delete-transaction-fn
                  list-size]} (om/get-state this)]
      (html
        [:div

         [:div.content-section
          [:div.row.column.collapse
           [:div.transactions-container

            [:div.transactions-header.row.align-middle.collapse.is-collapse-child
             ;[] :div.row.collapse.expanded
             [:div.amount
              (str "Amount (" (:currency/code (:user/currency user)) ")")]
             [:div.date
              "Date"]
             [:div.category
              "Category"]
             [:div.note
              "Note"]
             [:div.tags
              "Tags"]
             [:div.local-amount.text-right
              "Amount"]
             [:div.currency
              "Currency"]]
            (if (seq transactions)
              [:ul.transactions-list
               (into [] (comp (take list-size)
                              (map (fn [props]
                                     (->Transaction
                                       (om/computed props
                                                    {:currencies             currencies
                                                     :edit-transaction-fn    edit-transaction-fn
                                                     :delete-transaction-fn  delete-transaction-fn})))))
                     transactions)
               ;; List footer:
               (if (zero? list-size)
                 (dom/li #js {:className (str "row collapse align-middle is-collapse-child"
                                              " transactions-load-more")}
                   (dom/span #js {:key "loading-more-transactions"}
                             "Loading..."))
                 (dom/li #js {:className (str "row collapse align-middle is-collapse-child"
                                              " transactions-load-more")}
                   (dom/a #js {:key     "load-more-button"
                               :onClick #(om/update-state! this update :list-size + 25)}
                          "Load more")))]
              [:div.empty-message
               [:div.lead
                [:i.fa.fa-search.fa-fw]
                "No transactions found with filters."]])]]]])))

  (render [this]
    (let [{:keys [query/transactions proxy/add-transaction proxy/quick-add-transaction]} (om/props this)
          {:keys [project]} (om/get-computed this)
          {:keys [add-transaction? edit-transaction]} (om/get-state this)]
      (html
        [:div#txs
         (at/->QuickAddTransaction (om/computed quick-add-transaction
                                                {:project project}))
         (when (some? edit-transaction)
           (utils/modal {:content (->AddTransaction (om/computed add-transaction
                                                                 {:on-close #(om/update-state! this dissoc :edit-transaction)
                                                                  :input-transaction edit-transaction}))
                         :on-close #(om/update-state! this dissoc :edit-transaction)}))
         (if (seq transactions)
           (.render-transaction-list this transactions)
           (.render-empty-message this))
         (when add-transaction?
           (let [on-close #(om/update-state! this assoc :add-transaction? false)]
             (utils/modal {:content  (->AddTransaction (om/computed add-transaction {:on-close on-close}))
                           :on-close on-close})))]))))

(def ->AllTransactions (om/factory AllTransactions))