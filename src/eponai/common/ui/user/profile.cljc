(ns eponai.common.ui.user.profile
  (:require
    #?(:cljs
       [eponai.client.ui.photo-uploader :as pu])
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.elements.grid :as grid]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.elements.menu :as menu]
    [taoensso.timbre :refer [debug]]
    [eponai.client.routes :as routes]))

(defui Profile
  static om/IQuery
  (query [_]
    [#?(:cljs
        {:proxy/photo-upload (om/get-query pu/PhotoUploader)})])
  Object
  (initLocalState [_]
    {:tab          :following
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
          photo-url (or (get-in user [:user/photo :photo/path]) photo-url)]
      (dom/div
        {:id "sulo-profile"}
        (dom/div
          (css/add-class :header)
          (when file-upload?
            (common/modal
              {:on-close #(om/update-state! this assoc :file-upload? false)}
              (dom/div
                nil
                (dom/h2 nil "Change Profile Photo")
                (menu/vertical
                  nil
                  (menu/item
                    nil
                    (when did-mount?
                      #?(:cljs
                         (pu/->PhotoUploader
                           (om/computed
                             photo-upload
                             {:on-photo-upload (fn [photo]
                                                 (om/transact! this [(list 'photo/upload {:photo photo})
                                                                     :query/user])
                                                 (om/update-state! this assoc :file-upload? false))})))))
                  (menu/item
                    nil
                    (dom/a
                      (->> (css/button-hollow)
                           (css/add-class :expanded)) "Remove Photo"))))))
          (dom/div
            (css/add-class :user-info)
            (grid/row
              nil
              (grid/column
                (css/text-align :center)
                (dom/h1 nil (:user/name user))))

            (grid/row
              (css/align :center)

              (grid/column
                (cond->> (->> (grid/column-size {:small 4 :medium 3 :large 2})
                              (css/add-class :profile-photo))
                         is-current-user?
                         (css/add-class :edit-enabled))

                (dom/a {:onClick #(when is-current-user? (om/update-state! this assoc :file-upload? true))}
                       (photo/circle {:src photo-url}))))

            (grid/row
              (css/align :center)
              (grid/column
                (->> (grid/column-size {:small 4 :medium 3 :large 2})
                     (css/text-align :center))
                (if is-current-user?
                  (dom/a
                    (css/button-hollow {:href (routes/url :user/profile {:user-id (:db/id user)})})
                    (dom/span nil "Edit Profile"))
                  (dom/a
                    (css/button)
                    (dom/span nil "+ Follow")))))

            (grid/row-column
              nil
              (menu/horizontal
                (css/align :center)
                (menu/item-tab {:active?  (= tab :following)
                                :on-click #(om/update-state! this assoc :tab :following)} "Following")
                (menu/item-tab {:active?  (= tab :followers)
                                :on-click #(om/update-state! this assoc :tab :followers)} "Followers")
                (menu/item-tab {:active?  (= tab :products)
                                :on-click #(om/update-state! this assoc :tab :products)} "Products")
                (menu/item-tab {:active?  (= tab :about)
                                :on-click #(om/update-state! this assoc :tab :about)} "About")))))
        (grid/row-column
          (css/text-align :center)
          (condp = tab
            :following (dom/div (css/callout) (dom/span nil "NOt following anyone :("))
            :followers (dom/div (css/callout) (dom/span nil "NO follwers yet"))
            :products (dom/div (css/callout) (dom/span nil "No products"))
            :about (dom/div (css/callout) (dom/span nil "Abount section")))))
      )))

(def ->Profile (om/factory Profile))
