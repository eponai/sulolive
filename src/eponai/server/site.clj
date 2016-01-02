(ns eponai.server.site
  (:require [cemerick.friend :as friend]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [eponai.server.middleware :as m]
            [ring.util.response :as r]))

(defn html [path]
  (-> path
      (r/resource-response {:root "public"})
      (r/content-type "text/html")))

(defroutes
  site-routes
  (GET "/" [:as request]
    (if (friend/current-authentication)
      (html (str (::m/cljs-build-id request) "/budget.html"))
      (html "index.html")))
  (GET "/budget" request
       (r/redirect (str (::m/cljs-build-id request) "/budget.html")))

  (GET "/login" _
    (html "login.html"))

  (GET "/devcards" []
    (println "Hej")
    (html "devcards/budget.html"))

  (route/resources "/")
  (route/not-found "Not found"))
