(ns eponai.common.ui.user.profile-edit
  (:require
    #?(:cljs
       [eponai.client.ui.photo-uploader :as pu])
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.navbar :as nav]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.elements.menu :as menu]
    [taoensso.timbre :refer [debug]]))

(defui ProfileEdit
  Object
  (render [this]
    (let [{:keys [user]} (om/get-computed this)]
      (dom/div #js {:id "sulo-profile-edit"}
        (common/wip-label this)
        (my-dom/div
          (css/grid-row)
          (my-dom/div
            (css/grid-column)

            (dom/h1 nil "Edit Profile")
            (my-dom/div
              (css/add-class ::css/callout)
              ;(dom/h4 #js {:className "header"} "Public Information")
              (my-dom/div
                (->> (css/grid-row)
                     (css/align :middle)
                     (css/align :center))
                (my-dom/div
                  (->> (css/grid-column)
                       (css/grid-column-size {:small 6 :medium 4 :large 3}))
                  (photo/user-photo user))
                (my-dom/div
                  (->> (css/grid-column)
                       (css/grid-column-size {:small 12 :medium 8 :large 9}))
                  (my-dom/div
                    (css/grid-row)
                    (my-dom/div
                      (->> (css/grid-column)
                           (css/grid-column-size {:small 3 :medium 3 :large 2})
                           (css/text-align :right))
                      (dom/label nil "Name"))
                    (my-dom/div
                      (css/grid-column)
                      (dom/input #js {:type         "text"
                                      :defaultValue (get-in user [:user/profile :user.profile/name])})))

                  (my-dom/div
                    (css/grid-row)
                    (my-dom/div
                      (->> (css/grid-column)
                           (css/grid-column-size {:small 3 :medium 3 :large 2})
                           (css/text-align :right))
                      (dom/label nil "Username"))
                    (my-dom/div
                      (css/grid-column)
                      (dom/input #js {:type "text"})))

                  (my-dom/div
                    (css/grid-row)
                    (my-dom/div
                      (->> (css/grid-column)
                           (css/grid-column-size {:small 3 :medium 3 :large 2})
                           (css/text-align :right))
                      (dom/label nil "Email"))
                    (my-dom/div
                      (css/grid-column)
                      (dom/input #js {:type         "email"
                                      :defaultValue (:user/email user)}))))))))))))

(def ->ProfileEdit (om/factory ProfileEdit))