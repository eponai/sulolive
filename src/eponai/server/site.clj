(ns eponai.server.site
  (:require [cemerick.friend :as friend]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :as r]))

(defn html [path]
  (-> path
      (r/resource-response {:root "public"})
      (r/content-type "text/html")))

(defroutes
  site-routes
  (GET "/" []
    (if (friend/current-authentication)
      (html "dev/budget.html")
      (html "index.html")))

  (GET "/:path" [path]
    (if (friend/current-authentication)
      (html "dev/budget.html")
      (html (str path ".html"))))

  (route/resources "/")
  (route/not-found "Not found"))