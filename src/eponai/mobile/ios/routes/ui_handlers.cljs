(ns eponai.mobile.ios.routes.ui-handlers
  (:require [eponai.client.route-helper :refer [map->UiComponentMatch]]
            [eponai.mobile.ios.ui.landing :refer [Login ->Login]]
            [eponai.mobile.ios.ui.transactions :refer [Transactions ->Transactions]]
            [eponai.mobile.ios.ui.tabs :refer [Tabs ->Tabs]]
            [om.next :as om]))

(def login-handler
  (map->UiComponentMatch
    {:component      Login
     :factory        ->Login
     :route-param-fn (fn [x {:keys [verify-uuid]}]
                       {:pre [(or (om/component? x) (om/reconciler? x))]}
                       (om/transact! x `[(login/verify {:verify-uuid ~(uuid verify-uuid)})]))}))

(def route-handler->ui-component
  {:route/login login-handler
   :route/transactions (map->UiComponentMatch {:component Tabs
                                               :factory ->Tabs})})
