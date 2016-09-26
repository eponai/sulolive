(ns eponai.web.ui.all-transactions
  (:require
    [cljs-time.core :as time]
    [cljs.reader :as reader]
    [clojure.string :as string]
    [datascript.core :as d]
    [eponai.client.lib.transactions :as lib.t]
    [eponai.client.ui :refer [map-all update-query-params!] :refer-macros [style opts]]
    [eponai.common.format.date :as date]
    [eponai.web.ui.add-transaction :refer [->AddTransaction AddTransaction]]
    [eponai.web.ui.daterangepicker :refer [->DateRangePicker]]
    [eponai.web.ui.utils :as utils]
    [eponai.web.ui.utils.filter :as filter]
    [eponai.web.ui.utils.infinite-scroll :as infinite-scroll]
    [garden.core :refer [css]]
    [goog.string :as gstring]
    [goog.events :as events]
    [om.dom :as dom]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug warn]]
    [clojure.data :as diff]))

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
                             :currency/symbol-native
                             :currency/name]}
     {:transaction/category [:category/name]}
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

  utils/ISyncStateWithProps
  (props->init-state [_ props]
    (let [transaction (-> props
                          (update :transaction/tags (fn [tags] (sort-by :tag/name (map #(select-keys % [:tag/name]) tags))))
                          (update :transaction/amount two-decimal-string))]
      {:input-transaction transaction
       :init-state transaction}))

  Object
  (initLocalState [this]
    (merge
      ;; Never change this computed function.
      {:computed/date-range-picker-on-apply #(do (om/update-state! this assoc-in
                                                                   [:input-transaction
                                                                    :transaction/date] %)
                                                 (.save-edit this))}
      (utils/props->init-state this (om/props this))))

  (componentWillReceiveProps [this props]
    (utils/sync-with-received-props this props))

  (add-tag [this tag]
    (om/update-state! this (fn [st]
                             (-> st
                                 (assoc :input-tag ""
                                        :add-tag? false)
                                 (update-in [:input-transaction :transaction/tags] #(utils/add-tag % tag))))))
  (delete-tag [this tag]
    (om/update-state! this update-in [:input-transaction :transaction/tags] #(utils/delete-tag % tag)))

  (save-edit [this]
    (let [{:keys [input-transaction
                  init-state]} (om/get-state this)
          diff (lib.t/diff-transaction input-transaction init-state)]
      ;; Transact only when we have a diff to avoid unecessary mutations.
      (debug "Saving edit. Diff: " diff " real diff: " (diff/diff input-transaction init-state))
      (when (seq diff)
        ;(debug "Delete tag Will transacti diff: " diff)
        (om/transact! this `[(transaction/edit ~(-> diff
                                                    (assoc :transaction/uuid (:transaction/uuid input-transaction))
                                                    (assoc :db/id (:db/id input-transaction))))
                             ;; TODO: Are all these needed?
                             ;; Copied from AddTransaction.
                             :routing/project]))))

  (render [this]
    (let [{:keys [input-transaction input-tag]} (om/get-state this)
          {:keys [db/id
                  transaction/date
                  transaction/currency
                  transaction/amount
                  transaction/uuid
                  transaction/type
                  transaction/conversion
                  transaction/title]
           :as   transaction} (or input-transaction (om/props this))
          {:keys [computed/date-range-picker-on-apply]} (om/get-state this)
          {:keys [user on-tag-click currencies]} (om/get-computed this)]
      (dom/li
        nil
        (dom/div
          #js {:className "row collapse expanded"}

          ;; Date
          (dom/div
            #js {:className "columns small-6 medium-3 large-1"}

            (->DateRangePicker (om/computed {:single-calendar? true
                                             :start-date       (date/date-time date)}
                                            {:on-apply date-range-picker-on-apply
                                             :format   "MMM dd"})))

          ;; Amount in main currency
          (dom/div
            #js {:className "columns small-6 medium-3 large-2"}
            (if-let [rate (:conversion/rate conversion)]
              (dom/div
                nil
                (dom/span #js {:className "currency-code"}
                           (str (or (:currency/code (:user/currency user))
                                    (:currency/symbol-native (:user/currency user))) " "))
                (let [amount (cond-> amount (string? amount) (reader/read-string))]
                  (if (= (:db/ident type) :transaction.type/expense)
                   (dom/strong #js {:className "label alert"
                                    :style     #js {:padding "0.2em 0.3em"}}
                               (str "-" (two-decimal-string (/ amount rate))))
                   (dom/strong #js {:className "label success"
                                    :style     #js {:padding "0.2em 0.3em"}}
                               (two-decimal-string (/ amount rate))))))
              (dom/i #js {:className "fa fa-spinner fa-spin"})))

          ;; Amount in local currency
          (dom/div
            #js {:className "columns small-6 medium-3 large-2"}

            (html
              [:select.currency-code
               {:value     (:currency/code currency)
                :on-change #(do (om/update-state! this assoc-in [:input-transaction :transaction/currency] {:currency/code (.-value (.-target %))})
                                (.save-edit this))}
               (map-all
                 currencies
                 (fn [c]
                   [:option
                    {:key   (str (:currency/code c))
                     :value (:currency/code c)}
                    (str (or (:currency/symbol-native c)
                             (:currency/code c)) " ")]))])
            (dom/div #js {:className "whitespace"
                          :style   #js {:fontFamily "monospace" :whiteSpace "pre"}
                          :onClick #(when-let [node (utils/ref-dom-node this (str "amount-" id))]
                                     (.focus node)
                                     (select-first-digit node))}
              (utils/left-padding 10 (trim-decimals amount)))
            (dom/input
              #js {:style     #js {:fontFamily "monospace"}
                   :tabIndex  -1
                   :className "amount"
                   :value     (or amount "")
                   :pattern   "[0-9]+([\\.][0-9]+)?"
                   :type      "text"
                   :onChange  #(om/update-state! this assoc-in [:input-transaction :transaction/amount] (.-value (.-target %)))
                   :onKeyDown #(condp = (.-keyCode %)
                                ;; Prevent default on comma and period/dot.
                                events/KeyCodes.COMMA (.preventDefault %)
                                events/KeyCodes.PERIOD (when (string/includes? (str amount) ".") (.preventDefault %))
                                ;; Save edit on enter.
                                events/KeyCodes.ENTER (utils/on-enter-down % (fn [_] (.blur (.-target %))))
                                true)
                   :ref       (str "amount-" id)
                   :onBlur    #(do
                                (let [val (.-value (.-target %))]
                                  ;; When the value entered doesn't have 2 decimals, set it to be 2 decimals.
                                  ;; TODO: What if we need more decimals? What happens? This change was made
                                  ;;       to fix UI issues when there are less than 2 decimals.
                                  (when (not= val (two-decimal-string val))
                                    (om/update-state! this assoc-in [:input-transaction :transaction/amount] (two-decimal-string val))))
                                (.save-edit this))}))

          ;; Title
          (dom/div
            #js {:className "columns small-12 medium-3 large-2"}
            (dom/input
              #js {:className "title"
                   :value     (or title "")
                   :type      "text"
                   :onChange  #(om/update-state! this assoc-in [:input-transaction :transaction/title] (.-value (.-target %)))
                   :onKeyDown #(utils/on-enter-down % (fn [_]
                                                        (.blur (.-target %))))
                   :onBlur    #(.save-edit this)}))

          ;; Tags
          (dom/div
            #js {:className "columns small-10 large-5 end"
                 :id "all-transactions-transaction-tags"}
            (apply dom/div
                   nil
                   (map-all (sort-by :tag/name (:transaction/tags transaction))
                            (fn [tag]
                              (utils/tag tag {:on-click  #(do
                                                           (on-tag-click tag))
                                              :on-delete (fn []
                                                           (debug "Delete tag: " tag)
                                                           (.delete-tag this tag)
                                                           (.save-edit this))}))))
            (dom/input
              #js {:className "tags"
                   :type        "text"
                   :value       (or (:tag/name input-tag) "")
                   :onChange    #(om/update-state! this assoc :input-tag {:tag/name (.-value (.-target %))})
                   :onKeyDown   (fn [e]
                                  (utils/on-enter-down e (fn [t]
                                                           (.add-tag this {:tag/name t})
                                                           (.blur (.-target e)))))
                   :onBlur      #(.save-edit this)
                   :placeholder "Enter to add tag"})))))))

(def ->Transaction (om/factory Transaction {:keyfn :db/id}))

(defn date-range-from-filter [{:keys [filter/last-x-days filter/start-date filter/end-date]}]
  (if (some? last-x-days)
    (let [t (date/today)]
      {:start-date (time/minus t (time/days last-x-days))
       :end-date   t})
    {:start-date (date/date-time start-date)
     :end-date   (date/date-time end-date)}))

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
     {:query/all-currencies [:currency/code]}
     {:query/all-projects [:project/uuid
                           :project/name]}
     {:proxy/add-transaction (om/get-query AddTransaction)}])

  Object
  (initLocalState [this]
    {:computed/transaction-on-tag-click   (fn [tag]
                                            (om/update-query! this update-in [:params :filter :filter/include-tags]
                                                              #(utils/add-tag % tag)))
     :computed/tag-filter-on-change       (fn [tags]
                                            (om/update-query! this update-in [:params :filter]
                                                              #(assoc % :filter/include-tags tags)))
     :computed/amount-filter-on-change    (fn [{:keys [filter/min-amount filter/max-amount]}]
                                            (om/update-query! this update-in [:params :filter]
                                                              #(assoc % :filter/min-amount min-amount
                                                                        :filter/max-amount max-amount)))
     :computed/date-range-picker-on-apply (fn [{:keys [start-date end-date selected-range]}]
                                            (om/update-query! this update-in [:params :filter]
                                                              #(assoc % :filter/start-date (date/date-map start-date)
                                                                        :filter/end-date (date/date-map end-date))))
     :computed/infinite-scroll-node-fn    (fn []
                                            ;; TODO: Un-hack this.
                                            ;; HACK: I don't know how we can get the page-content div in any other way.
                                            (some-> (om/get-reconciler this)
                                                    (om/app-root)
                                                    (om/react-ref (str :eponai.web.ui.root/page-content-ref))
                                                    (js/ReactDOM.findDOMNode)))})

  (componentDidMount [this]
    (when (zero? (:list-size (om/get-state this)))
      (debug "Updating list-size!")
      (om/update-state! this assoc :list-size 50)))

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

  (render-filters [this]
    (let [{:keys [computed/tag-filter-on-change
                  computed/amount-filter-on-change
                  computed/date-range-picker-on-apply]} (om/get-state this)
          {:keys [filter/include-tags] :as filters} (:filter (om/get-params this))]
      (debug "render-filters, params: " (om/get-params this))
      (html
        [:div.transaction-filters
         (opts {:style {:padding "1em 0"}})
         [:div.row.expanded
          [:div.columns.small-3
           (filter/->TagFilter (om/computed {:tags include-tags}
                                            {:on-change tag-filter-on-change}))]
          [:div.columns.small-3
           (let [range (date-range-from-filter filters)]
             (->DateRangePicker (om/computed range
                                             {:on-apply date-range-picker-on-apply})))]
          [:div.columns.small-6
           (filter/->AmountFilter (om/computed {:amount-filter (select-keys filters [:filter/min-amount :filter/max-amount])}
                                               {:on-change amount-filter-on-change}))]]])))

  (render-transaction-list [this transactions]
    (let [{currencies      :query/all-currencies
           user            :query/current-user} (om/props this)
          {:keys [computed/transaction-on-tag-click
                  computed/infinite-scroll-node-fn]} (om/get-state this)]
      (html
        [:div
         (.render-filters this)
         [:div#all-transactions
          (if (seq transactions)
            [:div.transactions
             [:div.transaction-list
              (opts {:style {:width "100%"}})
              (infinite-scroll/->InfiniteScroll
                (om/computed
                  {:elements-container :ul.no-bullet
                   :elements           (into [] (map (fn [props]
                                                       (->Transaction
                                                         (om/computed props
                                                                      {:user         user
                                                                       :currencies   currencies
                                                                       :on-tag-click transaction-on-tag-click}))))
                                             transactions)}
                  {:dom-node-fn infinite-scroll-node-fn}))]]
            [:div.empty-message
             [:div.lead
              [:i.fa.fa-search.fa-fw]
              "No transactions found with filters."]])]])))

  (render [this]
    (let [{transactions    :query/transactions
           add-transaction :proxy/add-transaction} (om/props this)
          {:keys [add-transaction?]} (om/get-state this)]
      (html
        [:div#txs
         (if (or (seq transactions)
                 (.has-filter this))
           (.render-transaction-list this transactions)
           (.render-empty-message this))
         (when add-transaction?
           (let [on-close #(om/update-state! this assoc :add-transaction? false)]
             (utils/modal {:content  (->AddTransaction (om/computed add-transaction {:on-close on-close}))
                           :on-close on-close})))]))))

(def ->AllTransactions (om/factory AllTransactions))