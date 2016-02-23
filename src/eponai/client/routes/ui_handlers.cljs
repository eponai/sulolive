(ns eponai.client.routes.ui-handlers
  (:require [bidi.bidi :as bidi]
            [eponai.client.ui.dashboard :refer [Dashboard ->Dashboard]]
            [eponai.client.ui.all_transactions :refer [AllTransactions ->AllTransactions]]
            [eponai.client.ui.settings :refer [Settings ->Settings]]
            [eponai.client.ui.stripe :refer [Payment ->Payment]]
            [eponai.client.ui.profile :refer [Profile ->Profile]]
            [om.next :as om]))

(defprotocol RouteParamHandler
  (handle-route-params [this params reconciler]))

(defrecord UiComponentMatch [component factory route-param-fn]
  RouteParamHandler
  (handle-route-params [_ reconciler params]
    (route-param-fn reconciler params))
  bidi/Matched
  (resolve-handler [this m]
    (bidi/succeed this m))
  (unresolve-handler [this m] (when (= this (:handler m)) "")))

(def dashboard-handler
  (map->UiComponentMatch
    {:component      Dashboard
     :factory        ->Dashboard
     :route-param-fn (fn [reconciler {:keys [budget-uuid]}]
                       {:pre [(om/reconciler? reconciler)]}
                       (om/transact! reconciler
                                     `[(dashboard/set-active-budget
                                         {:budget-uuid ~(uuid budget-uuid)})]))}))

(def route-handler->ui-component
  {:route/dashboard    dashboard-handler
   :route/settings     (map->UiComponentMatch {:component Settings
                                               :factory   ->Settings})
   :route/transactions (map->UiComponentMatch {:component AllTransactions
                                               :factory   ->AllTransactions})
   :route/subscribe    (map->UiComponentMatch {:component Payment
                                               :factory   ->Payment})
   :route/profile      (map->UiComponentMatch {:component Profile
                                               :factory   ->Profile})})
