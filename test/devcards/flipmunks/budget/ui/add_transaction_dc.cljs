(ns flipmunks.budget.ui.add_transaction_dc
  (:require [devcards.core :as dc]
            [flipmunks.budget.ui.add_transaction :as n]
            [sablono.core :refer-macros [html]])
  (:require-macros [devcards.core :refer [defcard]]))

(defcard add-transaction
  (n/add-transaction))
