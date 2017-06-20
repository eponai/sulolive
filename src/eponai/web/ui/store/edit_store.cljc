(ns eponai.web.ui.store.edit-store
  (:require
    [eponai.common.ui.dom :as dom]
    #?(:cljs
       [eponai.client.ui.photo-uploader :as pu])
    #?(:cljs
       [eponai.web.utils :as utils])
    [eponai.common.ui.utils :refer [two-decimal-price]]
    [om.next :as om :refer [defui]]
    [eponai.web.ui.store.profile.status :as status]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.web.ui.photo :as photo]
    [eponai.common.ui.elements.css :as css]
    [eponai.web.ui.button :as button]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.client.routes :as routes]
    [eponai.common.ui.om-quill :as quill]
    [eponai.common.format :as f]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.client.parser.message :as msg]
    [eponai.common.mixpanel :as mixpanel]))

(def form-inputs
  {:field.general/store-name    "general.store-name"
   :field.general/store-tagline "general.store-tagline"
   :field.shipping/fee          "shipping.fee"})

(defn photo-uploader [component id k]
  #?(:cljs
     (let [preset (when (= k "cover") :preset/cover-photo)]
       (pu/->PhotoUploader (om/computed
                             {:id id}
                             {:on-photo-queue  (fn [img-result]
                                                 (om/update-state! component assoc (keyword k "queue") {:src img-result}))
                              :preset          preset
                              :on-photo-upload (fn [photo]
                                                 (if (= id "cover")
                                                   (mixpanel/track-key ::mixpanel/upload-photo (assoc photo :type "Store cover photo"))
                                                   (mixpanel/track-key ::mixpanel/upload-photo (assoc photo :type "Store profile photo")))
                                                 (om/update-state! component (fn [st]
                                                                               (-> st
                                                                                   (dissoc (keyword k "queue"))
                                                                                   (assoc (keyword k "upload") photo)))))})))))

(defn edit-about-section [component]
  (let [{:keys [store]} (om/get-computed component)
        {:about/keys [on-editor-change-desc
                      on-editor-create-desc] :as state} (om/get-state component)
        {{:store.profile/keys [cover tagline description]
          store-name          :store.profile/name} :store/profile} store]
    (dom/div
      (css/add-class :sl-store-about-section)
      (dom/div
        (css/add-class :section-title)
        (dom/h2 nil "About")
        (if (:edit/info state)
          (dom/div
            (css/add-class :action-buttons)
            (button/cancel {:onClick #(do
                                       (mixpanel/track "Store: Cancel edit about info")
                                       (om/update-state! component assoc :edit/info false))})
            (button/save (cond->> {:onClick #(.save-store component)}
                                  (or (some? (:cover/queue state))
                                      (some? (:profile/queue state)))
                                  (css/add-class :disabled))))
          (button/edit
            {:onClick #(do
                        (mixpanel/track "Store: Edit about info")
                        (om/update-state! component assoc :edit/info true))})))

      (when (and (:edit/info state) (:error/about state))
        (dom/p (css/add-class :section-error) (dom/small (css/add-class :text-alert) (:error/about state))))

      (callout/callout
        (css/add-class :section-content)

        ;; Enable Cover upload when in edit mode for the info section
        (let [{:cover/keys [upload queue]} state]
          (if (:edit/info state)
            (if (some? queue)
              ;; If there's a photo in queue, show the image data
              (photo/cover {:src    (:src queue)
                            :status :loading})
              (dom/label
                {:htmlFor "cover-photo-upload"}

                ;; Use the recently uploaded photo if one exists, otherwise use our saved photo.
                (photo/cover {:photo-id (or (:public_id upload) (:photo/id cover))
                              :status   :edit})
                (photo-uploader component "cover-photo-upload" "cover")))

            (let [photo-status-msg (msg/last-message component 'store.photo/upload)
                  ;; If we're waiting for response from the upload request to our server, show the uploaded url while waiting
                  photo-key (if (and (some? photo-status-msg) (msg/pending? photo-status-msg))
                              (:public_id upload)
                              (:photo/id cover))]
              (photo/cover {:photo-id photo-key}))))


        (dom/div
          (css/add-class :store-container)

          (grid/row
            (->> (css/align :middle)
                 (css/align :center))

            (grid/column
              (grid/column-size {:small 12 :medium 2})

              ;; Enable photo upload when in edit mode for the info section
              (let [{:profile/keys [upload queue]} state]
                (if (:edit/info state)
                  (if (some? queue)
                    (photo/circle {:src    (:src queue)
                                   :status :loading})
                    (dom/label
                      {:htmlFor "store-profile-photo-upload"}
                      (if (some? upload)
                        (photo/circle {:photo-id       (:public_id upload)
                                       :transformation :transformation/thumbnail
                                       :status         :edit})
                        (photo/store-photo store {:transformation :transformation/thumbnail
                                                  :status         :edit}))
                      (photo-uploader component "store-profile-photo-upload" "profile")))
                  (let [photo-status-msg (msg/last-message component 'store.photo/upload)]
                    (if (and (some? photo-status-msg)
                             (msg/pending? photo-status-msg))
                      (photo/circle {:photo-id       (:public_id upload)
                                     :transformation :transformation/thumbnail})
                      (photo/store-photo store {:transformation :transformation/thumbnail}))))))

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
                nil
                (- (:text-max/about state) (:text-length/about state))))))

        (dom/div (css/add-class :about-container)
                 (quill/->QuillEditor (om/computed {:content     (f/bytes->str description)
                                                    :enable?     (:edit/info state)
                                                    :id          "about"
                                                    :placeholder "No description"}
                                                   {:on-editor-created on-editor-create-desc
                                                    :on-text-change    on-editor-change-desc})))))))

