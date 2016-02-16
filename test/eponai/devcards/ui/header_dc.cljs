(ns eponai.devcards.ui.header_dc
  (:require [devcards.core :as dc]
            [eponai.client.ui.navbar :as h]
            [sablono.core :refer-macros [html]])
  (:require-macros [devcards.core :refer [defcard]]))

(defcard header-has-new-transacion-button
         (h/navbar-create {}))

