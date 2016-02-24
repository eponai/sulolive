(ns eponai.client.parser.mutate
  (:require [datascript.core :as d]
            [eponai.common.parser.mutate :refer [mutate]]
            [taoensso.timbre :refer-macros [info debug error trace]]))

;;---------------- Modal show/hide
(defmethod mutate 'ui.modal/show
  [{:keys [state]} _ {:keys [content on-save]}]
  {:action #(d/transact! state [{:ui/singleton               :ui.singleton/modal
                                 :ui.singleton.modal/visible true
                                 :ui.singleton.modal/content content
                                 :ui.singleton.modal/on-save on-save}])})

(defmethod mutate 'ui.modal/hide
  [{:keys [state]} _ _]
  {:action #(d/transact! state [{:ui/singleton               :ui.singleton/modal
                                 :ui.singleton.modal/visible false}])})

;;------------ Profile menu show/hide
(defmethod mutate 'ui.menu/show
  [{:keys [state]} _ _]
  {:action #(d/transact! state [{:ui/singleton               :ui.singleton/menu
                                 :ui.singleton.menu/visible true}])})

(defmethod mutate 'ui.menu/hide
  [{:keys [state]} _ _]
  {:action #(d/transact! state [{:ui/singleton               :ui.singleton/menu
                                 :ui.singleton.menu/visible false}])})

(defmethod mutate 'ui.loader/show
  [{:keys [state]} _ _]
  {:action #(d/transact! state [{:ui/singleton                :ui.singleton/loader
                                 :ui.singleton.loader/visible true}])})

(defmethod mutate 'ui.loader/hide
  [{:keys [state]} _ _]
  {:action #(d/transact! state [{:ui/singleton                :ui.singleton/loader
                                 :ui.singleton.loader/visible false}])})

(defmethod mutate 'budget/set-active-uuid
  [{:keys [state]} _ {:keys [budget-uuid]}]
  {:action #(d/transact! state [{:ui/component             :ui.component/budget
                                 :ui.component.budget/uuid budget-uuid}])})

(defmethod mutate 'ui.component.budget/clear
  [{:keys [state]} _ _]
  {:action #(d/transact! state
                         [[:db.fn/retractAttribute [:ui/component :ui.component/budget] :ui.component.budget/uuid]])})

(defmethod mutate 'transactions.filter/update
  [{:keys [state]} _ {:keys [filter]}]
  {:action #(d/transact! state
                        [{:ui/component                     :ui.component/transactions
                          :ui.component.transactions/filter filter}])})

(defmethod mutate 'transactions.filter/clear
  [{:keys [state]} _ _]
  {:action #(d/transact! state
                         [[:db.fn/retractAttribute [:ui/component :ui.component/transactions] :ui.component.transactions/filter]])})
