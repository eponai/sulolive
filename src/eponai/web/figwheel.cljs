(ns eponai.web.figwheel
  (:require [bidi.bidi :as bidi]
            [eponai.web.routes :as routes]
            [eponai.web.app :as app]
            [eponai.web.playground :as playground]
            [eponai.web.signup :as signup]
            [eponai.web.ui.utils :as utils]
            [taoensso.timbre :refer-macros [warn debug]]))

(defn reload! []
  (let [url js/location.pathname]
    (debug "Url is:" url)
    (cond
      (bidi/match-route (routes/routes) url)
      (let [run-fn (if utils/*playground?*
                     (with-meta playground/run {:name "playground/run"})
                     (with-meta app/run {:name "app/run"}))]
        (debug "Matched an app route. Calling run-fn: " (:name (meta run-fn)))
        (run-fn))
      (or (= url "/signup") (= url "/activate"))
      (with-meta signup/run {:name "signup/run"})
      :else
      (warn "Url did not match an app route. Figwheel will not call (app/run)"))))
