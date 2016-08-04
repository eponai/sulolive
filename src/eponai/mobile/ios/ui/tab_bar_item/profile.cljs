(ns eponai.mobile.ios.ui.tab-bar-item.profile
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.mobile.components :refer [view text touchable-highlight]]
    [om.next :as om :refer-macros [defui]]))

(defui Profile
  Object
  (render [this]
    (let [on-logout (.. (om/props this) -computed -onLogout)]
      (view (opts {:style {:flex 1 :margin 20}})
            (text nil "Is logged in")

            (touchable-highlight
              (opts {:style   {:background-color "#4267B2" :padding 10 :border-radius 5 :height 44 :justify-content "center" :margin-vertical 5}
                     :onPress on-logout})

              (text (opts {:style {:color "white" :text-align "center" :font-weight "bold"}})
                    "Sign Out"))))))

(def ->Profile (om/factory Profile))
