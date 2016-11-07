(ns eponai.web.ui.project.add-transaction
  (:require
    [cljsjs.moment]
    [cljsjs.pikaday]
    [cljsjs.react-select]
    [datascript.core :as d]
    [eponai.client.ui :refer [map-all] :refer-macros [style opts]]
    [eponai.client.parser.message :as message]
    [eponai.common.format.date :as date]
    [eponai.web.ui.daterangepicker :refer [->DateRangePicker]]
    [eponai.web.ui.select :as sel]
    [eponai.web.ui.utils :as utils]
    [eponai.web.ui.utils.button :as button]
    [goog.string :as gstring]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [warn debug]]
    [eponai.web.ui.utils.css-classes :as css]
    [cljs-time.core :as t]))

(defn- delete-tag-fn [component tag]
  (om/update-state! component update-in [:input-transaction :transaction/tags] disj tag))

(defn- add-tag [component tag]
  (om/update-state! component
                    #(-> %
                         (assoc :input-tag "")
                         (update-in [:input-transaction :transaction/tags] conj tag))))

(defn transaction-fee-element [fee on-delete]
  (let [fee-type (:transaction.fee/type fee)
        fee-value (gstring/format "%.2f" (:transaction.fee/value fee))
        title (if (= fee-type :transaction.fee.type/absolute) "Fixed value" "Relative value")
        value (if (= fee-type :transaction.fee.type/absolute) (str "$ " fee-value) (str fee-value " %"))]
    [:div.transaction-fee
     ;[:div.content]
     [:strong value]
     [:span title]
     [:a.float-right
      {:on-click on-delete}
      "x"]]))

(defui OptionSelector
  Object
  (render [this]
    (let [{:keys [options]
           option-name :name} (om/props this)]
      (html
        [:div.option-selector
         (map (fn [opt i]
                (let [id (str option-name "-" i)]
                  [:div.option
                   [:input {:id id :type "radio" :name option-name}]
                   [:label {:for id}
                    [:span (:label opt)]]
                   ]))
              options
              (range))]))))

(def ->OptionSelector (om/factory OptionSelector))

