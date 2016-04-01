(ns eponai.web.routes
  (:require [eponai.client.route-helper :as route-helper]
            [bidi.bidi :as bidi]))

(def app-root "/app")
(def inside (route-helper/create-inside-route-fn app-root))
(def outside (route-helper/create-outside-route-fn))

(def dashboard-routes
  {(bidi/alts "" "/") :route/dashboard
   "/dashboard"       {(bidi/alts "" "/" ["/" :project-uuid]) :route/dashboard}})

(def routes [app-root (merge
                        dashboard-routes
                        {"/transactions" :route/transactions
                         "/settings"     :route/settings
                         "/subscribe"    :route/subscribe
                         "/profile"      :route/profile})])
