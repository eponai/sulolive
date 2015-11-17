(ns eponai.devcards.devcards_main
  (:require
    [devcards.core :as dc]
    [sablono.core :as html :refer-macros [html]]
    [eponai.devcards.ui.header_dc]
    [eponai.devcards.ui.datepicker_dc]
    [eponai.devcards.ui.modal_dc]
    [eponai.devcards.ui.tag_dc]
    [eponai.devcards.ui.add_transaction_dc]
    [eponai.devcards.ui.transactions_dc])
  (:require-macros
    [devcards.core :refer [defcard]]))

(defcard my-first-card
  (html [:h1 "Devcards is freaking awesome!"]))

