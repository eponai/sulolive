(ns eponai.web.figwheel
  (:require
    [eponai.client.run :as run]
    [eponai.common.routes :as routes]
    [bidi.bidi :as bidi]
    [taoensso.timbre :refer-macros [warn debug]]))

(defn reload! []
  (let [url js/location.pathname]
    (if-let [matched (bidi/match-route routes/routes url)]
      (do (debug "Matched url to route: " matched " running the app.")
          (run/on-reload!))
      (debug "Figwheel did not match the url: " url
             " to any route. Will not run anything."))))
