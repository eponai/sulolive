(ns eponai.mobile.ios.app
  (:require [eponai.client.utils :as utils]
            [om.next :as om :refer-macros [defui ui]]
            [re-natal.support :as sup]
            [taoensso.timbre :refer-macros [info debug error trace warn]]))

(def app-registry (.-AppRegistry js/ReactNative))
(def logo-img (js/require "./images/cljs.png"))

(def root-node-id 1)
(defonce RootNode (sup/root-node! root-node-id))
(defonce app-root (om/factory RootNode))

(defn run [config]
  (info "Run called in: " (namespace ::foo))
  (debug "Disabled yellow box warnings"))

;; For figwheel. See env/dev/ios/main.cljs
(defn reload! []
  (debug "Reload!")
  (comment
    "Uncomment when we've got an ios app."
    (om/add-root! @utils/reconciler-atom root/RootView root-node-id)))
