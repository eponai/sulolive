(ns eponai.devcards.ui.add_transaction_dc
  (:require [devcards.core :as dc]
            [eponai.client.ui.add_transaction :as a]
            [eponai.client.ui.tag :as t]
            [om.next :as om]
            [goog.object :as gobj]
            [cljsjs.moment]
            [sablono.core :refer-macros [html]])
  (:require-macros [devcards.core :refer [defcard]]))

(def props {:query/all-currencies [{:currency/code :LEK} {:currency/code :TBH} {:currency/code :USD} {:currency/code :SEK}]})

(def input-data
  {:input-amount      123
   :input-date        (js/Date.)
   :input-currency    "SEK"
   :input-title       "Pizza"
   :input-description "The pizza place was amazing! We should come back here when we're back in Stockholm."
   ;; Enter clears the other tags because we're using props
   ;; as initial state, since we don't know how to create
   ;; om compoments with initial state.
   :input-tag         "Enter will clear others"
   :input-tags        [(t/tag-props "Stockholm" #(prn %))
                       (t/tag-props "Sweden" #(prn %))]})

(defcard add-transaction-without-data
         (a/->AddTransaction props))

(defcard add-transaction--complete
         (a/->AddTransaction (merge props input-data)))

(defcard
  add-transaction-many-tags
  (let [tag-fn (fn [name]
                 (t/tag-props name #(prn %)))
        input (merge input-data
                     {:input-tags (mapv tag-fn
                                       (range 1000 1020))})]
    (a/->AddTransaction (merge props input))))