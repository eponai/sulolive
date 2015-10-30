(ns flipmunks.budget.ui.add_transaction
  (:require [om.next :as om :refer-macros [defui]]
            [flipmunks.budget.ui :refer [style]]
            [sablono.core :as html :refer-macros [html]]
            [garden.core :refer [css]]))

(defui AddTransaction
       om/IQuery
       (query [this] [:query/all-currencies])
       Object
       (render
         [this]
         (let [{:keys [all-currencies]} (om/props this)]
           (html
             [:div
              [:h2 "New Transaction"]
              [:div [:span "Amount: "]
               [:input {:type      "text"
                        :on-change #(prn (.-value (.-target %)))}]]]))))

(def add-transaction (om/factory AddTransaction))