(defui EditStore
  static om/IQuery
  (query [_]
    [:query/current-route
     :query/messages
     {:proxy/status (om/get-query status/StoreStatus)}])

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
             (mixpanel/track "Store: Save about info" {:description-length description-length})
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
           (do
             (om/update-state! this assoc :error/about "Sorry, your description is too long."))))))
  (save-return-policy [this]
    #?(:cljs
       (let [{:editor/keys [return-policy] :as state} (om/get-state this)
             {:keys [store]} (om/get-computed this)
             store-return-policy (when return-policy (quill/get-HTML return-policy))
             text-lentgh (or (when return-policy (.getLength return-policy)) 0)]
         (if (< text-lentgh (:text-max/return-policy state))
           (do
             (mixpanel/track "Store: Save return policy" {:length text-lentgh})
             (msg/om-transact! this [(list 'store/update-info {:db/id         (:db/id store)
                                                               :store/profile {:store.profile/return-policy store-return-policy}})
                                     :query/store])
             (om/update-state! this assoc :edit/return-policy false))
           (om/update-state! this assoc :error/return-policy "Sorry, your return policy is too long.")))))

  (save-shipping-policy [this]
    #?(:cljs
       (let [{:editor/keys [shipping-policy] :as state} (om/get-state this)
             {:keys [store]} (om/get-computed this)
             store-shipping-policy (when shipping-policy (quill/get-HTML shipping-policy))
             text-lentgh (or (when shipping-policy (.getLength shipping-policy)) 0)]
         (if (< text-lentgh (:text-max/shipping-policy state))
           (do
             (mixpanel/track "Store: Save shipping policy" {:length text-lentgh})
             (msg/om-transact! this [(list 'store/update-shipping {:store-id (:db/id store)
                                                                   :shipping {:shipping/policy store-shipping-policy}})
                                     :query/store])
             (om/update-state! this assoc :edit/shipping-policy false))
           (om/update-state! this assoc :error/shipping-policy "Sorry, your return policy is too long.")))))

  (initLocalState [this]
    {:edit/info                                false
     :edit/return-policy                       false
     :edit/shipping-policy                     false

     :about/on-editor-change-desc              #(om/update-state! this assoc :text-length/about (.getLength %))
     :about/on-editor-create-desc              #(om/update-state! this assoc
                                                                  :editor/about %
                                                                  :text-length/about (.getLength %))

     :return-policy/on-editor-create           #(om/update-state! this assoc
                                                                  :editor/return-policy %
                                                                  :text-length/return-policy (.getLength %))
     :return-policy/on-editor-change           #(om/update-state! this assoc :text-length/return-policy (.getLength %))

     :shipping-policy/on-editor-create         #(om/update-state! this assoc
                                                                  :editor/shipping-policy %
                                                                  :text-length/shipping-policy (.getLength %))
     :shipping-policy/on-editor-change         #(om/update-state! this assoc :text-length/shipping-policy (.getLength %))

     :products.edit-sections/new-section-count 0
     :text-max/store-name                      100
     :text-max/tagline                         140
     :text-max/about                           5000
     :text-max/return-policy                   3000
     :text-max/shipping-policy                 3000

     :text-length/about                        0
     :text-length/return-policy                0
     :text-length/shipping-policy              0

     :products/selected-section                :all})
  (render [this]
    (let [{:keys [store]} (om/get-computed this)
          {{:store.profile/keys [return-policy]} :store/profile
           {shipping-policy :shipping/policy}    :store/shipping} store
          {:query/keys [current-route ]
           :proxy/keys [status]} (om/props this)
          {:keys [store-id]} (:route-params current-route)
          {:return-policy/keys [on-editor-create on-editor-change] :as state} (om/get-state this)
          {:keys [route route-params]} current-route]
      (dom/div
        {:id "sulo-store-edit"}
        (grid/row-column
          {:id "sulo-store" :classes ["edit-store"]}
          (dom/div
            (css/add-class :section-title)
            (dom/h1 nil "Store info"))
          (dom/div
            (->> {:id "store-navbar"}
                 (css/add-class :navbar-container))
            (dom/nav
              (->> (css/add-class :navbar)
                   (css/add-class :top-bar))
              (menu/horizontal
                nil
                (menu/item
                  (when (= route :store-dashboard/profile)
                    (css/add-class :is-active))
                  (dom/a {:href (routes/url :store-dashboard/profile route-params)}
                         (dom/span nil "Info & appearance")))
                (menu/item
                  (when (= route :store-dashboard/profile#options)
                    (css/add-class :is-active))
                  (dom/a {:href (routes/url :store-dashboard/profile#options route-params)}
                         (dom/span nil "Options"))))))

          (cond (= route :store-dashboard/profile#options)
                (status/->StoreStatus status)
                :else
                [(edit-about-section this)

                 (dom/div
                   (->> (css/add-class :expanded)
                        (css/add-class :collapse)
                        (css/add-classes [:store-info-section :store-info-section--policies]))
                   (dom/div
                     nil
                     (dom/div
                       (css/add-class :section-title)
                       (dom/h2 nil "Return policy")
                       (if (:edit/return-policy state)
                         (dom/div
                           (css/add-class :action-buttons)
                           (button/cancel {:onClick #(do
                                                      (mixpanel/track "Store: Cancel edit return policy.")
                                                      (om/update-state! this assoc :edit/return-policy false)
                                                      (quill/set-content (:editor/return-policy state) (f/bytes->str return-policy)))})
                           (button/save {:onClick #(.save-return-policy this)}))
                         (button/edit {:onClick #(do
                                                  (mixpanel/track "Store: Edit return policy.")
                                                  (om/update-state! this assoc :edit/return-policy true))})))
                     (when (and (:edit/return-policy state) (:error/return-policy state))
                       (dom/p (css/add-class :section-error) (dom/small (css/add-class :text-alert) (:error/return-policy state))))

                     (callout/callout-small
                       nil
                       (when (:edit/return-policy state)
                         (dom/div
                           (css/text-align :right)
                           (let [remaining (- (:text-max/return-policy state)
                                              (:text-length/return-policy state))]
                             (dom/small
                               (when (neg? remaining)
                                 (css/add-class :text-alert))
                               remaining))))
                       (quill/->QuillEditor (om/computed {:content     (f/bytes->str return-policy)
                                                          :id          "return-policy"
                                                          :enable?     (:edit/return-policy state)
                                                          :placeholder "No return policy"}
                                                         {:on-editor-created on-editor-create
                                                          :on-text-change    on-editor-change}))))
                   (dom/div
                     (when (:edit/shipping-policy state)
                       (css/add-class :editable))
                     (dom/div
                       (css/add-class :section-title)
                       (dom/h2 nil "Shipping policy")
                       (if (:edit/shipping-policy state)
                         (dom/div
                           (css/add-class :action-buttons)
                           (button/cancel {:onClick #(do
                                                      (mixpanel/track "Store: Cancel edit shipping policy")
                                                      (om/update-state! this assoc :edit/shipping-policy false)
                                                      (quill/set-content (:editor/shipping-policy state) (f/bytes->str shipping-policy)))})
                           (button/save {:onClick #(do
                                                    (mixpanel/track "Store: Save shipping policy")
                                                    (.save-shipping-policy this)
                                                    )}))
                         (button/edit {:onClick #(do
                                                  (mixpanel/track "Store: Edit shipping policy")
                                                  (om/update-state! this assoc :edit/shipping-policy true))})))
                     (callout/callout-small
                       (css/add-classes [:store-info-policy :store-info-policy--shipping])
                       ;(when (:edit/shipping-policy state)
                       ;  (dom/p nil
                       ;         (dom/small nil "Bas"))
                       ;  (callout/callout-small
                       ;    (css/add-class :warning)
                       ;    (dom/small nil
                       ;               "We're not quite ready with the work on shipping settings, so this section cannot be saved yet. We're working on it, hang in there!"))
                       ;  )
                       (if (:edit/shipping-policy state)
                         (dom/div
                           (css/text-align :right)
                           (let [remaining (- (:text-max/shipping-policy state)
                                              (:text-length/shipping-policy state))]
                             (dom/small
                               (when (neg? remaining)
                                 (css/add-class :text-alert))
                               remaining))))
                       (quill/->QuillEditor (om/computed {:content     (f/bytes->str shipping-policy)
                                                          :id          "shipping-policy"
                                                          :enable?     (:edit/shipping-policy state)
                                                          :placeholder "No shipping policy"}
                                                         {:on-editor-created (:shipping-policy/on-editor-create state)
                                                          :on-text-change    (:shipping-policy/on-editor-change state)}))
                       )))]))))))

(def ->EditStore (om/factory EditStore))