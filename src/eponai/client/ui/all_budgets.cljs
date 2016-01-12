(ns eponai.client.ui.all-budgets
  (:require [eponai.client.ui :refer-macros [style opts]]
            [garden.core :refer [css]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]))

(defui AllBudgets
  static om/IQuery
  (query [_]
    [:datascript/schema
     {:query/all-budgets [:db/id :transaction/_budget]}])
  Object
  (render [this]
    (let [{:keys [query/all-budgets] :as props} (om/props this)]
      (println "Props: " props)
      (html
        [:div
         [:ul
          {:class "nav nav-pills nav-justified"}
          [:li
           {:class "active"}
           [:a
            "tab 1"]]
          [:li
           [:a
            "tab 2"]]]
         [:div "This should be a tab view"]]))))

(def ->AllBudgets (om/factory AllBudgets))