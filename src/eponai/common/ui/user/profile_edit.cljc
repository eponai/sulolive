(ns eponai.common.ui.user.profile-edit
  (:require
    #?(:cljs
       [eponai.client.ui.photo-uploader :as pu])
    #?(:cljs
       [eponai.web.utils :as utils])
    [eponai.common.ui.dom :as my-dom]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.photo :as photo]
    [taoensso.timbre :refer [debug]]
    [eponai.client.parser.message :as msg]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.callout :as callout]))

(defui ProfileEdit
  static om/IQuery
  (query [_]
    [#?(:cljs
        {:proxy/photo-upload (om/get-query pu/PhotoUploader)})
     :query/messages])
  Object
  (componentDidUpdate [this _ _]
    (let [last-message (msg/last-message this 'photo/upload)]
      (when (msg/final? last-message)
        (.close-photo-menu this)
        (msg/clear-messages! this 'photo/upload)
        (om/update-state! this dissoc :queue-photo))))

  (open-photo-menu [this]
    (let [{:keys [on-close-photo-menu]} (om/get-state this)]
      (om/update-state! this assoc :photo-menu-open? true)
      #?(:cljs
         (.addEventListener js/document "click" on-close-photo-menu))))

  (close-photo-menu [this]
    (let [{:keys [on-close-photo-menu]} (om/get-state this)]
      (om/update-state! this assoc :photo-menu-open? false)
      #?(:cljs
         (.removeEventListener js/document "click" on-close-photo-menu))))

  (initLocalState [this]
    {:on-close-photo-menu #(.close-photo-menu this)})

  (render [this]
    (let [{:proxy/keys [photo-upload]} (om/props this)
          {:keys [user]} (om/get-computed this)
          {:keys [queue-photo photo-menu-open?]} (om/get-state this)]
      (my-dom/div {:id "sulo-profile-edit"}

        (grid/row-column
          nil
          (dom/h1 nil "Edit Profile"))
        (grid/row
          (css/add-class :collapse)
          (grid/column
            nil
            (callout/callout
              nil
              ;(dom/h4 #js {:className "header"} "Public Information")
              (grid/row
                (css/align :center)
                (grid/column
                  (grid/column-size {:small 6 :medium 4 :large 3})
                  (photo/user-photo
                    (cond-> {:id "user-profile-photo"
                             :user user
                             :onClick #(.open-photo-menu this)}
                            (some? queue-photo)
                            (assoc-in [:user :store/profile :store.profile/photo :photo/path] (:location queue-photo)))
                    (photo/overlay
                      (when (or (some? queue-photo) photo-menu-open?)
                        (css/add-class :is-active))
                      (if (some? queue-photo)
                        (my-dom/i {:classes ["fa fa-spinner fa-spin"]})
                        [
                         #?(:cljs
                            [
                             (pu/->PhotoUploader
                               (om/computed
                                 photo-upload
                                 {:hide-label?     true
                                  :on-photo-queue  (fn [img-result]
                                                     ;(debug "Got photo: " photo)
                                                     (om/update-state! this assoc :queue-photo {:location  img-result
                                                                                                :in-queue? true}))
                                  :on-photo-upload (fn [photo]
                                                     (debug "Received upload photo: " photo)
                                                     (msg/om-transact! this [(list 'photo/upload {:photo photo})
                                                                             :query/user]))}))
                             (my-dom/label
                               (css/button {:htmlFor "file-"})
                               "Upload photo")])
                         ;(my-dom/a
                         ;  (css/button {:onClick #(om/update-state! this assoc :file-upload? true)})
                         ;  (dom/span nil "Upload photo"))
                         (my-dom/a (css/button) (dom/span nil "Remove photo"))]))))
                (grid/column
                  (grid/column-size {:small 12 :medium 8 :large 9})
                  (grid/row
                    nil
                    (grid/column
                      (->> (grid/column-size {:small 12 :medium 3 :large 2})
                           (css/text-align :right))
                      (dom/label nil "Name"))
                    (grid/column
                      nil
                      (my-dom/input  {:type         "text"
                                      :defaultValue (get-in user [:user/profile :user.profile/name])})))

                  ;(my-dom/div
                  ;  (css/grid-row)
                  ;  (my-dom/div
                  ;    (->> (css/grid-column)
                  ;         (css/grid-column-size {:small 3 :medium 3 :large 2})
                  ;         (css/text-align :right))
                  ;    (dom/label nil "Username"))
                  ;  (my-dom/div
                  ;    (css/grid-column)
                  ;    (dom/input #js {:type "text"})))

                  (grid/row
                    nil
                    (grid/column
                      (->> (grid/column-size {:small 12 :medium 3 :large 2})
                           (css/text-align :right))
                      (dom/label nil "Email"))
                    (grid/column
                      nil
                      (my-dom/input {:type         "email"
                                     :disabled     true
                                     :defaultValue (:user/email user)}))))))))))))

(def ->ProfileEdit (om/factory ProfileEdit))