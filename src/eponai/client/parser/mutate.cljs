(ns eponai.client.parser.mutate
  (:require [eponai.common.database.transact :as t]
            [eponai.common.parser.mutate :refer [mutate]]
            [taoensso.timbre :refer-macros [info debug error trace]]))

;;---------------- Modal show/hide
(defmethod mutate 'ui.modal/show
  [{:keys [state]} _ _]
  {:action #(t/transact state [{:ui/singleton               :ui.singleton/modal
                                 :ui.singleton.modal/visible true}])})

(defmethod mutate 'ui.modal/hide
  [{:keys [state]} _ _]
  {:action #(t/transact state [{:ui/singleton               :ui.singleton/modal
                                 :ui.singleton.modal/visible false}])})

;;------------ Profile menu show/hide
(defmethod mutate 'ui.menu/show
  [{:keys [state]} _ _]
  {:action #(t/transact state [{:ui/singleton               :ui.singleton/menu
                                 :ui.singleton.menu/visible true}])})

(defmethod mutate 'ui.menu/hide
  [{:keys [state]} _ _]
  {:action #(t/transact state [{:ui/singleton               :ui.singleton/menu
                                 :ui.singleton.menu/visible false}])})

(defmethod mutate 'ui.loader/show
  [{:keys [state]} _ _]
  {:action #(t/transact state [{:ui/singleton                :ui.singleton/loader
                                 :ui.singleton.loader/visible true}])})

(defmethod mutate 'ui.loader/hide
  [{:keys [state]} _ _]
  {:action #(t/transact state [{:ui/singleton                :ui.singleton/loader
                                 :ui.singleton.loader/visible false}])})

(defmethod mutate 'budget/set-active-uuid
  [{:keys [state]} _ {:keys [budget-uuid]}]
  {:action #(t/transact state [{:ui/component             :ui.component/budget
                                 :ui.component.budget/uuid budget-uuid}])})

(defmethod mutate 'ui.component.budget/clear
  [{:keys [state]} _ _]
  {:action #(t/transact state
                         [[:db.fn/retractAttribute [:ui/component :ui.component/budget] :ui.component.budget/uuid]])})

(defmethod mutate 'transactions.filter/update
  [{:keys [state]} _ {:keys [filter]}]
  {:action #(t/transact state
                        [{:ui/component                     :ui.component/transactions
                          :ui.component.transactions/filter filter}])})

(defmethod mutate 'transactions.filter/clear
  [{:keys [state]} _ _]
  {:action #(t/transact state
                         [[:db.fn/retractAttribute [:ui/component :ui.component/transactions] :ui.component.transactions/filter]])})

(defmethod mutate 'transactions/deselect-transaction
  [{:keys [state]} _ ]
  {:action #(t/transact-one state [:db.fn/retractAttribute
                                   [:ui/component :ui.component/transactions]
                                   :ui.component.transactions/selected-transaction])})

(defmethod mutate 'transactions/select-transaction
  [{:keys [state]} _ {:keys [transaction]}]
  {:action #(t/transact-one state
                            {:ui/component                                   :ui.component/transactions
                             :ui.component.transactions/selected-transaction (:db/id transaction)})})
