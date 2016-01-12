(ns eponai.client.ui.all-budgets
  (:require [eponai.client.ui :refer-macros [style opts]]
            [eponai.client.ui.all_transactions :refer [AllTransactions
                                                       ->AllTransactions]]
            [garden.core :refer [css]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]))

(defui AllBudgets
  static om/IQuery
  (query [_]
    [:datascript/schema
     {:query/all-budgets [:budget/uuid
                          :budget/name
                          {:budget/created-by
                           [:user/uuid]}
                          {:transaction/_budget
                           (om/get-query AllTransactions)}]}])
  Object
  (render [this]
    (let [{:keys [query/all-budgets] :as props} (om/props this)]
      (html
        [:div
         [:ul
          {:class "nav nav-tabs"}
          (map
            (fn [budget]
              [:li
               (opts {:class "active"})
               [:a
                [:label
                 (str "budget" (:budget/created-by budget))]]])
            all-budgets)
          [:li
           [:a
            [:label
             "New"]]]]

         [:div
          {:class "tab-content"}
          [:br]
          (map #(->AllTransactions (:transaction/_budget %)) all-budgets)]]))))

(def ->AllBudgets (om/factory AllBudgets))