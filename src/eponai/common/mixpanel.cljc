(ns eponai.common.mixpanel
  (:require
    [taoensso.timbre :refer [warn]]))


(defn track [event & [properties]]
  #?(:cljs (.track js/mixpanel event (clj->js properties))))

(def events
  {
   ;; Store menu
   ::go-to-dashboard       "Store: Go to dashboard"
   ::go-to-store-info      "Store: Go to edit store info"
   ::go-to-stream-settings "Store: Go to stream settings"
   ::go-to-products        "Store: Go to products"
   ::go-to-orders          "Store: Go to orders"
   ::go-to-business        "Store: Go to business settings"

   ;; User menu
   ::go-to-manage-store    "Go to manage store"
   ::go-to-purchases       "Go to user purchases"
   ::go-to-settings        "Go to user settings"

   ::signout               "Sign out user"
   ::open-signin           "Open sign in user"
   ::go-to-start-store     "Go to start store"})

(defn track-key [k & [properties]]
  (if-let [event (get events k)]
    (track event properties)
    (warn "Mixpanel - Received unrecognized event key:  " k)))
