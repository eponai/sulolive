(ns eponai.mobile.ios.routes.ui-handlers
  (:require [eponai.client.route-helper :refer [map->UiComponentMatch]]
            [eponai.mobile.ios.ui.signup :refer [LoginNavScene ->LoginNavScene]]
            [medley.core :as medley]
            [om.next :as om]))

;(def login-handler
;  (map->UiComponentMatch
;    {:component      LoginNavScene
;     :factory        ->LoginNavScene
;     :route-key      :route/login
;     :route-param-fn (fn [{:keys [verify-uuid]}]
;                       (when (some? verify-uuid)
;                         `[(login/verify {:verify-uuid ~(uuid verify-uuid)})]))}))
;
;(def tabs-handler (map->UiComponentMatch {:component Tabs
;                                          :route-key :route/transactions
;                                          :factory ->Tabs}))
;
;(def route-handler->ui-component
;  {:route/login login-handler
;   :route/transactions  tabs-handler})
;
;(def root-handlers [login-handler tabs-handler])
;
;(def route-key->root-handler (->> (group-by :route-key root-handlers)
;                                  (medley/map-vals first)))
