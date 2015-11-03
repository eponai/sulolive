(ns flipmunks.budget.ui.add_transaction
  (:require [om.next :as om :refer-macros [defui]]
            [flipmunks.budget.ui :refer [style]]
            [flipmunks.budget.ui.datepicker :as d]
            [flipmunks.budget.ui.tag :as t]
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

(defn select [on-change opts & children]
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
               {:keys [::input-amount ::input-currency ::input-title
                       ::input-date ::input-description ::input-tag ::input-tags] :as state}
               ;; merging state with props, so that we can test the states
               ;; with devcards
               (merge (om/props this)
                      (om/get-state this))]
           (prn state)
           (html
             [:div
              [:h2 "New Transaction"]
              [:div [:span "Amount:"]
               (input (on-change this ::input-amount)
                      (merge (style {:text-align "right"})
                             {:type        "number"
                              :placeholder "enter amount"
                              :value       input-amount}))
               (apply select (on-change this ::input-currency)
                      (when input-currency
                        {:default-value input-currency})
                      (map #(let [v (name %)]
                             (vector :option {:value v} v))
                           all-currencies))]
              [:div [:span "Date:"]
               (d/datepicker {:value       input-date
                              :placeholder "enter date"
                              :on-change   #(om/update-state! this assoc ::input-date %)})]
              [:div [:span "Title:"]
               (input (on-change this ::input-title)
                      {:type        "text"
                       :placeholder "enter title"
                       :value       input-title})]
              [:div [:span "Description:"]
               (node :textarea (on-change this ::input-description)
                     {:value       input-description
                      :placeholder "enter description"})]
              [:div [:span "Tags:"]
               [:div (style {:display "inline-block"})
                (input (on-change this ::input-tag)
                       {:type        "text"
                        :placeholder "enter tag"
                        :value       input-tag})]
               (map t/tag input-tags)]]))))

(def add-transaction (om/factory AddTransaction))
