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
     {:transaction/currency [:currency/code]}
     {:transaction/tags (om/get-query tag/Tag)}
     ::transaction-show-tags?])
  Object
  (render [this]
    (let [{transaction-name :transaction/name
           :keys            [transaction/amount
                             transaction/currency
                             transaction/tags
                             transaction/details]} (om/props this)]
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
           (str (:currency/code currency) " " amount)]]

         [:div#tags
          (opts {:style {:display "flex"
                         :flex-direction "row"
                         :flex-wrap "wrap"}})
          (if (seq tags)
            (map
              (fn [tag]
                [:button
                 {:class "btn btn-info btn-xs"}
                 (:tag/name tag)])
              tags
              (range 10))
            )]]))))
(def ->Transaction (om/factory Transaction))
