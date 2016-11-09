(ns eponai.web.routes
  (:require [eponai.client.route-helper :refer [end]]
            [bidi.bidi :as bidi]))

(defonce app-root (atom "/app"))
(def version 1)

(def widget-routes
  {["/" :route-param/widget-type] {["/" :route-param/widget-id] {end :route/project->widget+type+id}}})

(def goal-routes
  {["/" :route-param/goal-id] {end :route/project->goal+id}})

(def dashboard-routes
  {end :route/project->dashboard})

(def transaction-routes
  {end :route/project->txs})

(def settings-routes
  {end :route/project->settings})

(def project-routes
  {"/project" {end                           :route/project-empty
               ["/" :route-param/project-id] {end             :route/project
                                              "/dashboard"    dashboard-routes
                                              "/transactions" transaction-routes
                                              "/settings"     settings-routes
                                              "/widget"       widget-routes}}})

(def profile-routes
  {"/profile" {end             :route/profile
               "/transactions" {end :route/profile->txs}}})

(def version-1-routes (merge
                        project-routes
                        profile-routes
                        {"/settings"  :route/settings
                         "/subscribe" :route/subscribe}))

(defn app-routes [app-root]
  {app-root {end  :route/home
             "/1" version-1-routes}})

(defn routes
  ([] (routes @app-root))
  ([app-root] ["" (app-routes app-root)]))

(defn key->route
  "Takes a route handler (keyword) and route-params if they are needed,
   and returns a route as a string. Returns nil when there's no match."
  ([route-handler] (key->route route-handler {}))
  ([route-handler route-params]
   {:pre [(keyword? route-handler)
          (and (map? route-params) (every? #(some? (val %)) route-params))]}
   (apply bidi/path-for (routes) route-handler (apply concat route-params))))

