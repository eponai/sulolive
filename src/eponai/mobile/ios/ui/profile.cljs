(ns eponai.mobile.ios.ui.profile
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.mobile.components :refer [view text touchable-highlight]]
    [eponai.mobile.components.button :as button]
    [om.next :as om :refer-macros [defui]]
    [taoensso.timbre :refer-macros [debug]]))

(defui Profile
  Object
  (render [this]
    (let [props (om/props this)
          on-logout (.. props -computed -onLogout)
          user (.. props -computed -user)]
      (debug "Profile user: " user)
      (view (opts {:style {:flex 1}})
            (text nil (str (.-email user) " is logged in"))


            (button/primary
              {:title "Sign Out"
               :on-press on-logout})))))

(def ->Profile (om/factory Profile))
