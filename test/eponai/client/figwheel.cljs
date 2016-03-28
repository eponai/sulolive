 (ns eponai.client.figwheel
  (:require [bidi.bidi :as bidi]
            [eponai.web.routes :as routes]
            [eponai.web.app :as app]
            [taoensso.timbre :refer-macros [warn debug]]))


(defn ^:export run []
  (let [url js/location.pathname]
    (debug "Url is:" url)
    (if (bidi/match-route routes/routes js/location.pathname)
     (do
       (debug "Matched an app route. Calling (app/run)")
       (app/run))
     (warn "Url did not match an app route. Figwheel will not call (app/run)"))))
