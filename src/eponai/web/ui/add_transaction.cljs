(ns eponai.web.ui.add-transaction
  (:require
    [cljsjs.moment]
    [cljsjs.pikaday]
    [datascript.core :as d]
    [eponai.client.ui :refer [map-all] :refer-macros [style opts]]
    [eponai.common.format.date :as date]
    [eponai.web.ui.datepicker :refer [->Datepicker]]
    [eponai.web.ui.utils :as utils]
    [garden.core :refer [css]]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
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
     {:query/all-projects [:project/uuid
                          :project/name]}])
  Object
  (initLocalState [this]
    (let [{:keys [query/all-currencies
                  query/all-projects]} (om/props this)]
      {:input-transaction {:transaction/date     (date/date-map (date/today))
                           :transaction/tags     #{}
                           :transaction/currency {:currency/code (-> all-currencies
                                                                     first
                                                                     :currency/code)}
                           :transaction/project  {:project/uuid (-> all-projects
                                                                    first
                                                                    :project/uuid)}
                           :transaction/type     :transaction.type/expense}}))
  (add-transaction [this]
    (let [st (om/get-state this)]
      (om/transact! this
                    `[(transaction/create
                        ~(-> (:input-transaction st)
                             (assoc :mutation-uuid (d/squuid))
                             (assoc :transaction/uuid (d/squuid))
                             (assoc :transaction/created-at (.getTime (js/Date.)))
                             ;(update :transaction/date (fn [d] {:date/ymd (f/date->ymd-string d)}))
                             ))
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
  (select-project [this project-uuid]
    (om/update-state! this assoc-in [:input-transaction :transaction/project :project/uuid] (uuid project-uuid)))

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
                  query/all-projects]} (om/props this)
          {:keys [input-tag
                  input-transaction]} (om/get-state this)
          {:keys [transaction/date transaction/tags transaction/currency
                  transaction/project transaction/amount transaction/title
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
           "Project:"]
          [:select
           {:on-change     #(.select-project this (.-value (.-target %)))
            :type          "text"
            :default-value project}
           (map-all all-projects
                    (fn [project]
                      [:option
                       (opts {:value (:project/uuid project)
                              :key   [(:db/id project)]})
                       (or (:project/name project) "Untitled")]))]

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
                   :value       (or amount "")
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
            :value     (or title "")}]

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