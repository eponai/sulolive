(ns flipmunks.budget.ui.add_transaction_dc
  (:require [devcards.core :as dc]
            [flipmunks.budget.ui.add_transaction :as a]
            [flipmunks.budget.ui.tag :as t]
            [om.next :as om]
            [goog.object :as gobj]
            [cljsjs.moment]
            [sablono.core :refer-macros [html]])
  (:require-macros [devcards.core :refer [defcard]]))

(def props {:query/all-currencies [:LEK :TBH :USD :SEK]})

(defcard add-transaction-without-data
         (a/->AddTransaction props))

(defcard add-transaction--complete
         (a/->AddTransaction
           (assoc props ::a/input-amount 123
                        ::a/input-date (js/Date.)
                        ::a/input-currency "SEK"
                        ::a/input-title "Pizza"
                        ::a/input-description "The pizza place was amazing! We should come back here when we're back in Stockholm."
                        ;; Enter clears the other tags because we're using props
                        ;; as initial state, since we don't know how to create
                        ;; om compoments with initial state.
                        ::a/input-tag "Enter will clear others"
                        ::a/input-tags [(t/tag-props "Stockholm" #(prn %))
                                        (t/tag-props "Sweden" #(prn %))])))
