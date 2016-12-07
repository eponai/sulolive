(ns eponai.web.figwheel
  (:require
    [eponai.client.run :as run]
    [taoensso.timbre :refer-macros [warn debug]]))

(defn reload! []
  (let [url js/location.pathname]
    (debug "Url is:" url)
    (cond
      (clojure.string/starts-with? url "/store")
      (do
        (run/navbar)
        (run/store))

      :else
      (warn "Url did not match an app route. Figwheel will not call (app/run)"))))
