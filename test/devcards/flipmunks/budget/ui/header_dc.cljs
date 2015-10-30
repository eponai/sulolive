(ns flipmunks.budget.ui.header_dc
  (:require [devcards.core :as dc]
            [flipmunks.budget.ui.header :as n]
            [sablono.core :refer-macros [html]])
  (:require-macros [devcards.core :refer [defcard]]))

(defcard generated-card
  (html [:div "generated. remove me from flipmunks.budget.ui.header"]))
