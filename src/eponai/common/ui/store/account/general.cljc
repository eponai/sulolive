(ns eponai.common.ui.store.account.general
  (:require
    [eponai.common.format :as f]
    #?(:cljs
       [eponai.client.ui.photo-uploader :as pu])
    #?(:cljs
       [eponai.web.utils :as utils])
    [eponai.common.ui.store.account.validate :as v]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.om-quill :as quill]
    [om.next :as om :refer [defui]]
    [eponai.client.parser.message :as msg]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.web.ui.photo :as p]
    [taoensso.timbre :refer [debug]]))

(defn label-column [opts & content]
  (grid/column
    (grid/column-size {:small 12 :large 2})
    content))

(defn photo-uploader [component id]
  (let [{:keys [did-mount?]} (om/get-state component)
        {:proxy/keys [photo-upload]} (om/props component)]

    #?(:cljs
       (pu/->PhotoUploader (om/computed
                             photo-upload
                             {:on-photo-queue  (fn [img-result]
                                                 (om/update-state! component assoc (keyword id "queue") {:src img-result}))
                              :on-photo-upload (fn [photo]
                                                 (om/update-state! component (fn [st]
                                                                               (-> st
                                                                                   (dissoc (keyword id "queue"))
                                                                                   (assoc (keyword id "upload") photo)))))
                              :id              id
                              :hide-label?     true})))))

