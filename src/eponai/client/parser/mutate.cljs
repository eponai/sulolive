(ns eponai.client.parser.mutate
  (:require [datascript.core :as d]
            [eponai.common.parser.mutate :refer [mutate]]))

;;---------------- Modal show/hide
(defmethod mutate 'ui.modal/show
  [{:keys [state]} _ _]
  {:action #(d/transact! state [{:ui/singleton               :ui.singleton/modal
                                 :ui.singleton.modal/visible true}])})

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