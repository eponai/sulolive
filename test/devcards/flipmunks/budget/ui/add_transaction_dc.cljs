(ns flipmunks.budget.ui.add_transaction_dc
  (:require [devcards.core :as dc]
            [flipmunks.budget.ui.add_transaction :as a]
            [flipmunks.budget.ui.tag_dc :as t.dc]
            [om.next :as om]
            [goog.object :as gobj]
            [cljsjs.moment]
            [sablono.core :refer-macros [html]])
  (:require-macros [devcards.core :refer [defcard]]))

(def props {:query/all-currencies [:LEK :TBH :USD :SEK]})

(defcard add-transaction-without-data
         (a/add-transaction props))

(defcard add-transaction--complete
         (a/add-transaction (assoc props ::a/input-amount 123
                                         ::a/input-date (js/Date.)
                                         ::a/input-currency "SEK"
                                         ::a/input-title "Pizza"
                                         ::a/input-description "The pizza place was amazing! We should come back here when we're back in Stockholm."
                                         ::a/input-tag "Lunc"
                                         ::a/input-tags [(t.dc/tag-props "Stockholm" #(prn %))
                                                         (t.dc/tag-props "Sweden" #(prn %))])))
