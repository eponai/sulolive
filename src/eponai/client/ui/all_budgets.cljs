(ns eponai.client.ui.all-budgets
  (:require [eponai.client.ui :refer-macros [style opts]]
            [eponai.client.ui.all_transactions :refer [AllTransactions
                                                       ->AllTransactions]]
            [garden.core :refer [css]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [datascript.core :as d]))

(defn on-change [this k]
  #(om/update-state! this assoc k (.-value (.-target %))))

(defn on-add-budget-key-down [this state]
  (fn [key]
    (when (and (= 13 (.-keyCode key))
               (seq (.. key -target -value)))
      (om/transact! this `[(budget/create ~(-> state
                                               (assoc :input-uuid (d/squuid))))
                           :query/all-budgets])
      (om/update-state! this assoc ::creating-new-budget false))))

(defui AllBudgets
  static om/IQuery
  (query [_]
    [:datascript/schema
     {:query/all-budgets [:budget/uuid
                          :budget/name
                          {:transaction/_budget
                           (om/get-query AllTransactions)}]}])
  Object
  (initLocalState [_]
    {::creating-new-budget false})

  (render [this]
    (let [{:keys [query/all-budgets]} (om/props this)

          {new-budget ::creating-new-budget
           ;; TODO: Move active-tab to datascript/app-state?
           active-tab ::active-tab
           :keys [input-budget-name] :as state} (om/get-state this)
          active-tab (or active-tab (first all-budgets))]
      (html
        [:div
         [:ul
          {:id ""
           :class "nav nav-tabs"}

          (when-let [budget (first all-budgets)]
            [:li
             {:class (if (= (:budget/uuid active-tab)
                            (:budget/uuid budget)) "active" "")
              :on-click #(om/update-state! this assoc ::active-tab budget)}
             [:a
              (or (:budget/name (first all-budgets)) "Untitled")]])
          (for [budget (rest all-budgets)]
            [:li
             (opts {:key [(:budget/uuid budget)]
                    :class    (if (= (:budget/uuid active-tab)
                                     (:budget/uuid budget)) "active" "")
                    :on-click #(om/update-state! this assoc ::active-tab budget)})
             [:a
              [:label
               (or (:budget/name budget) "Untitled")]]])

          (if (false? new-budget)
            [:li
             [:a
              [:label
               {:on-click #(om/update-state! this assoc ::creating-new-budget true)}
               "New"]]]
            [:li
             [:a
              [:input
               {:on-change (on-change this :input-budget-name)
                :on-key-down (on-add-budget-key-down this state)
                :value input-budget-name}]]])]

         [:br]
         [:div
          {:class "tab-content"}
          (let [budget (some #(if (= (:budget/uuid %)
                                     (:budget/uuid active-tab))
                               %
                               nil)
                             all-budgets)]
            (->AllTransactions (:transaction/_budget budget)))]]))))

(def ->AllBudgets (om/factory AllBudgets))