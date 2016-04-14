(ns eponai.web.routes-test
  (:require [cljs.test :refer-macros [deftest is are]]
            [eponai.web.routes :as routes]
            [eponai.client.route-helper :as route-helper]))

;; function which creates a version-1 route.
(def version-1-route (route-helper/create-inside-route-fn (str routes/app-root "/" routes/version)))

(deftest web-routes-version-1
  (are [handler route params]
    (= (routes/key->route handler params)
       (version-1-route route))
    :route/project "/project/123" {:route-param/project-id "123"}
    :route/project-empty "/project" {}
    :route/project-empty "/project/" {}
    :route/project->dashboard "/project/123/dashboard/" {:route-param/project-id "123"}
    :route/project->widget+type+id "/project/123/widget/track/987" 
    {:route-param/project-id "123" :route-param/widget-id "987" :route-param/widget-type "track"}

    :route/project->widget+type+id "/project/123/widget/track/new"
    {:route-param/project-id "123" :route-param/widget-id "new" :route-param/widget-type "track"}

    ;:route/project->dashboard->widget+mode "/project/123/widget/"
    ;{:route-param/project-id  "123" :route-param/widget-id   "new"}

    :route/project->txs"/project/123/transactions" {:route-param/project-id "123"}
    :route/project->txs->tx "/project/123/transactions/transaction/42"
    {:route-param/project-id     "123" :route-param/transaction-id "42"}

    :route/project->txs->tx+mode "/project/123/transactions/transaction/42/edit"
    {:route-param/project-id       "123"
     :route-param/transaction-id   "42"
     :route-param/transaction-mode "edit"}

    :route/profile "/profile" {}
    :route/profile->txs "/profile/transactions" {}

    :route/settings "/settings" {}
    :route/subscribe "/subscribe" {}))

(deftest api-routes
  (is (= (routes/key->route :route/api->logout)
         "/api/logout")))