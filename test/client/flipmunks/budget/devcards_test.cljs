(ns flipmunks.budget.devcards_test
  (:require
    [devcards.core :as dc]
    [sablono.core :as html :refer-macros [html]]
    [flipmunks.budget.ui.transactions])
  (:require-macros
    [devcards.core :refer [defcard]]))

(defcard my-first-card
  (html [:h1 "Devcards is freaking awesome!"]))

