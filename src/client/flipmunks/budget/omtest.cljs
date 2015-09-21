(ns flipmunks.budget.omtest
  (:require [om.core :as om]
            [sablono.core :as html :refer-macros [html]]))

(defn widget [data owner]
  (reify
    om/IRender
    (render [this]
      (html [:div {:class "container"} 
             [:div {:class "page-header"}
              [:h1 (:text data)]]
             [:div {:class "row"}
              [:div {:class "col-md-4"} "col1"]
              [:div {:class "col-md-4"} "col2"]
              [:div {:class "col-md-4"} "col3"]]]))))

(defn run []
  (om/root widget 
           {:text "Hello Budget!"}
           {:target (. js/document (getElementById "my-app"))}))

