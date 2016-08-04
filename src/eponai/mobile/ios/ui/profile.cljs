(ns eponai.mobile.ios.ui.profile
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.mobile.components :refer [view text touchable-highlight]]
    [om.next :as om :refer-macros [defui]]
    [taoensso.timbre :refer-macros [debug]]))

(defui Profile
  Object
  (render [this]
    (let [props (om/props this)
          on-logout (.. props -computed -onLogout)
          user (.. props -computed -user)]
      (debug "Profile user: " user)
      (view (opts {:style {:flex 1 :margin 20}})
            (text nil (str (.-email user) " is logged in"))

            (touchable-highlight
              (opts {:style   {:background-color "#4267B2" :padding 10 :border-radius 5 :height 44 :justify-content "center" :margin-vertical 5}
                     :onPress on-logout})

              (text (opts {:style {:color "white" :text-align "center" :font-weight "bold"}})
                    "Sign Out"))))))

(def ->Profile (om/factory Profile))
