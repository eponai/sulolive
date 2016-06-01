(ns eponai.web.homeless
  "Place where functions land when they have no clear way to go.
  Move these to somewhere else when it makes sense."
  (:require [clojure.string :as s]))

(defn content-type? [headers]
  (s/starts-with? (or (get headers "content-type")
                      (get headers "Content-Type")
                      "")
                  "text/html"))

(defn- popup-window-with-body [body]
  (let [error-window (.open js/window "" "_blank" "status=0,scrollbars=1, location=0")]
    (try
      (.. error-window -document (write body))
      (finally
        (.. error-window -document close)))))

(def om-next-endpoint-public "/api")
(def om-next-endpoint-user-auth "/api/user")
(def om-next-endpoint-playground "/api/playground")
(def email-endpoint-subscribe "/newsletter/subscribe")
