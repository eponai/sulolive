 (ns eponai.client.figwheel
  (:require [bidi.bidi :as bidi]
            [eponai.client.routes :as routes]
            [eponai.client.app :as app]
            [eponai.client.tests :as tests]
            [taoensso.timbre :refer-macros [warn debug]]))


(defn ^:export run []
  (tests/run)
  (let [url js/location.pathname]
    (debug "Url is:" url)
    (if (bidi/match-route routes/routes js/location.pathname)
     (do
       (debug "Matched an app route. Calling (app/run)")
       (app/run))
     (warn "Url did not match an app route. Figwheel will not call (app/run)"))))
