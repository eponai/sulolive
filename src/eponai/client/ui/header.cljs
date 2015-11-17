(ns flipmunks.budget.ui.header
  (:require [om.next :as om :refer-macros [defui]]
            [flipmunks.budget.ui :refer [style]]
            [flipmunks.budget.parser :as parser]
            [flipmunks.budget.ui.modal :refer [->Modal]]
            [flipmunks.budget.ui.add_transaction :as add.t :refer [->AddTransaction]]
            [sablono.core :as html :refer-macros [html]]
            [garden.core :refer [css]]))

(defmethod parser/read :query/header
  [{:keys [state selector]} _ _]
  {:value (parser/pull-all state selector '[[?e :ui/singleton :budget/header]])})

(defui Header
  static om/IQueryParams
  (params [this] {:add-transaction (om/get-query add.t/AddTransaction)})
  static om/IQuery
  (query [this]
         '[{:query/header [:db/id ::show-transaction-modal]}
           {:proxy ?add-transaction}])                      ;; what to transact?
  Object
  (render
    [this]
    (let [{:keys [query/header proxy]} (om/props this)
          {:keys [db/id ::show-transaction-modal]} (first header)
          modal-trigger #(parser/cas! this id ::show-transaction-modal
                              show-transaction-modal
                              (not show-transaction-modal))]
      (html [:div (style {:display         "flex"
                          :flex-wrap       "no-wrap"
                          :justify-content "space-between"})
             [:div (style {:display "flex"
                           :width   "33%"})
              [:div "Logo"]]
             [:button (merge (style {:width "33%"})
                             {:on-click modal-trigger})
              "New Transaction"]
             [:div (style {:display        "flex"
                           :flex-direction "row-reverse"
                           :width          "33%"})
              [:button "logout"]
              [:button "settings"]]
             (when show-transaction-modal
               (->Modal {:dialog-content #(->AddTransaction proxy)
                         :on-close       modal-trigger}))]))))

(def ->Header (om/factory Header))