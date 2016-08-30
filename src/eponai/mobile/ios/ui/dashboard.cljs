(ns eponai.mobile.ios.ui.dashboard
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.mobile.components :refer [text view list-view]]
    [eponai.mobile.components.table-view :as tv]
    [om.next :as om :refer-macros [defui]]))

(defui DashboardWidget
  Object
  (render [this]
    (view
      (opts {:style {:borderWidth 1
                     :height 150}})
      (text nil "This is a widget"))))

(def ->DashboardWidget (om/factory DashboardWidget))

(defui Dashboard
  Object
  (render [this]
    (tv/->TableView
      (om/computed {:rows ["test"]}
                   {:render-row (fn [row-data]
                                  (->DashboardWidget))}))))

(def ->Dashboard (om/factory Dashboard {:keyfn #(str Dashboard)}))
