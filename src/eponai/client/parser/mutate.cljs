(ns eponai.client.parser.mutate
  (:require [datascript.core :as d]
            [eponai.common.parser.mutate :refer [mutate]]
            [taoensso.timbre :refer-macros [info debug error trace]]))

;;---------------- Modal show/hide
(defmethod mutate 'ui.modal/show
  [{:keys [state]} _ {:keys [content]}]
  {:action #(d/transact! state [{:ui/singleton               :ui.singleton/modal
                                 :ui.singleton.modal/visible true
                                 :ui.singleton.modal/content content}])})

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

(defmethod mutate 'dashboard/set-active-budget
  [{:keys [state]} _ {:keys [budget-uuid]}]
  {:action #(d/transact! state [{:ui/component                      :ui.component/dashboard
                                 :ui.component.dashboard/active-budget budget-uuid}])})


(defn update-tag-filter [tx-action {:keys [state]} _ {:keys [tag/name]}]
  {:action #(let [ret (d/transact state [[tx-action
                                     [:ui/component :ui.component/all-transactions]
                                     :ui.component.all-transactions/filter-tags
                                     name]])]
             (prn "ui.comp.trans: " (into {} (d/entity (d/db state) [:ui/component :ui.component/all-transactions]))))})

(defmethod mutate 'transactions.filter.tags/add-tag
  [& args]
  (apply update-tag-filter :db/add args))

(defmethod mutate 'transactions.filter.tags/remove-tag
  [& args]
  (apply update-tag-filter :db/retract args))
