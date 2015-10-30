(ns flipmunks.budget.ui.add_transaction
  (:require [om.next :as om :refer-macros [defui]]
            [flipmunks.budget.ui :refer [style]]
            [sablono.core :as html :refer-macros [html]]
            [garden.core :refer [css]]))

(defui AddTransaction
       om/IQuery
       (query [this] [])
       Object
       (render [this]
               (html [:div "Foo"])))

(def add-transaction (om/factory AddTransaction))