(ns eponai.client.routes
  (:require [bidi.bidi :as bidi]
            [clojure.string :as s]
            [eponai.client.ui :as ui]
            [eponai.client.ui.dashboard :refer [Dashboard ->Dashboard]]
            [eponai.client.ui.all_transactions :refer [AllTransactions ->AllTransactions]]
            [eponai.client.ui.settings :refer [Settings ->Settings]]
            [eponai.client.ui.stripe :refer [Payment ->Payment]]
            [om.next :as om]))

(defprotocol RouteParamHandler
  (handle-route-params [this params reconciler]))

(defrecord UiComponentMatch [component factory route-param-fn]
  RouteParamHandler
  (handle-route-params [_ params reconciler]
    (route-param-fn params reconciler))
  bidi/Matched
  (resolve-handler [this m]
    (bidi/succeed this m))
  (unresolve-handler [this m] (when (= this (:handler m)) "")))

(def dashboard-handler
  (map->UiComponentMatch {:component      Dashboard
                          :factory        ->Dashboard
                          :route-param-fn (fn [{:keys [budget-uuid]} reconciler]
                                            (om/transact! reconciler `[(dashboard/set-active-budget
                                                                         {:budget-uuid ~(uuid budget-uuid)})]))}))

(def dashboard-routes
  {(bidi/alts "" "/") dashboard-handler
   "/dashboard"       {(bidi/alts "" "/" ["/" :budget-uuid]) dashboard-handler}})

(def routes
  [ui/app-root
   (merge
     dashboard-routes
     {"/transactions" (map->UiComponentMatch {:component AllTransactions
                                              :factory   ->AllTransactions})
      "/settings"     (map->UiComponentMatch {:component Settings
                                              :factory   ->Settings})
      "/subscribe"    (map->UiComponentMatch {:component Payment
                                              :factory   ->Payment})
      "/widget/widget" (map->UiComponentMatch {:component AllTransactions
                                            :factory   ->AllTransactions})})])
