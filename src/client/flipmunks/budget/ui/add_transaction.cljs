(ns flipmunks.budget.ui.add_transaction
  (:require [om.next :as om :refer-macros [defui]]
            [flipmunks.budget.ui :refer [style]]
            [sablono.core :as html :refer-macros [html]]
            [garden.core :refer [css]]))

(defn node [name on-change opts & children]
  (apply vector
         name
         (merge {:on-change on-change} opts)
         children))

(defn input [on-change opts]
  (node :input on-change opts))

(defn select [on-change opts children]
  (apply node :select on-change opts children))

(defn on-change [this k]
  #(om/update-state! this assoc k (.-value (.-target %))))

(defui AddTransaction
       om/IQuery
       (query [this] [:query/all-currencies])
       Object
       (render
         [this]
         (let [{:keys [query/all-currencies]} (om/props this)
               {:keys [::edit-amount ::edit-currency ::edit-title] :as state}
               ;; merging state with props, so that we can test the states
               ;; with devcards
               (merge (om/props this)
                      (om/get-state this))]
           (prn state)
           (html
             [:div
              [:h2 "New Transaction"]
              [:div [:span "Amount:"]
               (input (on-change this ::edit-amount)
                      (merge (style {:text-align "right"})
                             {:type        "number"
                              :placeholder "enter amount"
                              :value       edit-amount}))
               (select (on-change this ::edit-currency)
                       nil
                       (map #(let [v (name %)]
                              (vector :option
                                      (merge {:value v}
                                             (when (= v edit-currency)
                                               {:selected "selected"}))
                                      v))
                            all-currencies))]
              [:div [:span "Date:"]
               [:input {:type "text" :value "ENTER DATE LOL"}]]
              [:div [:span "Title:"]
               (input (on-change this ::edit-title)
                      {:type        "text"
                       :placeholder "enter title"
                       :value       edit-title})]]))))

(def add-transaction (om/factory AddTransaction))
