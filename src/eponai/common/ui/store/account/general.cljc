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
    [eponai.common.ui.common :as common]))

(defn label-column [opts & content]
  (grid/column
    (grid/column-size {:small 12 :large 2})
    content))

(defui General
  static om/IQuery
  (query [_]
    [#?(:cljs
        {:proxy/photo-upload (om/get-query pu/PhotoUploader)})
     :query/messages])
  Object
  (save-store [this]
    #?(:cljs (let [{:keys [uploaded-photo quill-editor]} (om/get-state this)
                   {:keys [store]} (om/get-computed this)

                   store-name (utils/input-value-by-id (:field.general/store-name v/form-inputs))
                   store-tagline (utils/input-value-by-id (:field.general/store-tagline v/form-inputs))
                   store-description (quill/get-HTML quill-editor)]
               (msg/om-transact! this [(list 'store/update-info {:db/id             (:db/id store)
                                                                 :store/name        store-name
                                                                 :store/tagline     store-tagline
                                                                 :store/description store-description})
                                       (list 'store.photo/upload {:photo uploaded-photo :store-id (:db/id store)})
                                       :query/store]))))
  (render [this]
    (let [{:keys [modal uploaded-photo]} (om/get-state this)
          {:proxy/keys [photo-upload]} (om/props this)
          {:keys [store]} (om/get-computed this)
          {:store/keys [description]} store
          update-msg (msg/last-message this 'store/update-info)
          photo-msg (msg/last-message this 'store.photo/upload)]
      (dom/div
        nil
        (when (or (msg/pending? update-msg) (msg/pending? photo-msg))
          (common/loading-spinner))
        (dom/div
          (css/callout)
          ;(dom/p (css/add-class :header) "Profile photo")
          (grid/row
            (css/align :center)
            (grid/column
              (->> (css/text-align :center)
                   (grid/column-size {:small 6 :medium 4 :large 3}))
              (if (some? (:location uploaded-photo))
                (photo/circle {:src (:location uploaded-photo)})
                (photo/store-photo store))
              ;(dom/a (->> {:onClick #(om/update-state! this :modal :photo-upload)}
              ;            (css/button-hollow)) "Upload Photo")
              ;(when (= modal :photo-upload))
              #?(:cljs
                 (pu/->PhotoUploader
                   (om/computed
                     photo-upload
                     {:on-photo-upload (fn [photo]
                                         (om/update-state! this assoc :uploaded-photo photo))}))))))
        (dom/div
          (css/callout)
          ;(dom/p (css/add-class :header) "Public information")
          (grid/row
            nil
            ;(css/align :center)
            ;(grid/column
            ;  (->> (css/text-align :center)
            ;       (grid/column-size {:small 6 :medium 4 :large 3}))
            ;  (photo/store-photo store)
            ;  (dom/a (css/button-hollow) "Upload Photo"))
            (grid/column
              (grid/column-size {:small 12})
              (grid/row
                nil
                (label-column
                  nil
                  (dom/label nil "Store name"))
                (grid/column
                  nil
                  (dom/input {:type         "text"
                              :id           (:field.general/store-name v/form-inputs)
                              :defaultValue (:store/name store)})))
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
                          :defaultValue (:store/tagline store)}
                         (css/add-class :tagline-input)))))
              (grid/row
                nil
                (label-column
                  nil
                  (dom/label nil "About"))
                (grid/column
                  nil
                  (quill/->QuillEditor (om/computed {:content     (f/bytes->str description)
                                                     :placeholder "What's your story?"}
                                                    {:on-editor-created #(om/update-state! this assoc :quill-editor %)})))))))
        (dom/div
          (css/callout)
          (dom/p (css/add-class :header))
          (dom/div
            (css/text-align :right)
            (dom/a
              (->> {:onClick #(.save-store this)}
                   (css/button)) (dom/span nil "Save"))))))))

(def ->General (om/factory General))

