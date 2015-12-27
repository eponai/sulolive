(ns eponai.server.site
  (:require [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.middleware.defaults :as r]
            [ring.util.response :refer [resource-response]]
            [ring.middleware.gzip :as gzip]
            [eponai.server.http :as h]))

(defroutes
  site-routes
  (GET "/" [] (resource-response "/b/index.html"))
  (route/resources "/b")
  (route/not-found "/b/index.html"))