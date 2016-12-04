(ns eponai.server.ui.store
  (:require
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.server.ui.common :as common]))

(defui Store
  Object
  (render [this]
    (let [{:keys [release?]} (om/props this)]
      (dom/html
        {:lang "en"}

        (apply dom/head nil (common/head release?))

        (dom/body
          nil
          (common/navbar nil)
          (dom/div nil "This is a store page."))))))
