(ns eponai.web.ui.all-transactions
  (:require
    [cljs-time.core :as time]
    [datascript.core :as d]
    [eponai.client.lib.transactions :as lib.t]
    [eponai.client.ui :refer [map-all update-query-params!] :refer-macros [style opts]]
    [eponai.common.format.date :as date]
    [eponai.web.ui.add-transaction :refer [->AddTransaction AddTransaction]]
    [eponai.web.ui.daterangepicker :refer [->DateRangePicker]]
    [eponai.web.ui.utils :as utils]
    [eponai.web.ui.utils.filter :as filter]
    [garden.core :refer [css]]
    [goog.string :as gstring]
    [om.dom :as dom]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug]]))

(defn- trim-decimals [n] (int n))

(defn select-last-number
  "Given an input field of type number, selects the last digit (most valuable digit) in the input."
  [dom-node]
  (try
    ;; Inputs of type number cannot use selection range by default.
    ;; Using type text for the selection, then switching back to type number.
    (set! (.-type dom-node) "text")
    (let [sel-start (dec (.-length (.-value dom-node)))
          sel-start (if (neg? sel-start) 0 sel-start)]
      (.setSelectionRange dom-node sel-start sel-start))
    (finally
      (set! (.-type dom-node) "number"))))

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
      ;(debug "Delete tag: resulted diff: " diff)
      ;(debug "Delete tag updated state: " input-transaction)
      ;(debug "delete tag init state: " init-state)
      (when (seq diff)
        ;(debug "Delete tag Will transacti diff: " diff)
        (om/transact! this `[(transaction/edit ~(-> diff
                                                    (assoc :transaction/uuid (:transaction/uuid input-transaction))
                                                    (assoc :db/id (:db/id input-transaction))
                                                    (assoc :mutation-uuid (d/squuid))))
                             (transactions/deselect)
                             ;; TODO: Are all these needed?
                             ;; Copied from AddTransaction.
                             :query/selected-transaction
                             :query/dashboard
                             :query/all-projects
                             :query/transactions]))))

  (initLocalState [this]
    (let [props (om/props this)
          transaction (update props :transaction/tags (fn [tags] (sort-by :tag/name (map #(select-keys % [:tag/name]) tags))))]
      {:input-transaction transaction
       :init-state transaction}))

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
                                            {:on-apply #(do (om/update-state!
                                                              this assoc-in [:input-transaction :transaction/date]
                                                              %)
                                                            (.save-edit this))
                                             :format "MMM dd"})))

          ;; Amount in main currency
          (dom/div
            #js {:className "columns small-6 medium-3 large-2"}
            (if-let [rate (:conversion/rate conversion)]
              (dom/div
                nil
                (dom/small #js {:className "currency-code"}
                           (str (or (:currency/code (:user/currency user))
                                    (:currency/symbol-native (:user/currency user))) " "))
                (if (= (:db/ident type) :transaction.type/expense)
                  (dom/strong #js {:className "label alert"
                                   :style     #js {:padding "0.2em 0.3em"}}
                              (gstring/format "-%.2f" (/ amount rate)))
                  (dom/strong #js {:className "label success"
                                   :style     #js {:padding "0.2em 0.3em"}}
                              (gstring/format "%.2f" (/ amount rate)))))
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
            (dom/div #js {:style   #js {:fontFamily "monospace" :whiteSpace "pre"}
                          :onClick #(when-let [node (utils/ref-dom-node this (str "amount-" id))]
                                     (.focus node)
                                     (select-last-number node))}
              (utils/left-padding 10 (trim-decimals amount)))
            (dom/input
              #js {:style     #js {:fontFamily "monospace"}
                   :tabIndex  -1
                   :className "amount"
                   :value     (or amount "")
                   :type      "number"
                   :onChange  #(om/update-state! this assoc-in [:input-transaction :transaction/amount] (.-value (.-target %)))
                   :onKeyDown #(utils/on-enter-down % (fn [_]
                                                        (.blur (.-target %))))
                   :ref       (str "amount-" id)
                   :onBlur    #(.save-edit this)}))

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

(defn date-range-from-filter [f]
  (if (some? (:filter/last-x-days f))
    (let [t (date/today)]
      {:start-date (time/minus t (time/days (:filter/last-x-days f)))
       :end-date   t})
    {:start-date (date/date-time (:filter/start-date f))
     :end-date   (date/date-time (:filter/end-date f))}))
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
    {:list-size                         0
     :computed/transaction-on-tag-click #(do
                                          (om/update-state! this update-in [:tag-filter :filter/include-tags] utils/add-tag %)
                                          (om/update-query! this assoc-in [:params :filter] (.filter this)))
     :computed/tag-filter-on-change     #(do
                                          (om/update-state! this assoc :tag-filter {:filter/include-tags %})
                                          (om/update-query! this assoc-in [:params :filter] (.filter this)))
     :computed/date-filter-on-change    (fn [s e]
                                          (om/update-state! this assoc :date-filter {:filter/start-date (date/date-map s)
                                                                                     :filter/end-date   (date/date-map e)})
                                          (om/update-query! this assoc-in [:params :filter] (.filter this)))
     :computed/amount-filter-on-change  #(do
                                          (om/update-state! this assoc :amount-filter %)
                                          (om/update-query! this assoc-in [:params :filter] (.filter this)))})

  (componentDidMount [this]
    (when (zero? (:list-size (om/get-state this)))
      (debug "Updating list-size!")
      (om/update-state! this assoc :list-size 50)))

  (componentWillUnmount [this]
    (.deselect-transaction this))

  (select-transaction [this transaction]
    (om/transact! this `[(transactions/select ~{:transaction transaction})]))

  (deselect-transaction [this]
    (om/transact! this `[(transactions/deselect)]))

  (filter [this]
    (let [{:keys [date-filter tag-filter amount-filter]} (om/get-state this)]
      (merge tag-filter date-filter amount-filter)))

  (has-filter [_ filter]
    (some #(let [v (val %)] (if (coll? v) (seq v) (some? v))) filter))

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
    (let [{:keys [tag-filter date-filter amount-filter
                  computed/tag-filter-on-change
                  computed/date-filter-on-change
                  computed/amount-filter-on-change
                  computed/date-range-picker-on-apply]} (om/get-state this)]
      (html
        [:div.transaction-filters
         [:div.row.expanded
          [:div.columns.small-3
           (filter/->TagFilter (om/computed {:tags (:filter/include-tags tag-filter)}
                                            {:on-change tag-filter-on-change}))]
          [:div.columns.small-3
           (let [range (date-range-from-filter date-filter)]
             (->DateRangePicker (om/computed range
                                             {:on-apply date-filter-on-change})))]
          [:div.columns.small-6
           (filter/->AmountFilter (om/computed {:amount-filter amount-filter}
                                               {:on-change amount-filter-on-change}))]]])))

  (render-transaction-list [this transactions]
    (let [{currencies      :query/all-currencies
           user            :query/current-user} (om/props this)
          {:keys [computed/transaction-on-tag-click list-size]} (om/get-state this)]
      (html
        [:div
         (.render-filters this)
         [:div#all-transactions
          (if (seq transactions)
            [:div.transactions
             [:div.transaction-list
              (opts {:style {:width "100%"}})
              [:ul.no-bullet
               (map (fn [props]
                      (->Transaction
                        (om/computed props
                                     {:user         user
                                      :currencies   currencies
                                      :on-tag-click transaction-on-tag-click})))
                    ;; TODO: Implement some way of seeing more than this limit:
                    (take list-size (sort-by #(get-in % [:transaction/date :date/timestamp]) > transactions)))]]]
            [:div.empty-message
             [:div.lead
              [:i.fa.fa-search.fa-fw]
              "No transactions found with filters."]])]])))

  (render [this]
    (let [{transactions    :query/transactions
           add-transaction :proxy/add-transaction} (om/props this)
          {:keys [add-transaction?]} (om/get-state this)
          input-filter (.filter this)]
      (html
        [:div#txs
         (opts {:style {:padding "1em"}})
         (if (or (seq transactions)
                 (.has-filter this input-filter))
           (.render-transaction-list this transactions)
           (.render-empty-message this))
         (when add-transaction?
           (let [on-close #(om/update-state! this assoc :add-transaction? false)]
             (utils/modal {:content  (->AddTransaction (om/computed add-transaction {:on-close on-close}))
                           :on-close on-close})))]))))

(def ->AllTransactions (om/factory AllTransactions))