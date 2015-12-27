(ns eponai.server.site
  (:require [cemerick.friend :as friend]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :refer [resource-response]]))

(defroutes
  site-routes
  (GET "/" [] (if (friend/current-authentication)
                (ring.util.response/redirect "/dev/budget.html")
                (ring.util.response/redirect "/b/index.html")))
  (route/not-found "Not found"))