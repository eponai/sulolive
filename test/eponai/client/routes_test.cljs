(ns eponai.client.routes-test
  (:require [bidi.bidi :as bidi]
            [cljs.test :refer-macros [deftest is are]]
            [eponai.client.ui :as ui]
            [eponai.client.routes :as routes]))

(deftest dashboard-routes
  (are [route] (= (:handler (bidi/match-route routes/routes (ui/inside route)))
                  routes/dashboard-handler)
               ""
               "/"
               "/dashboard"
               "/dashboard/"
               "/dashboard/foo-bar"))
