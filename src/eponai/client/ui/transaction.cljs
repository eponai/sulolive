(ns eponai.client.ui.transaction
  (:require [eponai.client.ui :refer-macros [style opts]]
            [eponai.client.ui.tag :as tag]
            [garden.core :refer [css]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]))

(defui Transaction
  static om/IQuery
  (query [_]
    [:db/id
     :transaction/uuid
     :transaction/name
     :transaction/amount
     :transaction/details
     :transaction/status
     {:transaction/currency '[*]}
     {:transaction/tags (om/get-query tag/Tag)}
     ::transaction-show-tags?])
  Object
  (render [this]
    (let [{transaction-name :transaction/name
           :keys            [transaction/amount
                             transaction/currency
                             transaction/tags
                             transaction/details]} (om/props this)
          {:keys [currency/code currency/symbol-native]} currency]
      (html
        [:div#transaction.list-group-item
         [:div#info
          (opts {:style
                 {:display        "flex"
                  :flex-direction "row"
                  :justify-content "space-between"
                  :width "100%"}})
          [:span#name
           (opts {:style {:margin-right "0.5em"}})
           transaction-name
           [:span.small.text-muted
            (opts {:style {:margin-left "0.5em"}})
            details]]

          [:span#amount
           (opts {:class "text-success"
                  :style {:margin-right "0.5em"}})
           (str amount " " (or symbol-native code))]]

         [:div#tags
          (opts {:style {:display "flex"
                         :flex-direction "row"
                         :flex-wrap "wrap"}})
          (if (seq tags)
            (map tag/->Tag
                 tags
                 (range 10)))]]))))
;; TODO: Why is this (range 10) here? Is it to limit the number of
;;       tags rendered? If so, use (take 10 (map ...))

(def ->Transaction (om/factory Transaction))
