(ns eponai.client.routes-test
  (:require [bidi.bidi :as bidi]
            [cljs.test :refer-macros [deftest is are]]
            [eponai.client.routes :as routes]))

(deftest dashboard-routes
  (are [route] (= (:handler (bidi/match-route routes/routes (routes/inside route)))
                  :route/dashboard)
               ""
               "/"
               "/dashboard"
               "/dashboard/"
               "/dashboard/foo-bar"))
