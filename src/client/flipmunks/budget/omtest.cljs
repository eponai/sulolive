(ns flipmunks.budget.omtest
  (:require [om.core :as om]
            [om.dom :as dom]))

(defn widget [data owner]
  (reify
    om/IRender
    (render [this]
      (dom/h1 nil (:text data)))))

(defn run []
  (om/root widget {:text "Hello Budget!"}
           {:target (. js/document (getElementById "my-app"))}))

