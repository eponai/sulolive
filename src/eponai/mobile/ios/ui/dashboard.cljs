(ns eponai.mobile.ios.ui.dashboard
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.mobile.components :refer [text view list-view]]
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
  (initLocalState [this]
    (let [ds (js/ReactNative.ListView.DataSource. #js {:rowHasChanged (fn [prev next]
                                                                        (not= prev next))})]
      {:data-source (.cloneWithRows ds #js ["test"])}))
  (render [this]
    (let [{:keys [data-source]} (om/get-state this)]
      (list-view
        (opts {:style {:paddingTop 10}
               :dataSource data-source
               :renderRow (fn [row-data]
                            (->DashboardWidget))})))))

(def ->Dashboard (om/factory Dashboard {:keyfn #(str Dashboard)}))
