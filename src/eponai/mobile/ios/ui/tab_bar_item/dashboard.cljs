(ns eponai.mobile.ios.ui.tab-bar-item.dashboard
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.mobile.components :refer [navigator-ios view text]]
    [om.next :as om :refer-macros [defui]]))

(defui Main
  Object
  (render [this]
    (view nil (text nil "This is dashboard main page"))))

(def ->Main (om/factory Main))

(defui Dashboard
  static om/IQuery
  (query [this]
    [{:query/transactions [:transaction/uuid]}])
  Object
  (render [this]
    (navigator-ios
      (opts
        {:initialRoute {:title "Dashboard"
                        :component ->Main}}))))

(def ->Dashboard (om/factory Dashboard))
