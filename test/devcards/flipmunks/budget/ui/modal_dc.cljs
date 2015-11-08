(ns flipmunks.budget.ui.modal_dc
  (:require [devcards.core :as dc]
            [flipmunks.budget.ui.modal :as n]
            [sablono.core :refer-macros [html]])
  (:require-macros [devcards.core :refer [defcard]]))

(defcard generated-card
  (html [:div "generated. remove me from flipmunks.budget.ui.modal"]))
