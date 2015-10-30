(ns flipmunks.budget.ui.header_dc
  (:require [devcards.core :as dc]
            [flipmunks.budget.ui.header :as h]
            [sablono.core :refer-macros [html]])
  (:require-macros [devcards.core :refer [defcard]]))

(defcard header-has-new-transacion-button
         (h/header {}))

