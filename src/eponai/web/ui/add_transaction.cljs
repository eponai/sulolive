(ns eponai.web.ui.add-transaction
  (:require [om.next :as om :refer-macros [defui]]
            [eponai.client.ui :refer [map-all] :refer-macros [style opts]]
            [eponai.web.ui.datepicker :refer [->Datepicker]]
            [eponai.web.ui.utils :as utils]
            [eponai.common.format :as format]
            [sablono.core :refer-macros [html]]
            [cljsjs.pikaday]
            [cljsjs.moment]
            [garden.core :refer [css]]
            [datascript.core :as d]
            [eponai.common.format :as f]
            [taoensso.timbre :refer-macros [debug]]))

(defn- delete-tag-fn [component tag]
    (om/update-state! component update-in [:input-transaction :transaction/tags] disj tag))

(defn- add-tag [component tag]
  (om/update-state! component
                    #(-> %
                         (assoc :input-tag "")
                         (update-in [:input-transaction :transaction/tags] conj tag))))

(defui AddTransaction
  static om/IQuery
  (query [_]
    [{:query/all-currencies [:currency/code]}
     {:query/all-budgets [:budget/uuid
                          :budget/name]}])
  Object
  (initLocalState [this]
    (let [{:keys [query/all-currencies
                  query/all-budgets]} (om/props this)]
      {:input-transaction {:transaction/date     (js/Date.)
                           :transaction/tags     #{}
                           :transaction/currency {:currency/code (-> all-currencies
                                                                     first
                                                                     :currency/code)}
                           :transaction/budget   {:budget/uuid (-> all-budgets
                                                                   first
                                                                   :budget/uuid)}
                           :transaction/type     :transaction.type/expense}}))
  (add-transaction [this]
    (let [st (om/get-state this)]
      (om/transact! this
                    `[(transaction/create
                        ~(-> (:input-transaction st)
                             (assoc :mutation-uuid (d/squuid))
                             (assoc :transaction/uuid (d/squuid))
                             (assoc :transaction/created-at (.getTime (js/Date.)))
                             (update :transaction/date (fn [d] {:date/ymd (f/date->ymd-string d)}))))
                      :query/transactions
                      :query/dashboard])))

  (toggle-input-type [this]
    (let [{:keys [input-transaction]} (om/get-state this)
          type (:transaction/type input-transaction)]
      (cond (= type :transaction.type/expense)
            (om/update-state! this assoc-in [:input-transaction :transaction/type] :transaction.type/income)

            (= type :transaction.type/income)
            (om/update-state! this assoc-in [:input-transaction :transaction/type] :transaction.type/expense))))

  (select-date [this date]
    (om/update-state! this assoc-in [:input-transaction :transaction/date] date))
  (select-budget [this budget-uuid]
    (om/update-state! this assoc-in [:input-transaction :transaction/budget :budget/uuid] (uuid budget-uuid)))

  (ui-config [this]
    (let [{:keys [input-transaction]} (om/get-state this)
          type (:transaction/type input-transaction)]
      (cond (= type :transaction.type/expense)
            {:btn-class "button alert small"
             :i-class "fa fa-minus"}

            (= type :transaction.type/income)
            {:btn-class "button success small"
             :i-class "fa fa-plus"})))
  (render
    [this]
    (let [{:keys [query/all-currencies
                  query/all-budgets]} (om/props this)
          {:keys [input-tag
                  input-transaction]} (om/get-state this)
          {:keys [transaction/date transaction/tags transaction/currency
                  transaction/budget transaction/amount transaction/title
                  transaction/type]} input-transaction
          {:keys [on-close]} (om/get-computed this)
          {:keys [btn-class
                  i-class] :as conf} (.ui-config this)]
      (html
        [:div

         [:div
          {:class (if (= type :transaction.type/expense) "alert" "success")}
          [:h3
           (if (= type :transaction.type/expense)
             "New expense"
             "New income")]]

         [:div
          [:label
           "Sheet:"]
          [:select
           {:on-change     #(.select-budget this (.-value (.-target %)))
            :type          "text"
            :default-value budget}
           (map-all all-budgets
                    (fn [budget]
                      [:option
                       (opts {:value (:budget/uuid budget)
                              :key   [(:db/id budget)]})
                       (or (:budget/name budget) "Untitled")]))]

          [:label
           "Amount:"]
          ;; Input amount with currency
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

          [:label.form
           "Title:"]

          [:input
           {:on-change (utils/on-change-in this [:input-transaction :transaction/title])
            :type      "text"
            :value     title}]

          [:label
           "Date:"]

          ; Input date with datepicker

          (->Datepicker
            (opts {:key       [::date-picker]
                   :value     date
                   :on-change #(.select-date this %)}))

          [:label
           "Tags:"]

          (utils/tag-input
            {:input-tag     input-tag
             :selected-tags tags
             :on-change     #(om/update-state! this assoc :input-tag %)
             :on-add-tag    #(add-tag this %)
             :on-delete-tag #(delete-tag-fn this %)
             :placeholder   "Select tags..."})]

         [:div
          (opts {:style {:float :right}})
          [:a
           (opts {:class    "button secondary"
                  :on-click on-close})
           "Cancel"]
          [:button.btn.btn-md
           (opts {:class    "button"
                  :on-click #(do (.add-transaction this)
                                 (on-close))})
           "Save"]]]))))

(def ->AddTransaction (om/factory AddTransaction))