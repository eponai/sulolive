(ns eponai.mobile.ios.ui.transactions
  (:require-macros [natal-shell.components :refer [view text list-view]])
  (:require [om.next :as om :refer-macros [defui]]
            [eponai.client.ui :refer-macros [opts]]
            [taoensso.timbre :refer-macros [info debug error trace]]))

;;TODO: Is this needed everywhere?
;;      Can we set it once?
(set! js/React (js/require "react-native"))

(defui Transactions
  static om/IQuery
  (query [this]
    [{:query/transactions [:transaction/uuid
                           :transaction/title
                           :transaction/amount]}])
  Object
  (render [this]
    (let [{:keys [query/transactions]} (om/props this)
          ds (js/React.ListView.DataSource. #js{:rowHasChanged not=})]
      (debug "Rendering transactions: " transactions)
      (list-view {:dataSource (.cloneWithRows ds (clj->js (or transactions [])))
                  :renderRow  (fn [t]
                                (let [{:keys [title amount]} (js->clj t :keywordize-keys true)]
                                  (view (opts {:style {:flex-direction "row"}})
                                        (text (opts {:style {:font-size 30 :font-weight "100" :margin-bottom 20 :margin-right 30}})
                                              title)
                                        (text (opts {:style {:font-size 30 :font-weight "100" :margin-bottom 20}})
                                              amount))))}))))

(def ->Transactions (om/factory Transactions))