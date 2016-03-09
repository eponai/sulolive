(ns eponai.client.ui.add_transaction
  (:require [om.next :as om :refer-macros [defui]]
            [eponai.client.ui :refer [map-all] :refer-macros [style opts]]
            [eponai.client.ui.datepicker :refer [->Datepicker]]
            [eponai.client.ui.utils :as utils]
            [eponai.common.format :as format]
            [sablono.core :refer-macros [html]]
            [cljsjs.pikaday]
            [cljsjs.moment]
            [garden.core :refer [css]]
            [datascript.core :as d]))

(defn- delete-tag-fn [component tag]
    (om/update-state! component update :input/tags disj tag))

(defn- add-tag [component tag]
  (om/update-state! component
                    #(-> %
                         (assoc :input/tag "")
                         (update :input/tags conj tag))))

(defn- add-transaction [component st]
  (om/transact! component
                `[(transaction/create
                    ~(-> st
                         (assoc :mutation-uuid (d/squuid))
                         (update :input/date format/date->ymd-string)
                         (assoc :input/uuid (d/squuid))
                         (assoc :input/created-at (.getTime (js/Date.)))
                         (dissoc :input/tag)))
                  :proxy/all-transactions
                  :query/dashboard
                  :query/all-budgets]))

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
      {:input/date     (js/Date.)
       :input/tags     #{}
       :input/currency (-> all-currencies
                           first
                           :currency/code)
       :input/budget   (-> all-budgets
                           first
                           :budget/uuid)
       :input/type :transaction.type/expense}))
  (toggle-input-type [this]
    (let [{:keys [input/type]} (om/get-state this)]
      (cond (= type :transaction.type/expense)
            (om/update-state! this assoc :input/type :transaction.type/income)

            (= type :transaction.type/income)
            (om/update-state! this assoc :input/type :transaction.type/expense))))

  (ui-config [this]
    (let [{:keys [input/type]} (om/get-state this)]
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
          {:keys [input/date input/tags input/currency input/budget
                  input/tag input/amount input/title input/type]}
          ;; merging state with props, so that we can test the states
          ;; with devcards
          (merge (om/props this)
                 (om/get-state this))
          {:keys [on-close]} (om/get-computed this)
          {:keys [btn-class i-class]} (.ui-config this)]
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
           {:on-change     (utils/on-change this :input/budget)
            :type          "text"
            :default-value budget}
           (map-all all-budgets
                    (fn [budget]
                      [:option
                       (opts {:value (:budget/uuid budget)
                              :key   [(:budget/uuid budget)]})
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
                   :on-change   (utils/on-change this :input/amount)})]

           [:select.input-group-field
            (opts {:on-change     (utils/on-change this :input/currency)
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
           {:on-change (utils/on-change this :input/title)
            :type      "text"
            :value     title}]

          [:label
           "Date:"]

          ; Input date with datepicker

          (->Datepicker
            (opts {:key       [::date-picker]
                   :value     date
                   :on-change #(om/update-state! this assoc :input/date %)}))

          [:label
           "Tags:"]

          (utils/tag-input
            {:input-tag     tag
             :selected-tags tags
             :on-change     #(om/update-state! this assoc :input/tag %)
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
                  :on-click #(do (add-transaction this (om/get-state this))
                                 (on-close))})
           "Save"]]]))))

(def ->AddTransaction (om/factory AddTransaction))