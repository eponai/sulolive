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
  (let [path (url->path (or url url-prefix))]
    (when (s/starts-with? path "/login/verify/")
      (let [verification-uuid (last (s/split path "/"))]
        (debug "Verify email UUID: " verification-uuid)
        (om/transact! x `[(email/verify ~{:verify-uuid verification-uuid})
                          :user/current
                          :query/auth])))))

(defn start!
  "Gets the initial url and loads the root matching the url's path."
  [reconciler]
  (.. linking getInitialURL
      (then ( fn [url] (load-url! reconciler url)))
      (catch (fn [err]
               (error "Error occurred when getting initial URL: " err)))))
