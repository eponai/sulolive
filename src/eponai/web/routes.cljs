(ns eponai.web.routes
  (:require [eponai.client.route-helper :refer [end]]
            [bidi.bidi :as bidi]))

(def app-root "/app")
(def version 1)

(def widget-routes
  {["/" :route-param/widget-type] {["/" :route-param/widget-id] {end :route/project->widget+type+id}}})

(def goal-routes
  {["/" :route-param/goal-id] {end :route/project->goal+id}})

(def dashboard-routes
  {end                               :route/project->dashboard})

(def transaction-routes
  {end :route/project->txs})

(def project-routes
  {"/project" {end                           :route/project-empty
               ["/" :route-param/project-id] {end             :route/project
                                              "/dashboard"    dashboard-routes
                                              "/transactions" transaction-routes
                                              "/widget"       widget-routes}}})

(def profile-routes
  {"/profile" {end           :route/profile
               "/transactions" {end :route/profile->txs}}})

(def version-1-routes (merge
                        project-routes
                        profile-routes
                        {"/settings"  :route/settings
                         "/subscribe" :route/subscribe}))

(def app-routes {app-root {end  :route/home
                           "/1" version-1-routes}})

(def api-routes {"/api/logout" :route/api->logout})

(def routes ["" (merge app-routes
                       api-routes)])

(defn key->route
  "Takes a route handler (keyword) and route-params if they are needed,
   and returns a route as a string. Returns nil when there's no match."
  ([route-handler] (key->route route-handler {}))
  ([route-handler route-params]
   {:pre [(keyword? route-handler)
          (and (map? route-params) (every? #(some? (val %)) route-params))]}
   (apply bidi/path-for routes route-handler (apply concat route-params))))

