(ns eponai.mobile.ios.routes
  (:require [eponai.client.route-helper :as route-helper]
            [bidi.bidi :as bidi]))

(def app-root "/ios")
(def inside (route-helper/create-inside-route-fn app-root))
(def outside (route-helper/create-outside-route-fn))

(def routes [app-root
             {"/1"
              {"/login" {(bidi/alts "" "/" ["/verify/" :verify-uuid]) :route/login}}}])

(def default-route :route/login)
