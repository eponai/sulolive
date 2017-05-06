(ns eponai.common.ui.user.profile
  (:require
    #?(:cljs
       [eponai.client.ui.photo-uploader :as pu])
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.icons :as icons]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.elements.menu :as menu]
    [taoensso.timbre :refer [debug]]
    [eponai.client.routes :as routes]))

(defui Profile
  Object
  (initLocalState [_]
    {:tab          :favorites
     :file-upload? false
     ;;TODO: Resolve s3 paths to cloudfront paths
     ;;"https://s3.amazonaws.com/sulo-images/site/collection-women.jpg"
     :photo-url    "https://d30slnyi7gxcwc.cloudfront.net/site/women-new.jpg"})
  (componentDidMount [this]
    (om/update-state! this assoc :did-mount? true))
  (render [this]
    (let [{:keys [proxy/photo-upload]} (om/props this)
          {:keys [tab file-upload? photo-url did-mount?]} (om/get-state this)
          {:keys [is-current-user? user]} (om/get-computed this)
          photo-url (or (get-in user [:user/profile :user.profile/photo :photo/path]) photo-url)]
      (dom/div
        {:id "sulo-profile"}

        (dom/div
          (css/add-class :header)

          (dom/div
            (css/add-class :user-info)
            (grid/row
              nil
              (grid/column
                (css/text-align :center)
                (dom/h1 nil (get-in user [:user/profile :user.profile/name]))))

            (grid/row-column
              (cond->> (css/add-class :profile-photo)
                       is-current-user?
                       (css/add-class :edit-enabled))
              (photo/user-photo {:user user}))

            (grid/row-column
              (css/text-align :center)
              (if is-current-user?
                (dom/a
                  (css/button-hollow {:href (routes/url :user/profile {:user-id (:db/id user)})})
                  (dom/span nil "Edit Profile"))
                (common/follow-button nil)))))

        ;(grid/row-column
        ;  nil)
        (dom/ul
          (css/add-class :tabs (css/align :center))
          (menu/item-tab
            {:is-active? (= tab :favorites)}
            (dom/a {:onClick #(om/update-state! this assoc :tab :products)}
                   (dom/strong (css/add-class :stat) "0")
                   (dom/span nil " favorites")))
          (menu/item-tab
            {:is-active? (= tab :followers)}
            (dom/a {:onClick #(om/update-state! this assoc :tab :followers)}
                   (dom/strong (css/add-class :stat) "0")
                   (dom/span nil " followers")))
          (menu/item-tab
            {:is-active? (= tab :following)}
            (dom/a {:onClick #(om/update-state! this assoc :tab :following)}
                   (dom/strong (css/add-class :stat) "0")
                   (dom/span nil " following")))
          ;(menu/item-tab
          ;  {:is-active? (= tab :about)}
          ;  (dom/a {:onClick #(om/update-state! this assoc :tab :about)}
          ;         (dom/span nil "About")))
          )
        (grid/row-column
          nil
          (dom/div
            (css/add-class :tabs-content)
            (dom/div
              (cond->> (css/add-class :tabs-panel)
                       (= tab :following)
                       (css/add-class :is-active))
              (dom/div
                (css/text-align :center)
                (dom/p nil "Your're not following anyone yet"))))

          (dom/div
            (css/add-class :tabs-content)
            (dom/div
              (cond->> (css/add-class :tabs-panel)
                       (= tab :followers)
                       (css/add-class :is-active))
              (dom/div
                (css/text-align :center)
                (dom/p nil "Your don't have any followers yet :("))))

          (dom/div
            (css/add-class :tabs-content)
            (dom/div
              (cond->> (css/add-class :tabs-panel)
                       (= tab :favorites)
                       (css/add-class :is-active))
              (dom/div
                (css/text-align :center)
                (dom/p nil "No favorites")))))))))

(def ->Profile (om/factory Profile))
