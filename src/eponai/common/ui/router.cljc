(ns eponai.common.ui.router
  (:require
    [om.next :as om :refer [defui]]
    [om.dom :as dom]
    [eponai.common.routes :as routes]))

(def dom-app-id "the-sulo-app")

(defui Router
  static om/IQuery
  (query [this]
    [:query/current-route
     {:routing/app-root (into {}
                              (map (fn [[route {:keys [component]}]]
                                     [route (om/get-query component)]))
                              routes/route->component)}])
  Object
  (render [this]
    (let [{:keys [routing/app-root query/current-route]} (om/props this)
          route (:route current-route :index)
          factory (get-in routes/route->component [route :factory])]
      (factory app-root))))
