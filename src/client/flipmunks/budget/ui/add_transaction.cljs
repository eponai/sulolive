(ns flipmunks.budget.ui.add_transaction
  (:require [om.next :as om :refer-macros [defui]]
            [flipmunks.budget.ui :refer [style]]
            [flipmunks.budget.ui.datepicker :as d]
            [sablono.core :as html :refer-macros [html]]
            [cljsjs.pikaday]
            [cljsjs.moment]
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
               {:keys [::edit-amount ::edit-currency ::edit-title
                       ::edit-date] :as state}
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
               (d/datepicker {:value edit-date
                            :placeholder "enter date"
                            :on-change #(om/update-state!
                                         this
                                         assoc
                                         ::edit-date
                                         %)})]
              [:div [:span "Title:"]
               (input (on-change this ::edit-title)
                      {:type        "text"
                       :placeholder "enter title"
                       :value       edit-title})]]))))

(def add-transaction (om/factory AddTransaction))
