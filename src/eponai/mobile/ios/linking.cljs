(ns eponai.mobile.ios.linking
  (:require [bidi.bidi :as bidi]
            [clojure.string :as s]
            [eponai.client.route-helper :as route-helper]
            [eponai.mobile.ios.routes.ui-handlers :as ui-handlers]
            [eponai.mobile.ios.routes :as routes]
            [om.next :as om]
            [taoensso.timbre :refer-macros [error debug]]))

(def linking (js/require "Linking"))
(def url-prefix "jourmoney://")

(defn match-route [url]
  (->> url (bidi/match-route routes/routes)))

(defn url->path [url]
  (when-not (s/starts-with? url url-prefix)
    (throw (ex-info (str "Url needs to start with: " url-prefix " was: " url)
                    {:url url :expected-prefix url-prefix})))
  (s/replace-first url url-prefix "/"))

(defn load-url!
  "Transacts the route matching the url into the app state."
  [x url]
  {:pre [(or (om/reconciler? x) (om/component? x))
         (or (nil? url) (string? url))]}
  (debug "Loading url: " url)
  (let [path (url->path (or url url-prefix))
        _ (debug "path: " path)
        {:keys [handler route-params] :or {handler routes/default-route}} (match-route path)
        ui-handler (get ui-handlers/route-handler->ui-component handler)
        param-mutations (route-helper/route-params-mutations ui-handler route-params)]
    (debug "Setting route: " handler)
    (om/transact! x (vec (cons `(app/set-route ~{:route handler})
                               param-mutations)))))

(defn start!
  "Gets the initial url and loads the root matching the url's path."
  [reconciler]
  (.. linking getInitialURL
      (then ( fn [url] (load-url! reconciler url)))
      (catch (fn [err]
               (error "Error occurred when getting initial URL: " err)))))
