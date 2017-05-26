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
    [eponai.web.ui.button :as button]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.client.routes :as routes]
    [eponai.common.ui.om-quill :as quill]
    [eponai.common.format :as f]
    ;[eponai.common.ui.elements.photo :as old-photo]
    [eponai.common.ui.elements.callout :as callout]
    ;[eponai.web.ui.store.common :as store-common :refer [edit-button save-button cancel-button]]
    [eponai.client.parser.message :as msg]
    [clojure.string :as string]
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
            nil
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

(defn products-section [component]
  (let [{:query/keys [current-route]} (om/props component)
        {:keys [store]} (om/get-computed component)
        {:products/keys                [selected-section search-input edit-sections]
         :products.edit-sections?/keys [new-section-count]} (om/get-state component)
        {:store/keys [items]} store
        items (cond->> (sort-by :store.item/index items)
                       (not= selected-section :all)
                       (filter #(= selected-section (get-in % [:store.item/section :db/id])))
                       (not-empty search-input)
                       (filter #(clojure.string/includes? (.toLowerCase (:store.item/name %))
                                                          (.toLowerCase search-input))))]
    (dom/div
      nil
      (when (some? edit-sections)
        (common/modal
          {:on-close #(om/update-state! component dissoc :products/edit-sections)}
          (let [items-by-section (group-by #(get-in % [:store.item/section :db/id]) (:store/items store))]
            (dom/div
              nil
              (dom/p (css/add-class :header) "Edit sections")
              (menu/vertical
                (css/add-class :edit-sections-menu)
                (map-indexed
                  (fn [i s]
                    (let [no-items (count (get items-by-section (:db/id s)))]
                      (menu/item (css/add-class :edit-sections-item)
                                 ;(dom/a nil (dom/i {:classes ["fa "]}))
                                 (dom/input
                                   {:type        "text"
                                    :id          (str "input.section-" i)
                                    :placeholder "New section"
                                    :value       (:store.section/label s "")
                                    :onChange    #(om/update-state! component update :products/edit-sections
                                                                    (fn [sections]
                                                                      (let [old (get sections i)
                                                                            new (assoc old :store.section/label (.-value (.-target %)))]
                                                                        (assoc sections i new))))})
                                 (if (= 1 no-items)
                                   (dom/small nil (str no-items " item"))
                                   (dom/small nil (str no-items " items")))
                                 (button/user-setting-default
                                   {:onClick #(om/update-state! component update :products/edit-sections
                                                                (fn [sections]
                                                                  (into [] (remove nil? (assoc sections i nil)))))}
                                   (dom/span nil "Remove")))))
                  edit-sections)
                (menu/item
                  (css/add-class :edit-sections-item)
                  (button/user-setting-default
                    {:onClick #(om/update-state! component update :products/edit-sections conj {})}
                    (dom/span nil "Add section..."))))

              (dom/div
                (->> (css/text-align :right)
                     (css/add-class :action-buttons))
                (button/cancel {:onClick #(om/update-state! component dissoc :products/edit-sections)})
                (button/save {:onClick #(.save-sections component)}))))))
      (callout/callout-small
        nil
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
                   (when (= selected-section (:db/id s))
                     (css/add-class :is-active))
                   (dom/a {:onClick #(om/update-state! component assoc :products/selected-section (:db/id s))}
                          (dom/span nil (string/capitalize (:store.section/label s))))))
               (:store/sections store)))

        ;(dom/input
        ;  {:key         "profile.products.search"
        ;   :value       (or search-input "")
        ;   :onChange    #(om/update-state! component assoc :products/search-input (.. % -target -value))
        ;   :placeholder "Search Products..."
        ;   :type        "text"})
        (grid/products items
                       (fn [p]
                         (let [{:store.item/keys [price]
                                item-name        :store.item/name} p]
                           (dom/a
                             (->> {:href (routes/url :store-dashboard/product (assoc (:route-params current-route) :product-id (:db/id p)))}
                                  (css/add-class :content-item)
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
                             (dom/div
                               (css/add-class :text)
                               (dom/strong nil (two-decimal-price price)))
                             ;(menu/horizontal
                             ;  (css/add-class :edit-item-menu)
                             ;  (menu/item nil
                             ;             (dom/a {:href (routes/url :store-dashboard/product
                             ;                                       {:product-id (:db/id p)
                             ;                                        :store-id   (:db/id store)})}
                             ;                    (dom/i {:classes ["fa fa-pencil fa-fw"]})
                             ;                    (dom/span nil "Go to edit"))))
                             ))))))))

(defui EditStore
  static om/IQuery
  (query [_]
    [:query/current-route
     :query/messages
     {:query/countries [{:country/continent [:continent/code]}]}])

  Object
  (save-sections [this]
    (let [{:products/keys [edit-sections]} (om/get-state this)
          {:query/keys [current-route]} (om/props this)
          new-sections (filter #(not-empty (string/trim (:store.section/label % ""))) edit-sections)]
      (msg/om-transact! this [(list 'store/update-sections {:sections new-sections
                                                            :store-id (get-in current-route [:route-params :store-id])})
                              :query/store])
      (om/update-state! this dissoc :products/edit-sections)))

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
             shipping-fee (utils/input-value-or-nil-by-id (:field.shipping/fee form-inputs))
             text-lentgh (or (when shipping-policy (.getLength shipping-policy)) 0)]
         (if (< text-lentgh (:text-max/shipping-policy state))
           (do
             (mixpanel/track "Store: Save shipping policy" {:length text-lentgh})
             (msg/om-transact! this [(list 'store/update-info {:db/id         (:db/id store)
                                                               :store/profile {:store.profile/shipping-policy store-shipping-policy
                                                                               :store.profile/shipping-fee shipping-fee}})
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
          {{:store.profile/keys [return-policy shipping-policy shipping-fee]} :store/profile} store
          {:query/keys [current-route countries]} (om/props this)
          {:keys [store-id]} (:route-params current-route)
          {:return-policy/keys [on-editor-create on-editor-change] :as state} (om/get-state this)]
      ;(debug "Countries: " countries)
      (dom/div
        {:id "sulo-store-edit"}
        (grid/row-column
          {:id "sulo-store" :classes ["edit-store"]}
          (dom/h1 (css/show-for-sr) "Edit store")
          (edit-about-section this)

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
                    nil
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
                    nil
                    (button/cancel {:onClick #(do
                                               (mixpanel/track "Store: Cancel edit shipping policy")
                                               (om/update-state! this assoc :edit/shipping-policy false)
                                               (quill/set-content (:editor/shipping-policy state) (f/bytes->str shipping-policy)))})
                    (button/save {:onClick #(do
                                             (mixpanel/track "Store: Save shipping policy")
                                             ;(.save-shipping-policy this)
                                             )}))
                  (button/edit {:onClick #(do
                                           (mixpanel/track "Store: Edit shipping policy")
                                           (om/update-state! this assoc :edit/shipping-policy true))})))
              (callout/callout-small
                (css/add-classes [:store-info-policy :store-info-policy--shipping])
                (when (:edit/shipping-policy state)
                  (dom/p nil
                         (dom/small nil "Bas"))
                  (callout/callout-small
                    (css/add-class :warning)
                    (dom/small nil
                               "We're not quite ready with the work on shipping settings, so this section cannot be saved yet. We're working on it, hang in there!")))
                (dom/div
                  (->> (css/add-class :shipping-fee))
                  ;(grid/column
                  ;  (css/add-class :shrink)
                  ;  (dom/label nil "Fee"))
                  ;(grid/column
                  ;  nil)
                  (dom/label nil "Fee")
                  (dom/input
                    (cond-> {:id           (:field.shipping/fee form-inputs)
                             :type         "number"
                             :defaultValue (or shipping-fee 0)
                             :step         0.01}
                            (not (:edit/shipping-policy state))
                            (assoc :readOnly true)))
                  (dom/small nil "Your base shipping fee that will be added to all orders. Additional cost can also be specified for each product."))

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
                )))

          (dom/div
            (css/add-class :product-sections-container)
            (dom/div
              (css/add-class :section-title)
              (dom/h2 nil "Product sections")
              (dom/div
                (css/text-align :right)
                (button/edit {:onClick #(do
                                         (mixpanel/track "Store: Edit product sections")
                                         (om/update-state! this assoc :products/edit-sections (into [] (:store/sections store))))})
                (button/default-hollow
                  {:href    (routes/url :store-dashboard/product-list {:store-id (:db/id store)})
                   :onClick #(mixpanel/track-key ::mixpanel/go-to-products {:source "store-info"})}
                  (dom/span nil "Products")
                  (dom/i {:classes ["fa fa-chevron-right"]}))
                ))

            (products-section this)))))))

(def ->EditStore (om/factory EditStore))