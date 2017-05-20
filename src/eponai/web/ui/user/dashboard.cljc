(ns eponai.web.ui.user.dashboard
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.router :as router]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.web.ui.photo :as photo]))

(defui UserDashboard
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/auth [:user/email
                   {:user/profile [:user.profile/name
                                   {:user.profile/photo [:photo/id]}]}
                   :user/stripe]}
     :query/current-route])
  Object
  (render [this]
    (let [{:proxy/keys [navbar]
           :query/keys [auth]} (om/props this)
          {user-profile :user/profile} auth]
      (debug "Auth: " auth)
      (common/page-container
        {:navbar navbar :id "sulo-user-dashboard"}
        (grid/row-column
          nil
          (dom/h1 nil "Settings")
          (dom/div
            (css/add-class :section-title)
            (dom/span nil "Account"))
          (menu/vertical
            (css/add-class :section-list)
            (menu/item
              nil
              (grid/row
                (css/align :middle)
                (grid/column
                  nil
                  (dom/label nil "Email")
                  (dom/span nil (:user/email auth)))
                (grid/column
                  (grid/column-size {:small 12 :medium 6})
                  (dom/a
                    (->> (css/button-hollow)
                         (css/add-class :secondary)
                         (css/add-class :small))
                    (dom/span nil "Edit email")))))
            (menu/item
              nil
              (grid/row
                (css/align :middle)
                (grid/column
                  nil
                  (dom/label nil "Public profile")
                  (dom/p nil (dom/small nil "This is how other users on SULO will see you when you interact in common spaces (such as store chat rooms)."))
                  )
                (grid/column
                  (grid/column-size {:small 12 :medium 6})
                  (dom/div
                    (css/add-class :user-profile)
                    (dom/span nil (:user.profile/name user-profile))
                    (photo/user-photo auth {:transformation :transformation/thumbnail}))
                  (dom/a
                    (->> (css/button-hollow)
                         (css/add-class :secondary)
                         (css/add-class :small))
                    (dom/span nil "Edit profile")))))
            )
          (dom/div
            (css/add-class :section-title)
            (dom/span nil "Shopping details"))
          (menu/vertical
            (css/add-class :section-list)

            (menu/item
              nil
              (grid/row
                (css/align :middle)
                (grid/column
                  nil
                  (dom/label nil "Payment")
                  (dom/p nil (dom/small nil "Save and manage your credit cards for easier checkout.")))
                (grid/column
                  (grid/column-size {:small 12 :medium 6})
                  (dom/a
                    (->> (css/button-hollow)
                         (css/add-class :secondary)
                         (css/add-class :small))
                    (dom/span nil "Manage payment info")))))
            (menu/item
              nil
              (grid/row
                (css/align :middle)
                (grid/column
                  nil
                  (dom/label nil "Shipping")
                  (dom/p nil (dom/small nil "Your saved shipping addresses for easier checkout.")))
                (grid/column
                  (grid/column-size {:small 12 :medium 6})
                  (dom/a
                    (->> (css/button-hollow)
                         (css/add-class :secondary)
                         (css/add-class :small))
                    (dom/span nil "Manage shipping info"))))))

          (dom/div
            (css/add-class :section-title)
            (dom/span nil "Connections"))
          (menu/vertical
            (css/add-class :section-list)
            (menu/item
              nil
              (grid/row
                (css/align :middle)
                (grid/column
                  nil
                  (dom/label nil "Facebook")
                  (dom/p nil (dom/small nil "Connect to Facebook to login with your account. We will never post to Facebook or message your friends without your permission")))
                (grid/column
                  (grid/column-size {:small 12 :medium 6})
                  (dom/a
                    (->> (css/button)
                         (css/add-class :facebook)
                         (css/add-class :small))
                    (dom/i {:classes ["fa fa-facebook fa-fw"]})
                    (dom/span nil "Connect to Facebook")))))

            (menu/item
              nil
              (grid/row
                (css/align :middle)
                (grid/column
                  nil
                  (dom/label nil "Twitter")
                  (dom/p nil (dom/small nil "Connect to Twitter to login with your account. We will never post to Twitter or message your followers without your permission.")))
                (grid/column
                  (grid/column-size {:small 12 :medium 6})
                  (dom/a
                    (->> (css/button)
                         (css/add-class :twitter)
                         (css/add-class :small))
                    (dom/i {:classes ["fa fa-twitter fa-fw"]})
                    (dom/span nil "Connect to Twitter")))))))))))

;(def ->UserSettings (om/factory UserSettings))

(router/register-component :user-dashboard UserDashboard)