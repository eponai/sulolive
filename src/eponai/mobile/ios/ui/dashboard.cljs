(ns eponai.mobile.ios.ui.dashboard
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.mobile.components :refer [navigator-ios view text]]
    [eponai.mobile.components.nav :as nav]
    [om.next :as om :refer-macros [defui]]))

(defui Main
  Object
  (render [this]
    (view (opts {:style {:flex 1}}) (text nil "This is dashboard main page"))))

(def ->Main (om/factory Main))

(defui Dashboard
  static om/IQuery
  (query [this]
    [{:query/transactions [:transaction/uuid]}])
  Object
  (render [this]
    (nav/clean-navigator
      {:initial-route {:title     "Dashboard"
                       :component ->Main}})))

(def ->Dashboard (om/factory Dashboard))
