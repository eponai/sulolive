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
    [taoensso.timbre :refer [debug]]
    [eponai.client.parser.message :as msg]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.web.ui.photo :as p]))

(def form-inputs
  {:user.info/name "user.info.name"})
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
        (msg/clear-messages! this 'photo/upload)
        (om/update-state! this dissoc :queue-photo))))

  (save-info [this]
    #?(:cljs
       (let [input-name (utils/input-value-by-id (:user.info/name form-inputs))]
         (when (not-empty input-name)
           (msg/om-transact! this [(list 'user.info/update {:user/name input-name})])))))

  (render [this]
    (let [{:proxy/keys [photo-upload]} (om/props this)
          {:keys [user]} (om/get-computed this)
          {:keys [queue-photo]} (om/get-state this)]
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

                  (if (some? queue-photo)
                    (my-dom/div
                      {:classes ["upload-photo circle loading user-profile-photo"]}
                      (p/circle {:src (:src queue-photo)}
                                (p/overlay nil (my-dom/i {:classes ["fa fa-spinner fa-spin"]}))))
                    (my-dom/label {:htmlFor "file-profile"
                                :classes ["upload-photo circle"]}
                               (p/user-photo user {:transformation :transformation/thumbnail})
                               #?(:cljs
                                  (pu/->PhotoUploader
                                    (om/computed
                                      photo-upload
                                      {:hide-label?     true
                                       :id "profile"
                                       :on-photo-queue  (fn [img-result]
                                                          (om/update-state! this assoc :queue-photo {:src  img-result}))
                                       :on-photo-upload (fn [photo]
                                                          (msg/om-transact! this [(list 'photo/upload {:photo photo})
                                                                                  :query/user]))}))))))
                (grid/column
                  (grid/column-size {:small 12 :medium 8 :large 9})
                  (dom/div
                    nil
                    (grid/row
                      nil
                      (grid/column
                        (->> (grid/column-size {:small 12 :medium 3 :large 2})
                             (css/text-align :right))
                        (dom/label nil "Name"))
                      (grid/column
                        nil
                        (my-dom/input {:id           (:user.info/name form-inputs)
                                       :type         "text"
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
                                       :defaultValue (:user/email user)}))))
                  (my-dom/div
                    (css/text-align :right)
                    (my-dom/a (css/button {:onClick #(.save-info this)}) (my-dom/span nil "Save"))))))))))))

(def ->ProfileEdit (om/factory ProfileEdit))