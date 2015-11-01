(ns flipmunks.budget.ui.add_transaction
  (:require [om.next :as om :refer-macros [defui]]
            [flipmunks.budget.ui :refer [style]]
            [sablono.core :as html :refer-macros [html]]
            [garden.core :refer [css]]))

(defn input [this k opts]
  [:input (-> opts
              (assoc :on-change #(om/update-state! this k (.-value (.-target %)))))])

(defui AddTransaction
       om/IQuery
       (query [this] [:query/all-currencies])
       Object
       (render
         [this]
         (let [{:keys [query/all-currencies]} (om/props this)
               {:keys [ui.add-transaction/edit-amount
                       ui.add-transaction/edit-currency
                       ui.add-transaction/edit-title
                       ui.add-transaction/edit-date]} (om/get-state this)]
           (prn "DATE: " edit-date)
           (html
             [:div
              [:h2 "New Transaction"]
              [:div [:span "Amount:"]
               (input this :ui.add-transaction/edit-amount
                      (apply assoc
                             (style {:text-align "right"})
                             :type "number"
                             :placeholder "enter amount"
                             :value edit-amount))
               [:select {:on-change #(om/update-state!
                                      this
                                      assoc
                                      :ui.add-transaction/edit-currency
                                      (.-value (.-target %)))}
                (map #(vector :option (merge {:value %}
                                             (when (= % edit-currency)
                                               {:selected "selected"}))
                              %)
                     all-currencies)]]
              [:div [:span "Date:"]
               [:input {:type "text" :value "ENTER DATE LOL"}]]
              [:div [:span "Title:"]
               (input this :ui.add-transaction/edit-title {:type        "text"
                                                           :placeholder "enter title"
                                                           :value       edit-title})]]))))

(def add-transaction (om/factory AddTransaction))
