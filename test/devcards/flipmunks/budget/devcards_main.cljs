(ns flipmunks.budget.devcards_main
  (:require
    [devcards.core :as dc]
    [sablono.core :as html :refer-macros [html]]
    [flipmunks.budget.ui.header_dc]
    [flipmunks.budget.ui.datepicker_dc]
    [flipmunks.budget.ui.add_transaction_dc]
    [flipmunks.budget.ui.transactions_dc])
  (:require-macros
    [devcards.core :refer [defcard]]))

(defcard my-first-card
  (html [:h1 "Devcards is freaking awesome!"]))

