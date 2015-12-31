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
                             ::transaction-show-tags?]} (om/props this)]
      (html
        [:div#transaction
         (opts {:style {:display "flex"
                        :flex-direction "column"
                        :align-items "flex-start"
                        :width "100%"
                        :margin-left "0.5em"}
                :class "list-group-item-text"})
         [:style
          (css [:#transaction
                {:background-color #"fff"}
                [:&:hover {:background-color "rgb(250,250,250)"}]
                [:&:active {:background-color "#eee"}]])]
         [:div#info
          (opts {:style
                 {:display        "flex"
                  :flex-direction "row"
                  :justify-content "space-between"
                  :width "100%"}})
          [:p#name
           (opts {:class "text-primar"
                  :style {:margin-right "0.5em"}})
           transaction-name]

          [:p#amount
           (opts {:class "text-success"
                  :style {:margin-right "0.5em"}})
           (str (:currency/code currency) " " amount)]]

         (if (seq tags)
           (map
             (fn [tag]
               [:button
                {:class "btn btn-default btn-xs"}
                (:tag/name tag)])
             tags)
           )]))))
;Object
       ;(render [this]
       ;        (let [{;; rename to avoid replacing clojure.core/name
       ;               transaction-name :transaction/name
       ;               :keys            [transaction/tags
       ;                                 transaction/amount
       ;                                 transaction/currency
       ;                                 transaction/details
       ;                                 transaction/status
       ;                                 ::transaction-show-tags?]} (om/props this)]
       ;          (html
       ;            [:div
       ;             [:style (css [:#ui-transaction {:background-color #"fff"}
       ;                           [:&:hover {:background-color "rgb(250,250,250)"}]
       ;                           [:&:active {:background-color "#eee"}]])]
       ;             [:div (-> (style {:display        "flex"
       ;                               :flex-direction "column"
       ;                               :border-style   "solid"
       ;                               :border-width   "1px 0px 0px"
       ;                               :border-color   "#ddd"
       ;                               :padding        "0.5em"})
       ;                       (assoc :id "ui-transaction"))
       ;              [:div (style {:display         "flex"
       ;                            :flex-direction  "row"
       ;                            :justify-content "flex-start"
       ;                            })
       ;               [:div (str amount " " (:currency/code currency))]
       ;               [:div (style {:margin-left "0.5em"
       ;                             :font-weight "bold"
       ;                             :color       (if status
       ;                                            (condp = status
       ;                                              :transaction.status/synced "green"
       ;                                              :transaction.status/pending "orange"
       ;                                              :transaction.status/failed "red")
       ;                                            "blue")})
       ;                transaction-name]]
       ;              (when details
       ;                [:div (style {:margin    "0em 1.0em"
       ;                              :padding   "0.3em"
       ;                              :fontStyle "italic"})
       ;                 details])
       ;              (when transaction-show-tags?
       ;                [:div
       ;                 (map tag/->Tag tags)])]]))))

(def ->Transaction (om/factory Transaction))
