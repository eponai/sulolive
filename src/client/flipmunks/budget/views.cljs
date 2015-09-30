(ns flipmunks.budget.views
  (:require
    [devcards.core :as dc]  ; <-- here
    [sablono.core :as sab]) ; just for this example
  (:require-macros
    [devcards.core :refer [defcard]])) ; <-- and here

(defcard my-first-card
  (sab/html [:h1 "Devcards is freaking awesome!"]))
