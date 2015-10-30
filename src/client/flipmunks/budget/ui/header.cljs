(ns flipmunks.budget.ui.header
  (:require [om.next :as om :refer-macros [defui]]
            [flipmunks.budget.ui :refer [style]]
            [sablono.core :as html :refer-macros [html]]
            [garden.core :refer [css]]))

(defui Header
       om/IQueryParams
       (params [this] {:user []})
       om/IQuery
       (query [this]
              '[{:query/current-user ?user}])
       Object
       (render [this]
               (html [:div (style {:display         "flex"
                                   :flex-wrap       "no-wrap"
                                   :justify-content "space-between"})
                      [:div (style {:display       "flex"
                                    :width         "33%"})
                       [:div "Logo"]]
                      [:button (-> (style {:width "33%"}) {:on-submit #(prn %)})
                       "New Transaction"]
                      [:div (style {:display    "flex"
                                    :flex-direction "row-reverse"
                                    :width "33%"})
                       [:button "logout"]
                       [:button "settings"]]])))

(def header (om/factory Header))