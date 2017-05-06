(ns eponai.web.ui.store.edit-store
  (:require
    [eponai.common.ui.dom :as dom]
    #?(:cljs
       [eponai.client.ui.photo-uploader :as pu])
    #?(:cljs
       [eponai.web.utils :as utils])
    [eponai.common.ui.utils :refer [two-decimal-price]]
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
    [eponai.client.parser.message :as msg]
    [clojure.string :as string]))

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

(defn edit-button [opts & content]
  (dom/a
    (->> (css/button-hollow opts)
         (css/add-class :shrink))
    (dom/i {:classes ["fa fa-pencil fa-fw"]})
    ;(dom/span nil "Edit info")
    ))

(defn save-button [opts & content]
  (dom/a (css/button opts)
         (dom/span nil "Save")))

(defn cancel-button [opts & content]
  (dom/a (css/button-hollow opts)
         (dom/span nil "Cancel")))

(defn edit-about-section [component]
  (let [{:keys [store]} (om/get-computed component)
        {:about/keys [on-editor-change-desc
                      on-editor-create-desc] :as state} (om/get-state component)
        {{:store.profile/keys [cover tagline description]
          store-name          :store.profile/name} :store/profile} store
        ]
    (dom/div
      (css/add-class :sl-store-about-section)
      (dom/div
        (css/add-class :section-title)
        (dom/h1 nil (dom/small nil "About"))
        (if (:edit/info state)
          (dom/div
            nil
            (cancel-button {:onClick #(om/update-state! component assoc :edit/info false)})
            (save-button {:onClick #(.save-store component)}))
          (edit-button {:onClick #(om/update-state! component assoc :edit/info true)})))
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
                (- (:text-max/about state) (:text-length/about state))))))

        (dom/div (css/add-class :about-container)
                 (quill/->QuillEditor (om/computed {:content     (f/bytes->str description)
                                                    :enable?     (:edit/info state)
                                                    :id          "about"
                                                    :placeholder "No description"}
                                                   {:on-editor-created on-editor-create-desc
                                                    :on-text-change on-editor-change-desc}))
                 )))))

(defn products-section [component]
  (let [{:keys [store]} (om/get-computed component)
        {:products/keys [selected-section search-input]} (om/get-state component)
        {:store/keys [items]} store
        items (cond->> items
                       (not= selected-section :all)
                       (filter #(= selected-section (get-in % [:store.item/section :store.section/label])))
                       (not-empty search-input)
                       (filter #(clojure.string/includes? (.toLowerCase (:store.item/name %))
                                                          (.toLowerCase search-input))))]
    (callout/callout-small
      nil
      (dom/input
        {:key         "profile.products.search"
         :value       (or search-input "")
         :onChange    #(om/update-state! component assoc :products/search-input (.. % -target -value))
         :placeholder "Search Products..."
         :type        "text"})
      (menu/horizontal
        (css/add-class :product-section-menu)
        (menu/item
          (when (= selected-section :all)
            (css/add-class :is-active))
          (dom/a
            {:onClick #(om/update-state! component assoc :products/selected-section :all)}
            (dom/span nil "All items")))
        (map (fn [s]
               (menu/item
                 (when (= selected-section (:store.section/label s))
                   (css/add-class :is-active))
                 (dom/a {:onClick #(om/update-state! component assoc :products/selected-section (:store.section/label s))}
                        (dom/span nil (string/capitalize (:store.section/label s))))))
             (concat (:store/sections store) (:store/sections store) (:store/sections store) (:store/sections store))))
      (grid/products items
                     (fn [p]
                       (let [{:store.item/keys [price]
                              item-name        :store.item/name} p]
                         (dom/div
                           (->> (css/add-class :content-item)
                                (css/add-class :product-item))
                           (dom/div
                             (->>
                               (css/add-class :primary-photo))
                             (photo/product-preview p))

                           (dom/div
                             (->> (css/add-class :header)
                                  (css/add-class :text))
                             (dom/div
                               nil
                               (dom/span nil item-name)))
                           ;(dom/div
                           ;  (css/add-class :text)
                           ;  (dom/small
                           ;    nil
                           ;    (dom/span nil "by ")
                           ;    (dom/a {:href (routes/url :store {:store-id (:db/id store)})}
                           ;           (dom/span nil (:store.profile/name (:store/profile store))))))

                           (dom/div
                             (css/add-class :text)
                             (dom/strong nil (two-decimal-price price))))))))))

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
       (let [{:editor/keys [return-policy] :as state} (om/get-state this)
             {:keys [store]} (om/get-computed this)
             store-return-policy (when return-policy (quill/get-HTML return-policy))
             text-lentgh (or (when return-policy (.getLength return-policy)) 0)]
         (if (< text-lentgh (:text-max/return-policy state))
           (do
             (msg/om-transact! this [(list 'store/update-info {:db/id         (:db/id store)
                                                               :store/profile {:store.profile/return-policy store-return-policy}})
                                     :query/store])
             (om/update-state! this assoc :edit/return-policy false))
           (om/update-state! this assoc :error/return-policy "Sorry, your return policy is too long.")))))

  (update-text-counter [this id value]
    #?(:cljs
       (when-let [counter (utils/element-by-id id)]
         (.removeChild counter (.-firstChild counter))
         (.appendChild counter (.createTextNode js/document value))
         (if (neg? value)
           (set! (.-className counter) "text-alert")
           (set! (.-className counter) "")))))

  (initLocalState [this]
    {:edit/info                   false
     :edit/return-policy          false
     :edit/shipping-policy        false

     :about/on-editor-change-desc #(.update-text-counter this "text-length.about"
                                                         (- (:text-max/about (om/get-state this))
                                                            (.getLength %)))
     :about/on-editor-create-desc #(om/update-state! this assoc
                                                     :editor/about %
                                                     :text-length/about (.getLength %))

     :return-policy/on-editor-create #(om/update-state! this assoc
                                                        :editor/return-policy %
                                                        :text-length/return-policy (.getLength %))
     :return-policy/on-editor-change #(.update-text-counter this "text-length.return-policy"
                                                            (- (:text-max/return-policy (om/get-state this))
                                                               (.getLength %)))

     :shipping-policy/on-editor-create #(om/update-state! this assoc
                                                          :editor/shipping-policy %
                                                          :text-length/shipping-policy (.getLength %))
     :shipping-policy/on-editor-change #(.update-text-counter this "text-length.shipping-policy"
                                                              (- (:text-max/shipping-policy (om/get-state this))
                                                                 (.getLength %)))

     :text-max/store-name         100
     :text-max/tagline            140
     :text-max/about              500
     :text-max/return-policy      500
     :text-max/shipping-policy    500

     :text-length/about           0
     :text-length/return-policy   0
     :text-length/shipping-policy 0

     :products/selected-section   :all})
  (render [this]
    (let [{:keys [store]} (om/get-computed this)
          {{:store.profile/keys [return-policy]} :store/profile
           store-items                           :store/items} store
          {:query/keys [current-route]} (om/props this)
          {:keys [store-id]} (:route-params current-route)
          shipping-policy nil
          {:return-policy/keys [on-editor-create on-editor-change] :as state} (om/get-state this)]
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
              (dom/div
                (css/add-class :section-title)
                (dom/h1 nil (dom/small nil "Return policy"))
                (if (:edit/return-policy state)
                  (dom/div
                    nil
                    (cancel-button {:onClick #(do
                                               (om/update-state! this assoc :edit/return-policy false)
                                               (quill/set-content (:editor/return-policy state) (f/bytes->str return-policy)))})
                    (save-button {:onClick #(.save-return-policy this)}))
                  (edit-button {:onClick #(om/update-state! this assoc :edit/return-policy true)})))
              (when (and (:edit/return-policy state) (:error/return-policy state))
                (dom/p (css/add-class :section-error) (dom/small (css/add-class :text-alert) (:error/return-policy state))))

              (callout/callout-small
                nil
                (when (:edit/return-policy state)
                  (dom/div
                    (css/text-align :right)
                    (dom/small
                      {:id "text-length.return-policy"} (- (:text-max/return-policy state)
                                                           (:text-length/return-policy state)))))
                (quill/->QuillEditor (om/computed {:content     (f/bytes->str return-policy)
                                                   :id          "return-policy"
                                                   :enable?     (:edit/return-policy state)
                                                   :placeholder "No return policy"}
                                                  {:on-editor-created on-editor-create
                                                   :on-text-change    on-editor-change}))))
            (grid/column
              (grid/column-size {:small 12 :medium 6})
              (dom/div
                (css/add-class :section-title)
                (dom/h1 nil (dom/small nil "Shipping policy"))
                (if (:edit/shipping-policy state)
                  (dom/div
                    nil
                    (cancel-button {:onClick #(do
                                               (om/update-state! this assoc :edit/shipping-policy false)
                                               (quill/set-content (:editor/shipping-policy state) (f/bytes->str shipping-policy)))})
                    (save-button nil))
                  (edit-button {:onClick #(om/update-state! this assoc :edit/shipping-policy true)})))
              (callout/callout-small
                nil
                (when (:edit/shipping-policy state)
                  (dom/div
                    (css/text-align :right)
                    (dom/small
                      {:id "text-length.shipping-policy"} (- (:text-max/shipping-policy state)
                                                             (:text-length/shipping-policy state)))))
                (quill/->QuillEditor (om/computed {:content     (f/bytes->str shipping-policy)
                                                   :id          "shipping-policy"
                                                   :enable?     (:edit/shipping-policy state)
                                                   :placeholder "No shipping policy"}
                                                  {:on-editor-created (:shipping-policy/on-editor-create state)
                                                   :on-text-change    (:shipping-policy/on-editor-change state)}))
                )))

          (dom/div
            (css/add-class :section-title)
            (dom/h1 nil (dom/small nil "Products"))
            (dom/a
              (css/button-hollow {:href (routes/url :store-dashboard/product-list {:store-id store-id})})
              (dom/span nil "Product list")))

          (products-section this))))))

(def ->EditStore (om/factory EditStore))