(defui General
  static om/IQuery
  (query [_]
    [#?(:cljs
        {:proxy/photo-upload (om/get-query pu/PhotoUploader)})
     :query/messages])
  Object
  (save-store [this]
    #?(:cljs (let [{uploaded-photo :profile/upload
                    uploaded-cover :cover/upload
                    :quill/keys [about return-policy]} (om/get-state this)
                   {:keys [store]} (om/get-computed this)

                   store-name (utils/input-value-by-id (:field.general/store-name v/form-inputs))
                   store-tagline (utils/input-value-by-id (:field.general/store-tagline v/form-inputs))
                   store-description (quill/get-HTML about)
                   store-return-policy (quill/get-HTML return-policy)]
               (msg/om-transact! this (cond-> [(list 'store/update-info {:db/id         (:db/id store)
                                                                         :store/profile {:store.profile/name          store-name
                                                                                         :store.profile/tagline       store-tagline
                                                                                         :store.profile/description   store-description
                                                                                         :store.profile/return-policy store-return-policy}})]
                                              (some? uploaded-photo)
                                              (conj (list 'store.photo/upload {:photo uploaded-photo
                                                                               :photo-key :store.profile/photo
                                                                               :store-id (:db/id store)}))

                                              (some? uploaded-cover)
                                              (conj (list 'store.photo/upload {:photo uploaded-cover
                                                                               :photo-key :store.profile/cover
                                                                               :store-id (:db/id store)}))

                                              :always
                                              (conj :query/store))))))
  (render [this]
    (let [{:keys [modal uploaded-photo] :as state} (om/get-state this)
          {:proxy/keys [photo-upload]} (om/props this)
          {:keys [store]} (om/get-computed this)
          {{:store.profile/keys [cover tagline description]
            store-name :store.profile/name} :store/profile} store
          update-msg (msg/last-message this 'store/update-info)
          photo-msg (msg/last-message this 'store.photo/upload)]
      (debug "Uploaded photo: " (:profile/upload state))
      (dom/div
        nil
        (callout/callout-small
          nil
          (grid/row
            nil
            (grid/column
              (grid/column-size {:small 12 :medium 6})
              ;(callout/header nil "Profile photo")
              (dom/label (css/text-align :left) "Profile photo")
              (let [{:profile/keys [upload queue]} state]

                (if (some? queue)
                  (dom/div
                    {:classes ["upload-photo circle loading"]}
                    (p/circle {:src (:src queue)}
                              (p/overlay nil (dom/i {:classes ["fa fa-spinner fa-spin"]}))))
                  (dom/label {:htmlFor "file-profile"
                              :classes ["upload-photo circle"]}
                             (if (some? upload)
                               (p/circle {:photo-id       (:public_id upload)
                                          :transformation :transformation/thumbnail})
                               (p/store-photo store {:transformation :transformation/thumbnail}
                                              (p/overlay nil (dom/i {:classes ["fa fa-camera fa-2x"]}))))
                             ;(p/cover {:photo-id     (:photo/id cover)
                             ;          :placeholder? true
                             ;          :classes      ["contain"]}
                             ;         (p/overlay nil (dom/i {:classes ["fa fa-camera fa-2x"]})))
                             ;(dom/label
                             ;  {:htmlFor (str "file-" (count photos))})
                             (photo-uploader this "profile")))))
            (grid/column
              (grid/column-size {:small 12 :medium 6})
              ;(callout/header nil "Cover photo")
              (dom/label (css/text-align :left) "Cover photo")
              (let [{:cover/keys [upload queue]} state]

                (if (some? queue)
                  (dom/div
                    {:classes "upload-photo cover loading"}
                    (p/cover {:src (:src queue)}
                             (p/overlay nil (dom/i {:classes ["fa fa-spinner fa-spin"]}))))
                  (dom/label {:htmlFor "file-cover"
                              :classes ["upload-photo cover"]}
                             (if (some? upload)
                               (p/cover {:photo-id (:public_id upload)
                                         :transformation :transformation/thumbnail})
                               (p/cover {:photo-id     (:photo/id cover)
                                         :placeholder? true}
                                        (p/overlay nil (dom/i {:classes ["fa fa-camera fa-2x"]}))))
                             ;(dom/label
                             ;  {:htmlFor (str "file-" (count photos))})
                             (photo-uploader this "cover"))))
              )))
        (callout/callout-small
          nil
          ;(callout/header nil "Store information")
          (grid/row
            nil
            (label-column nil
                          (dom/label nil "Store name"))
            (grid/column
              nil
              (dom/input {:type         "text"
                          :id           (:field.general/store-name v/form-inputs)
                          :defaultValue store-name})))
          (grid/row
            nil
            (label-column
              nil
              (dom/label nil "Short description"))
            (dom/div
              (css/grid-column)
              (dom/input
                (->> {:type         "text"
                      :placeholder  "Keep calm and wear pretty jewelry"
                      :id           (:field.general/store-tagline v/form-inputs)
                      :defaultValue tagline}
                     (css/add-class :tagline-input)))))
          (grid/row
            nil
            (label-column
              nil
              (dom/label nil "About"))
            (grid/column
              nil
              (quill/->QuillEditor (om/computed {:content     (f/bytes->str description)
                                                 :placeholder "What's your story?"
                                                 :id          "about"}
                                                {:on-editor-created #(om/update-state! this assoc :quill/about %)})))))
        (callout/callout-small
          nil
          (callout/header nil)
          (dom/div
            (css/text-align :right)
            (dom/a
              (->> {:onClick #(.save-store this)}
                   (css/button)) (dom/span nil "Save")))))
      ;(dom/div
      ;  nil
      ;  (when (or (msg/pending? update-msg) (msg/pending? photo-msg))
      ;    (common/loading-spinner))
      ;  (callout/callout-small
      ;    nil
      ;    (callout/header nil "Profile photo")
      ;    (grid/row
      ;      (css/align :center)
      ;      (grid/column
      ;        (->> (css/text-align :center)
      ;             (grid/column-size {:small 6 :medium 4 :large 3}))
      ;        (if (some? (:public_id uploaded-photo))
      ;          (p/circle {:photo-id       (:public_id uploaded-photo)
      ;                     :transformation :transformation/thumbnail-large})
      ;          (p/store-photo store :transformation/thumbnail-large))
      ;
      ;        #?(:cljs
      ;           (pu/->PhotoUploader
      ;             (om/computed
      ;               photo-upload
      ;               {:on-photo-upload (fn [photo]
      ;                                   (om/update-state! this assoc :uploaded-photo photo))}))))))
      ;  (callout/callout-small
      ;    nil
      ;    (callout/header nil "Store information")
      ;    (grid/row
      ;      nil
      ;      (label-column
      ;        nil
      ;        (dom/label nil "Store name"))
      ;      (grid/column
      ;        nil
      ;        (dom/input {:type         "text"
      ;                    :id           (:field.general/store-name v/form-inputs)
      ;                    :defaultValue (:store.profile/name profile)})))
      ;    (grid/row
      ;      nil
      ;      (label-column
      ;        nil
      ;        (dom/label nil "Short description"))
      ;      (dom/div
      ;        (css/grid-column)
      ;        (dom/input
      ;          (->> {:type         "text"
      ;                :placeholder  "Keep calm and wear pretty jewelry"
      ;                :id           (:field.general/store-tagline v/form-inputs)
      ;                :defaultValue (:store.profile/tagline profile)}
      ;               (css/add-class :tagline-input)))))
      ;    (grid/row
      ;      nil
      ;      (label-column
      ;        nil
      ;        (dom/label nil "About"))
      ;      (grid/column
      ;        nil
      ;        (quill/->QuillEditor (om/computed {:content     (f/bytes->str description)
      ;                                           :placeholder "What's your story?"
      ;                                           :id          "about"}
      ;                                          {:on-editor-created #(om/update-state! this assoc :quill/about %)})))))
      ;  (dom/div
      ;    (css/callout)
      ;    (dom/p (css/add-class :header) "Return policy")
      ;
      ;    (grid/row
      ;      nil
      ;      (label-column
      ;        nil
      ;        (dom/label nil "Policy"))
      ;      (grid/column
      ;        nil
      ;        (quill/->QuillEditor (om/computed {:content     (f/bytes->str return-policy)
      ;                                           :placeholder ""
      ;                                           :id          "return-policy"}
      ;                                          {:on-editor-created #(om/update-state! this assoc :quill/return-policy %)})))))
      ;  (dom/div
      ;    (css/callout)
      ;    (dom/p (css/add-class :header))
      ;    (dom/div
      ;      (css/text-align :right)
      ;      (dom/a
      ;        (->> {:onClick #(.save-store this)}
      ;             (css/button)) (dom/span nil "Save")))))
      )))

(def ->General (om/factory General))

