 (ns eponai.client.figwheel
  (:require [eponai.client.app :as app]
            [eponai.client.tests :as tests]))


(defn ^:export run []
 (tests/run)
 (app/run))