(defui AddTransactionFee
  Object
  (save [this]
    (let [{:keys [on-save]} (om/get-computed this)
          {:keys [transaction-fee]} (om/get-state this)]
      (when on-save
        (on-save (-> transaction-fee
                     (update :transaction.fee/type #(:value %)))))))
  (initLocalState [_]
    {:transaction-fee {:transaction.fee/type {:label "$" :value :transaction.fee.type/absolute}}})
  (componentDidMount [this]
    (let [value-input (js/ReactDOM.findDOMNode (om/react-ref this "fee-value-input"))]
      (.focus value-input)))
  (render [this]
    (let [{:keys [all-currencies default]} (om/get-computed this)
          {:keys [transaction-fee]} (om/get-state this)]
      (debug "Will add transaction fee: " transaction-fee)
      (html
        [:div.add-new-transaction-fee
         [:div
          (sel/->SegmentedOption (om/computed {:options [{:label "$" :value :transaction.fee.type/absolute}
                                                         {:label "%" :value :transaction.fee.type/relative}]
                                               :name    "fee-selector"
                                               :value   (:transaction.fee/type transaction-fee)}
                                              {:on-select #(om/update-state! this assoc-in [:transaction-fee :transaction.fee/type] %)}))]
         [:div
          [:input {:type        "number"
                   :placeholder "Value"
                   :ref         "fee-value-input"
                   :on-change   #(om/update-state! this assoc-in [:transaction-fee :transaction.fee/value] (.. % -target -value))}]]
         (when (= (get-in transaction-fee [:transaction.fee/type :value]) :transaction.fee.type/absolute)
           [:div
            (sel/->Select {:options (map (fn [{:keys [currency/code db/id]}]
                                           {:label code
                                            :value id})
                                         all-currencies)
                           :value   (or (:transaction.fee/currency transaction-fee) default)})])
         [:a.button.small
          {:on-click
           #(.save this)}
          "Add"]]))))

(def ->AddTransactionFee (om/factory AddTransactionFee))

(defui AddTransaction
  static om/IQuery
  (query [_]
    [:query/message-fn
     {:query/all-currencies [:currency/code]}
     {:query/all-tags [:tag/name]}
     {:query/all-categories [:category/name]}])
  Object
  (add-transaction [this]
    (let [{:keys [::is-longterm?] :as st} (om/get-state this)
          update-category (fn [tx]
                               (let [{:keys [transaction/category]} tx]
                                 (if (nil? (:label category))
                                   (dissoc tx :transaction/category)
                                   (update tx :transaction/category (fn [{:keys [label _]}]
                                                                      {:category/name label})))))
          message-id (message/om-transact! this
                                           `[(transaction/create
                                               ~(-> (::input-transaction st)
                                                    (assoc :transaction/uuid (d/squuid))
                                                    (update :transaction/currency :value)
                                                    update-category
                                                    (update :transaction/tags (fn [ts]
                                                                                (map (fn [{:keys [label _]}]
                                                                                       {:tag/name label}) ts)))
                                                    ;(update :transaction/category (fn [{:keys [label _]}]
                                                    ;                                {:category/name label}))
                                                    (assoc :transaction/created-at (.getTime (js/Date.)))
                                                    (cond-> (not is-longterm?) (dissoc :transaction/end-date))))
                                             :routing/project])]
      (om/update-state! this assoc :pending-transaction message-id)))
  (initLocalState [this]
    (let [{:keys [query/all-currencies query/all-categories]} (om/props this)
          {:keys [project-id]} (om/get-computed this)
          usd-entity (some #(when (= (:currency/code %) "USD") %) all-currencies)
          category-entity (first all-categories)]
      {::input-transaction                  {:transaction/date     (date/date-map (date/today))
                                             :transaction/tags     #{}
                                             :transaction/currency {:label (:currency/code usd-entity)
                                                                    :value (:db/id usd-entity)}
                                             ;:transaction/category {:label (:category/name category-entity)
                                             ;                       :value (:db/id category-entity)}
                                             :transaction/project  project-id
                                             :transaction/type     :transaction.type/expense
                                             :transaction/fees      []}
       ::type                               :expense
       ::on-date-apply-fn                   #(om/update-state! this assoc-in [::input-transaction :transaction/date] %)
       ::on-end-date-apply-fn               #(om/update-state! this assoc-in [::input-transaction :transaction/end-date] %)
       ::is-longterm?                       false
       ::add-fee?                           false
       ::show-bank-fee-section?             false
       ::show-tags-input?                   false}))

  (componentDidUpdate [this _ _]
    (when-let [history-id (:pending-transaction (om/get-state this))]
      (let [{:keys [query/message-fn]} (om/props this)
            {:keys [on-close]} (om/get-computed this)
            message (message-fn history-id 'transaction/create)]
        (comment
          "Here's something we could do with the message if we want"
          " it to be syncronous."
          (when message
            (when on-close
              (on-close)))))))

  (render-bank-fee-section [this]
    (let [{:keys [query/all-currencies]} (om/props this)
          {:keys [::input-transaction ::add-fee?]} (om/get-state this)
          {:keys [transaction/fees transaction/currency]} input-transaction]
      (html
        [:div.row
         [:div.column.transaction-fee
          [:div
           (when-not (empty? fees)
             (map (fn [f i]
                    [:div.transaction-fee-container
                     {:key (str "fee " i)}
                     (transaction-fee-element f
                                              #(om/update-state! this update-in [::input-transaction :transaction/fees] (fn [fees]
                                                                                                                        (vec (concat
                                                                                                                               (subvec fees 0 i)
                                                                                                                               (subvec fees (inc i) (count fees)))))))])
                  fees
                  (range)))]
          [:div
           [:a
            {:on-click #(om/update-state! this assoc ::add-fee? true)}
            "+ Add new fee"]]
          (when add-fee?
            (utils/popup {:on-close #(om/update-state! this assoc ::add-fee? false)}
                         (->AddTransactionFee (om/computed {}
                                                           {:all-currencies all-currencies
                                                            :default        currency
                                                            :on-save        #(om/update-state! this (fn [st]
                                                                                                      (-> st
                                                                                                          (update-in [::input-transaction :transaction/fees] conj %)
                                                                                                          (assoc ::add-fee? false))))}))))]])))

  (render-tags-section [this]
    (let [{:keys [query/all-tags]} (om/props this)
          {:keys [::input-transaction]} (om/get-state this)]
      (html
        [:div.row.column
         (sel/->SelectTags (om/computed {:options (map (fn [{:keys [tag/name db/id]}]
                                                         {:label name
                                                          :value id})
                                                       all-tags)
                                         :value   (:transaction/tags input-transaction)}
                                        {:on-select #(om/update-state! this assoc-in [::input-transaction :transaction/tags] %)}))])))

  (render [this]
    (let [{:keys [::type
                  ::input-transaction
                  ::is-longterm?
                  ::show-tags-input?
                  ::show-bank-fee-section?

                  ::on-date-apply-fn
                  ::on-end-date-apply-fn]} (om/get-state this)
          {:keys [transaction/date transaction/end-date transaction/currency transaction/category]} input-transaction
          {:keys [query/all-currencies
                  query/all-categories]} (om/props this)]
      (html
        [:div#add-transaction
         [:h4.header "New Transaction"]
         [:div.top-bar-container.subnav
          {:class (condp = type
                    :expense "alert"
                    :income "success"
                    :atm "black")}
          [:ul.menu
           [:li
            [:a
             {:class    (when (= type :expense) "active")
              :on-click #(om/update-state! this (fn [st]
                                                  (-> st
                                                      (assoc ::type :expense)
                                                      (assoc-in [::input-transaction :transaction/type] :transaction.type/expense))))}
             "Expense"]]
           [:li
            [:a
             {:class    (when (= type :income) "active")
              :on-click #(om/update-state! this (fn [st]
                                                  (-> st
                                                      (assoc ::type :income)
                                                      (assoc-in [::input-transaction :transaction/type] :transaction.type/income))))}
             "Income"]]
           [:li
            [:a
             {:class    (when (= type :atm) "active")
              :on-click #(om/update-state! this assoc ::type :atm)}
             "ATM"]]]]
         [:div.content


          [:div.content-section
           [:div.row
            [:div.column.small-7
             [:input
              {:type        "number"
               :min         "0"
               :placeholder "Amount"
               :on-change   #(om/update-state! this assoc-in [::input-transaction :transaction/amount] (.. % -target -value))}]]
            [:div.column
             (sel/->Select (om/computed {:options (map (fn [{:keys [currency/code db/id]}]
                                                         {:label code
                                                          :value id})
                                                       all-currencies)
                                         :value currency}
                                        {:on-select #(om/update-state! this assoc-in [::input-transaction :transaction/currency] %)}))]]

           [:div.row
            [:div.column
             [:input
              {:type      "text"
               :placeholder "Title"
               :value     (:transaction/title input-transaction "")
               :on-change #(om/update-state! this assoc-in [::input-transaction :transaction/title] (.. % -target -value))}]]]

           [:div.row.column
            (sel/->Select (om/computed {:options     (map (fn [c]
                                                            {:label (:category/name c)
                                                             :value (:db/id c)})
                                                          all-categories)
                                        :value       category
                                        :clearable   true
                                        :placeholder "Category"}
                                       {:on-select #(om/update-state! this assoc-in [::input-transaction :transaction/category] %)}))]

           [:div.row
            [:div.column
             (->DateRangePicker (om/computed {:single-calendar? true
                                              :start-date       (date/date-time date)}
                                             {:on-apply on-date-apply-fn
                                              :format   "MMM dd"}))]

            [:div.column
            ; End date input field here
             (when is-longterm?
               (->DateRangePicker (om/computed {:single-calendar? true
                                                :start-date       (date/date-time end-date)}
                                               {:on-apply on-end-date-apply-fn
                                                :format   "MMM dd"})))
             ]]
           [:div.row.column
            [:div.long-term
             [:div.switch.tiny
              [:input.switch-input
               {:id       "long-term-switch"
                :type     "checkbox"
                :name     "long-term-switch"
                :on-click #(om/update-state! this (fn [st]
                                                    (cond-> (assoc st ::is-longterm? (.. % -target -checked))
                                                            (nil? (get-in st [::input-transaction :transaction/end-date]))
                                                            (assoc-in [::input-transaction :transaction/end-date] (date/date-map (t/plus (date/date-time date) (t/days 30)))))))}]
              [:label.switch-paddle {:for "long-term-switch"}]]
             [:span "Long term"]]]

           (cond-> []
                   show-tags-input?
                   (conj (.render-tags-section this))

                   show-bank-fee-section?
                   (conj (.render-bank-fee-section this)))


           ]



          [:div.content-section.clearfix
           [:div.float-left
            [:ul.menu
             [:li [:a {:on-click #(om/update-state! this update ::show-tags-input? not)} "Tags"]]
             [:li [:a {:on-click #(om/update-state! this update ::show-bank-fee-section? not)} "Bank fees"]]
             ;[:li [:a "Location"]]
             ]]
           ((-> button/button button/hollow button/black css/float-right)
             {:on-click #(do (.add-transaction this)
                             (let [on-close (:on-close (om/get-computed this))]
                               (on-close)))}
             "Save")]]]))))

(def ->AddTransaction (om/factory AddTransaction))

(defui QuickAddTransaction
  static om/IQuery
  (query [_]
    [:query/message-fn
     {:query/all-categories [:category/name]}
     {:query/all-currencies [:currency/code]}
     {:query/all-tags [:tag/name]}])
  Object
  (new-transaction [this]
    (let [{:keys [query/all-currencies]} (om/props this)
          {:keys [project]} (om/get-computed this)
          usd-entity (some #(when (= (:currency/code %) "USD") %) all-currencies)]
      {:transaction/date     (date/date-map (date/today))
       :transaction/tags     #{}
       :transaction/currency {:label (:currency/code usd-entity)
                              :value (:db/id usd-entity)}
       :transaction/project  (:db/id project)
       :transaction/type     :transaction.type/expense}))
  (initLocalState [this]
    {:is-open?          false
     :on-close-fn       #(.close this %)
     :on-keydown-fn     #(do
                          (when (= 13 (or (.-which %) (.-keyCode %)))
                            (.save this)))
     :input-transaction (.new-transaction this)})

  (open [this]
    (let [{:keys [is-open? on-close-fn on-keydown-fn]} (om/get-state this)]
      (when-not is-open?
        (om/update-state! this assoc :is-open? true)
        (.. js/document (addEventListener "click" on-close-fn)))))

  (mouse-event-outside [_ event]
    (let [includes-class-fn (fn [class-name class-names-str]
                              (let [class-array (clojure.string/split class-names-str #" ")]
                                (some #(when (= % class-name) %) class-array)))]
      (debug "Includes quick-add-input-section: " (some #(includes-class-fn "quick-add-input-section" (.-className %))
                                                        (.-path event)))
      (not (some #(includes-class-fn "quick-add-input-section" (.-className %))
                 (.-path event)))))

  (close [this event]
    (let [{:keys [on-close-fn is-open?]} (om/get-state this)
          should-close? (.mouse-event-outside this event)]
      (when (and is-open? should-close?)
        (om/update-state! this assoc :is-open? false)
        (.. js/document (removeEventListener "click" on-close-fn)))))

  (save [this]
    (let [st (om/get-state this)
          update-category (fn [tx]
                            (let [{:keys [transaction/category]} tx]
                              (if (nil? (:label category))
                                (dissoc tx :transaction/category)
                                (update tx :transaction/category (fn [{:keys [label _]}]
                                                                   {:category/name label})))))
          message-id (message/om-transact! this
                                           `[(transaction/create
                                               ~(-> (:input-transaction st)
                                                    (assoc :transaction/uuid (d/squuid))
                                                    (update :transaction/currency :value)
                                                    update-category
                                                    (update :transaction/tags (fn [ts]
                                                                                (map (fn [{:keys [label _]}]
                                                                                       {:tag/name label}) ts)))
                                                    ;(update :transaction/category (fn [{:keys [label _]}]
                                                    ;                                {:category/name label}))
                                                    (assoc :transaction/created-at (.getTime (js/Date.)))))
                                             :routing/project])
          new-transaction (.new-transaction this)
          ]
      (debug "Save new transaction: " (:input-transaction st) " input " (:input-amount st))
      (debug "Set new transaction: " new-transaction)
      (om/update-state! this assoc :is-open? false
                        :pending-transaction message-id
                        :input-transaction new-transaction)
      ))

  (componentDidUpdate [this _ _]
    (when-let [history-id (:pending-transaction (om/get-state this))]
      (let [{:keys [query/message-fn]} (om/props this)
            {:keys [on-close]} (om/get-computed this)
            message (message-fn history-id 'transaction/create)]
        (comment
          "Here's something we could do with the message if we want"
          " it to be syncronous."
          (when message
            (when on-close
              (on-close)))))))

  (render [this]
    (let [{:keys [query/all-categories query/all-currencies query/all-tags]} (om/props this)
          {:keys [is-open? input-amount input-transaction on-keydown-fn]} (om/get-state this)]
      (debug "input-amount: '" input-amount "'")
      (debug "Input Transaction: " input-transaction)
      (html
        [:div.quick-add-container
         {:on-key-down on-keydown-fn}
         [:div.row.column.quick-add
          [:ul.menu.quick-add-input-section
           {:class    (when is-open? "is-open")
            :on-click #(.open this)}
           [:li.attribute.note
            [:input {:value       (if is-open? (or (:transaction/amount input-transaction) "") "")
                     :type        "number"
                     :placeholder (if is-open? "0.00" "Quick add expense for today...")
                     :tabIndex    0
                     :on-key-down #(do
                                    (debug "keycode: " (.-keyCode %) " which: " (.-which %))
                                    (when (= 13 (or (.-which %) (.-keyCode %)))
                                      (debug "Blurring yes: ")
                                      (.blur (.-target %))
                                      ))
                     ;:on-change     #(om/update-state! this assoc :input-amount (.. % -target -value))
                     :on-change   #(om/update-state! this assoc-in [:input-transaction :transaction/amount] (.. % -target -value))
                     }]]
           [:li.attribute.currency
            (sel/->Select (om/computed {:value       (:transaction/currency input-transaction)
                                        :options     (map (fn [c]
                                                            {:label (:currency/code c)
                                                             :value (:db/id c)})
                                                          all-currencies)
                                        :placeholder "USD"
                                        :tab-index 0}
                                       {:on-select #(do
                                                     (debug "Got new value: " %)
                                                     (om/update-state! this assoc-in [:input-transaction :transaction/currency] %))}))]
           [:li.attribute.category
            (sel/->Select (om/computed {:value       (:transaction/category input-transaction)
                                        :options     (map (fn [c]
                                                            {:label (:category/name c)
                                                             :value (:db/id c)})
                                                          all-categories)
                                        :placeholder "Category..."
                                        :tab-index 0}
                                       {:on-select #(do
                                                     (debug "category event: " %)
                                                     (om/update-state! this assoc-in [:input-transaction :transaction/category] %))}))]
           [:li.attribute.tags
            (sel/->SelectTags (om/computed {:value             (:transaction/tags input-transaction)
                                            :options           (map (fn [t]
                                                                      {:label (:tag/name t)
                                                                       :value (:db/id t)})
                                                                    all-tags)
                                            :on-input-key-down #(do
                                                                 (debug "Selec tags input key event: " %)
                                                                 (.startPropagation %))
                                            :tab-index 0}
                                           {:on-select #(om/update-state! this assoc-in [:input-transaction :transaction/tags] %)}))]]

          [:div.actions
           {:class (when is-open? "show")}
           [:a.save-button
            {:on-click #(.save this)
             :tabIndex 0}
            [:i.fa.fa-check.fa-fw]]
           [:a.cancel-button
            {:on-click #(om/update-state! this assoc :input-transaction (.new-transaction this))
             :tabIndex 0}
            [:i.fa.fa-times.fa-fw]]]]]))))

(def ->QuickAddTransaction (om/factory QuickAddTransaction))