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

(defmethod mutate 'dashboard/set-active-budget
  [{:keys [state]} _ {:keys [budget-uuid]}]
  {:action #(d/transact! state [{:ui/component                      :ui.component/dashboard
                                 :ui.component.dashboard/active-budget budget-uuid}])})
