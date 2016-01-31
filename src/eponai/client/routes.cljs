(ns eponai.client.routes
  (:require [bidi.bidi :as bidi]
            [clojure.string :as s]
            [eponai.client.ui.dashboard :refer [Dashboard ->Dashboard]]
            [eponai.client.ui.all_transactions :refer [AllTransactions ->AllTransactions]]
            [eponai.client.ui.settings :refer [Settings ->Settings]]
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

(def app-root "/app")

(defn- create-route [root paths]
  (letfn [(trim-separators [s]
            (let [s (str s)]
              (cond-> s
                      (s/starts-with? s "/") (->> rest (apply str))
                      (s/ends-with? s "/") (->> butlast (apply str)))))]
    (s/join "/" (cons root (map trim-separators paths)))))

(defn outside
  "Takes any number of paths and creates a path outside our app."
  [& paths]
  (create-route "" paths))

(defn inside
  "Takes any number of paths and creates a path inside our app."
  [& paths]
  (create-route app-root paths))

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
  [app-root
   (merge
     dashboard-routes
     {"/transactions" (map->UiComponentMatch {:component AllTransactions
                                              :factory   ->AllTransactions})
      "/settings"     (map->UiComponentMatch {:component Settings
                                              :factory   ->Settings})})])
