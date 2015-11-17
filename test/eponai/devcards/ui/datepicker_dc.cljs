(ns eponai.devcards.ui.datepicker_dc
  (:require [devcards.core :as dc]
            [eponai.client.ui.datepicker :as d]
            [sablono.core :refer-macros [html]])
  (:require-macros [devcards.core :refer [defcard]]))

(defcard datepicker
         (d/->Datepicker {:value       (js/Date.)
                        :on-change   #(prn "on-changed called with: " %)
                        :placeholder "enter date"}))
