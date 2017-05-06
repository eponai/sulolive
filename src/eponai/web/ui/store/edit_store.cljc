(ns eponai.web.ui.store.edit-store
  (:require
    [eponai.common.ui.dom :as dom]
    #?(:cljs
       [eponai.client.ui.photo-uploader :as pu])
    #?(:cljs
       [eponai.web.utils :as utils])
    [om.next :as om :refer [defui]]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.web.ui.photo :as photo]
    [eponai.common.ui.elements.css :as css]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.client.routes :as routes]
    [eponai.common.ui.om-quill :as quill]
    [eponai.common.format :as f]
    [eponai.common.ui.product-item :as pi]
    ;[eponai.common.ui.elements.photo :as old-photo]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.client.parser.message :as msg]))

(def form-inputs
  {:field.general/store-name    "general.store-name"
   :field.general/store-tagline "general.store-tagline"})

(defn photo-uploader [component id]
  (let [{:proxy/keys [photo-upload]} (om/props component)]

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
(defn edit-about-section [component]
  (let [{:keys [store]} (om/get-computed component)
        state (om/get-state component)
        {{:store.profile/keys [cover tagline description]
          store-name          :store.profile/name} :store/profile} store]
    (dom/div
      (css/add-class :sl-store-about-section)
      (dom/div
        (css/add-class :section-title)
        (dom/h1 nil (dom/small nil "About"))
        (if (:edit/info state)
          (dom/div
            nil
            (dom/a (css/button-hollow {:onClick #(om/update-state! component assoc :edit/info false)})
                   (dom/span nil "Cancel"))
            (dom/a (css/button {:onClick #(.save-store component)})
                   (dom/span nil "Save")))
          (dom/a
            (->> (css/button-hollow {:onClick #(om/update-state! component assoc :edit/info true)})
                 (css/add-class :shrink))
            (dom/i {:classes ["fa fa-pencil fa-fw"]})
            ;(dom/span nil "Edit info")
            )))
      (when (and (:edit/info state) (:error/about state))
        (dom/p (css/add-class :section-error) (dom/small (css/add-class :text-alert) (:error/about state))))

      (callout/callout
        (css/add-class :section-content)

        ;; Enable Cover upload when in edit mode for the info section
        (if (:edit/info state)
          (let [{:cover/keys [upload queue]} state]
            (if (some? queue)
              (dom/div
                {:classes "upload-photo cover loading"}
                (photo/cover {:src (:src queue)}
                             (photo/overlay nil (dom/i {:classes ["fa fa-spinner fa-spin"]}))))
              (dom/label {:htmlFor "file-cover"
                          :classes ["upload-photo cover"]}
                         (if (some? upload)
                           (photo/cover {:photo-id       (:public_id upload)
                                         :transformation :transformation/preview}
                                        (photo/overlay nil (dom/i {:classes ["fa fa-camera fa-fw"]})))
                           (photo/cover {:photo-id     (:photo/id cover)
                                         :placeholder? true}
                                        (photo/overlay nil (dom/i {:classes ["fa fa-camera fa-fw"]}))))
                         ;(dom/label
                         ;  {:htmlFor (str "file-" (count photos))})
                         (photo-uploader component "cover"))))
          (photo/cover {:photo-id     (:photo/id cover)
                        :placeholder? true}))


        (dom/div
          (css/add-class :store-container)

          (grid/row
            (->> (css/align :middle)
                 (css/align :center))

            (grid/column
              (grid/column-size {:small 12 :medium 2})

              ;; Enable photo upload when in edit mode for the info section
              (if (:edit/info state)
                (let [{:profile/keys [upload queue]} state]

                  (if (some? queue)
                    (dom/div
                      {:classes ["upload-photo circle loading"]}
                      (photo/circle {:src (:src queue)}
                                    (photo/overlay nil (dom/i {:classes ["fa fa-spinner fa-spin"]}))))
                    (dom/label {:htmlFor "file-profile"
                                :classes ["upload-photo circle"]}
                               (if (some? upload)
                                 (photo/circle {:photo-id       (:public_id upload)
                                                :transformation :transformation/thumbnail}
                                               (photo/overlay nil (dom/i {:classes ["fa fa-camera fa-fw"]})))
                                 (photo/store-photo store {:transformation :transformation/thumbnail}
                                                    (photo/overlay nil (dom/i {:classes ["fa fa-camera fa-fw"]}))))
                               (photo-uploader component "profile"))))
                (photo/store-photo store {:transformation :transformation/thumbnail})))

            (if (:edit/info state)
              (grid/column
                nil
                (dom/input {:type         "text"
                            :placeholder  "Store name"
                            :defaultValue store-name
                            :id           (:field.general/store-name form-inputs)
                            :maxLength    (:text-max/store-name state)})
                (dom/input {:type         "text"
                            :placeholder  "Keep calm and wear pretty jewelry"
                            :defaultValue tagline
                            :id           (:field.general/store-tagline form-inputs)
                            :maxLength    (:text-max/tagline state)}))
              (grid/column
                (css/add-class :shrink)
                (dom/div (css/add-class :store-name)
                         (dom/strong nil store-name))
                (dom/p nil
                       (dom/span (css/add-class :tagline)
                                 (or (not-empty tagline) "No tagline")))))))

        ;; About description
        (grid/row
          (->> (css/add-class :expanded)
               (css/add-class :collapse)
               (css/align :bottom))
          (grid/column
            nil (dom/label nil "Description"))
          (when (:edit/info state)
            (grid/column
              (css/text-align :right)
              (dom/small
                {:id "text-length.about"}
                (- 500 (:text-length/about state))))))

        (dom/div (css/add-class :about-container)
                 (quill/->QuillEditor (om/computed {:content     (f/bytes->str description)
                                                    :enable?     (:edit/info state)
                                                    :id          "about"
                                                    :placeholder "No description"}
                                                   {:on-editor-created #(om/update-state! component assoc
                                                                                          :editor/about %
                                                                                          :text-length/about (.getLength %))
                                                    :on-text-change    #(.update-text-counter component "text-length.about"
                                                                                              (- (:text-max/about state)
                                                                                                 (.getLength %)))})))))))

(defui EditStore
  static om/IQuery
  (query [_]
    [#?(:cljs
        {:proxy/photo-upload (om/get-query pu/PhotoUploader)})
     :query/current-route
     :query/messages])
  Object
  (save-store [this]
    #?(:cljs
       (let [{uploaded-photo :profile/upload
              uploaded-cover :cover/upload
              :editor/keys   [about] :as state} (om/get-state this)
             {:keys [store]} (om/get-computed this)

             store-name (utils/input-value-by-id (:field.general/store-name form-inputs))
             store-tagline (utils/input-value-by-id (:field.general/store-tagline form-inputs))
             store-description (when about (quill/get-HTML about))
             description-length (or (when about (.getLength about)) 0)]

         (if (< description-length (:text-max/about state))
           (do
             (msg/om-transact! this (cond-> [(list 'store/update-info {:db/id         (:db/id store)
                                                                       :store/profile {:store.profile/name        store-name
                                                                                       :store.profile/tagline     store-tagline
                                                                                       :store.profile/description store-description}})]
                                            (some? uploaded-photo)
                                            (conj (list 'store.photo/upload {:photo     uploaded-photo
                                                                             :photo-key :store.profile/photo
                                                                             :store-id  (:db/id store)}))

                                            (some? uploaded-cover)
                                            (conj (list 'store.photo/upload {:photo     uploaded-cover
                                                                             :photo-key :store.profile/cover
                                                                             :store-id  (:db/id store)}))

                                            :always
                                            (conj :query/store)))
             (om/update-state! this assoc :edit/info false))
           (om/update-state! this assoc :error/about "Sorry, your description is too long.")))))
  (save-return-policy [this]
    #?(:cljs
       (let [{:editor/keys [return-policy]} (om/get-state this)
             {:keys [store]} (om/get-computed this)
             store-return-policy (when return-policy (quill/get-HTML return-policy))]
         (msg/om-transact! this [(list 'store/update-info {:db/id         (:db/id store)
                                                           :store/profile {:store/return-policy store-return-policy}})]))))

  (update-text-counter [this id value]
    #?(:cljs
       (when-let [counter (utils/element-by-id id)]
         (.removeChild counter (.-firstChild counter))
         (.appendChild counter (.createTextNode js/document value))
         (if (neg? value)
           (set! (.-className counter) "text-alert")
           (set! (.-className counter) "")))))

  (initLocalState [this]
    {:edit/info            false
     :edit/return-policy   false
     :edit/shipping-policy false

     :text-max/store-name  100
     :text-max/tagline     140
     :text-max/about       500
     :text-length/about    0})
  (render [this]
    (let [{:keys [store]} (om/get-computed this)
          {{:store.profile/keys [return-policy]} :store/profile
           store-items                               :store/items} store
          {:query/keys [current-route]} (om/props this)
          state (om/get-state this)]
      (debug "Edit store: " store)
      (dom/div
        {:id "sulo-store-edit"}
        (grid/row-column
          {:id "sulo-store" :classes ["edit-store"]}
          (edit-about-section this)

          (grid/row
            (->> (css/add-class :expanded)
                 (css/add-class :collapse)
                 (css/add-class :policies))
            (grid/column
              (grid/column-size {:small 12 :medium 6})
              (grid/row
                (->> (css/align :bottom)
                     (css/add-class :expanded)
                     (css/add-class :collapse))
                (grid/column
                  nil
                  (dom/h1 nil (dom/small nil "Return policy")))
                (grid/column
                  (->> (css/text-align :right)
                       (css/add-class :shrink))

                  (if (:edit/return-policy state)
                    (dom/div
                      nil
                      (dom/a (css/button-hollow {:onClick #(om/update-state! this assoc :edit/return-policy false)})
                             (dom/span nil "Cancel"))
                      (dom/a (css/button {:onClick #(do (.save-return-policy this)
                                                        (om/update-state! this assoc :edit/return-policy false))})
                             (dom/span nil "Save")))
                    (dom/a
                      (->> (css/button-hollow {:onClick #(om/update-state! this assoc :edit/return-policy true)})
                           (css/add-class :shrink))
                      (dom/i {:classes ["fa fa-pencil fa-fw"]})
                      ;(dom/span nil "Edit info")
                      ))))
              (callout/callout-small
                nil
                (quill/->QuillEditor (om/computed {:content     (f/bytes->str return-policy)
                                                   :id          "return-policy"
                                                   :enable?     (:edit/return-policy state)
                                                   :placeholder "No return policy"}
                                                  {:on-editor-created #(om/update-state! this assoc :editor/return-policy %)}))
                ;(quill/->QuillRenderer {:html (f/bytes->str return-policy)})
                ))
            (grid/column
              (grid/column-size {:small 12 :medium 6})
              (grid/row
                (->> (css/align :bottom)
                     (css/add-class :expanded)
                     (css/add-class :collapse))
                (grid/column
                  nil
                  (dom/h1 nil (dom/small nil "Shipping policy")))
                (grid/column
                  (->> (css/text-align :right)
                       (css/add-class :shrink))
                  (dom/a (css/button-hollow)
                         (dom/i {:classes ["fa fa-pencil fa-fw"]})
                         ;(dom/span nil "Edit")
                         )))
              (callout/callout-small
                nil

                (quill/->QuillRenderer {:html (f/bytes->str return-policy)}))))

          (dom/h1 nil (dom/small nil "Products"))
          (callout/callout-small
            nil
            (grid/products store-items
                           (fn [p]
                             (pi/->ProductItem {:product p})))))))))

(def ->EditStore (om/factory EditStore))