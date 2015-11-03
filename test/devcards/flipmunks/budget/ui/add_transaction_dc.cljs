(ns flipmunks.budget.ui.add_transaction_dc
  (:require [devcards.core :as dc]
            [flipmunks.budget.ui.add_transaction :as a]
            [om.next :as om]
            [goog.object :as gobj]
            [cljsjs.moment]
            [sablono.core :refer-macros [html]])
  (:require-macros [devcards.core :refer [defcard]]))

(def props {:query/all-currencies [:LEK :TBH :USD :SEK]})

(defcard add-transaction-without-data
         (a/add-transaction props))

(defcard add-transaction--complete
         (a/add-transaction (assoc props ::a/edit-amount 123
                                         ::a/edit-date (js/Date.)
                                         ::a/edit-currency "SEK"
                                         ::a/edit-title "Pizza"
                                         ::a/edit-description "The pizza place was amazing! We should come back here when we're back in Stockholm.")))
