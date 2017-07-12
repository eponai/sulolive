(ns eponai.web.ui.content-item
  (:require
    [om.next :as om :refer [defui]]
    [eponai.client.routes :as routes]
    [eponai.common.ui.elements.css :as css]
    [eponai.web.ui.photo :as photo]
    [eponai.common.ui.dom :as dom]
    [taoensso.timbre :refer [debug]]
    [eponai.common.shared :as shared]
    #?(:cljs
       [eponai.web.firebase :as firebase])
    [eponai.common.ui.product :as product]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.utils :as ui-utils]
    [eponai.web.ui.button :as button]))

(defui OnlineChannel
  static om/IQuery
  (query [_]
    [:db/id
     :stream/title
     {:stream/store [:db/id
                     {:store/locality [:sulo-locality/path]}
                     {:store/profile [:store.profile/name
                                      {:store.profile/photo [:photo/path :photo/id]}]}
                     {:store/status [:status/type]}
                     :store/username]}])
  Object
  (initLocalState [_]
    {:visitor-count 0})
  (componentWillMount [this]
    #?(:cljs
       (let [channel (om/props this)
             fb (shared/by-key this :shared/firebase)
             presence-ref (firebase/-ref fb (str "visitors/" (-> channel :stream/store :db/id)))]
         (debug "Reference for path: " (str "visitors/" (-> channel :stream/store :db/id)))
         (firebase/-once fb (fn [snapshot]
                              (debug "Got presence value: " snapshot)
                              (let [visitors (count (or (:value snapshot) []))]
                                (om/update-state! this assoc :visitor-count visitors)))
                         presence-ref)
         (om/update-state! this assoc :presence-ref presence-ref))))
  (componentWillUnmount [this]
    #?(:cljs
       (let [{:keys [presence-ref]} (om/get-state this)]
         (firebase/-off (shared/by-key this :shared/firebase) presence-ref))))
  (render [this]
    (let [channel (om/props this)
          {:keys [visitor-count]} (om/get-state this)
          {:stream/keys [store]
           stream-name  :stream/title} channel
          {{:store.profile/keys [photo]
            store-name          :store.profile/name} :store/profile} store
          store-link (routes/store-url store :store)]
      (debug "Stream: " channel)
      (debug "Stream store: " (:stream/state channel))
      (dom/div
        (cond->> (css/add-classes [:content-item :stream-item])
                 (pos? visitor-count)
                 (css/add-class :show-visitor-count))
        (dom/a
          {:href store-link}
          (photo/stream-photo store
                              nil
                              ;(when (pos? visitor-count))
                              (dom/div
                                (css/add-class :visitor-count)
                                (dom/i {:classes ["fa fa-user"]})
                                (dom/span nil (str visitor-count)))
                              ))
        ;(dom/div
        ;  (->> (css/add-class :text)
        ;       (css/add-class :header))
        ;  (dom/a {:href store-link}
        ;         (dom/span nil stream-name)))


        (dom/div
          (css/add-classes [:text :is-live])

          (dom/a {:href store-link}
                 (dom/strong nil store-name))
          )))))

(def ->OnlineChannel (om/factory OnlineChannel))

(defui StoreItem
  static om/IQuery
  (query [this]
    [:db/id
     {:store/profile [:store.profile/name
                      {:store.profile/photo [:photo/path :photo/id]}]}
     {:store/locality [:sulo-locality/path]}
     {:store/status [:status/type]}
     {:stream/_store [:stream/state]}
     :store/username
     :store/created-at
     :store/featured
     :store/featured-img-src
     {:store/owners [:store.owner/user]}
     {:store/items [:db/id {:store.item/photos [{:store.item.photo/photo [:photo/path :photo/id]}
                                                :store.item.photo/index]}]}])
  Object
  (initLocalState [this]
    {:visitor-count 0})
  (componentWillMount [this]
    #?(:cljs
       (let [store (om/props this)
             fb (shared/by-key this :shared/firebase)
             presence-ref (firebase/-ref fb "presence")
             visitors-ref (firebase/-ref fb (str "visitors/" (:db/id store)))
             stream-state (-> store :stream/_store first :stream/state)
             store-owner (-> store :store/owners :store.owner/user)]
         (firebase/-once fb (fn [snapshot]
                              (debug "Got presence value: " snapshot)
                              (let [visitors (count (or (:value snapshot) []))]
                                (om/update-state! this assoc :visitor-count visitors)))
                         visitors-ref)
         (if (= :stream.state/live stream-state)
           (om/update-state! this assoc :store-live? true)
           (firebase/-once fb (fn [snapshot]
                                (let [is-online? (= true (get (:value snapshot) (str (:db/id store-owner))))]
                                  (om/update-state! this assoc :store-online? is-online?)))
                           presence-ref))
         (om/update-state! this assoc :visitors-ref visitors-ref :presence-ref presence-ref))))

  (componentWillUnmount [this]
    #?(:cljs
       (let [{:keys [presence-ref]} (om/get-state this)]
         (firebase/-off (shared/by-key this :shared/firebase) presence-ref))))
  (render [this]
    (let [store (om/props this)
          {:keys [store-online? store-live? visitor-count]} (om/get-state this)
          store-name (-> store :store/profile :store.profile/name)
          online-status (cond store-live? :is-live store-online? :is-online :else :is-offline)]
      (dom/div
        (->> (css/add-class :content-item)
             (css/add-class :store-item))
        (dom/a
          {:href (routes/store-url store :store)}
          (photo/store-photo store {:transformation :transformation/thumbnail-large}
                             (when (pos? visitor-count)
                               (photo/overlay
                                 nil
                                 (dom/div
                                   (css/add-class :visitor-count)
                                   (dom/i {:classes ["fa fa-user"]})
                                   (dom/span nil (str visitor-count)))))))
        (dom/div
          (css/add-classes [:text :header online-status])
          (dom/a {:href (routes/store-url store :store)}
                 (dom/strong nil store-name)))))))

(def ->StoreItem (om/factory StoreItem))



(defn show-status [current-route]
  (not (contains? #{:store} (:route current-route))))

(defui ProductItem
  static om/IQuery
  (query [_]
    (product/product-query))
  Object
  (componentWillMount [this]
    #?(:cljs
       (let [fb (shared/by-key this :shared/firebase)
             product (om/props this)
             {:keys [current-route]} (om/get-computed this)
             store-owner (-> product :store/_items :store/owners :store.owner/user)
             stream-state (-> product :store/_items :stream/_store first :stream/state)
             presence-ref (firebase/-ref fb "presence")]
         (if (and (= :stream.state/live stream-state)
                  (show-status current-route))
           (om/update-state! this assoc :store-live? true)
           (firebase/-once fb (fn [snapshot]
                                (let [is-online? (and (= true (get (:value snapshot) (str (:db/id store-owner))))
                                                      (show-status current-route))]
                                  (om/update-state! this assoc :store-online? is-online?)))
                           presence-ref)))))
  (render [this]
    (let [product (om/props this)
          {:keys [current-route open-url? store-status]} (om/get-computed this)
          {:keys [show-item? store-online? store-live?]} (om/get-state this)
          on-click #(om/update-state! this assoc :show-item? true)

          goods-href (when (or open-url? (nil? on-click)) (product/product-url product))
          on-click (when-not open-url? on-click)
          {:store.item/keys [photos price]
           item-name        :store.item/name
           store            :store/_items} product
          {:store.item.photo/keys [photo]} (first (sort-by :store.item.photo/index photos))]
      (dom/div
        (cond->> (css/add-classes [:content-item :product-item])
                 (= store-status :online)
                 (css/add-class :is-online)
                 (= store-status :live)
                 (css/add-class :is-live))
        (when show-item?
          (common/modal
            {:on-close #(do
                         (om/update-state! this assoc :show-item? false)
                         #?(:cljs
                            (.replaceState js/history nil nil (routes/url (:route current-route)
                                                                          (:route-params current-route)
                                                                          (:query-params current-route)))))
             :size     :large}
            (product/->Product product)))

        (dom/a
          (->> {:onClick #(when on-click
                           (on-click)
                           (debug "Chagne URL to: " goods-href)
                           #?(:cljs
                              (.replaceState js/history nil nil (product/product-url product))))

                :href    goods-href}
               (css/add-class :primary-photo))
          (photo/product-preview product nil))

        (dom/div
          (css/add-classes [:header :text])
          (dom/a {:onClick on-click
                  :href    goods-href}
                 (dom/span nil item-name)))
        (dom/a
          (css/add-classes [:text :store-name :sl-tooltip] {:href (routes/store-url store :store)})

          (dom/small
            nil
            (str "by " (:store.profile/name (:store/profile store))))
          (when (#{:live :online} store-status)
            (dom/small (css/add-class :sl-tooltip-text)
                       (str (:store.profile/name (:store/profile store))
                            " is " (name store-status) " right now, say hi in their store."))))

        (dom/div
          (css/add-class :text)
          (dom/strong nil (ui-utils/two-decimal-price price)))))))

(def ->ProductItem (om/factory ProductItem))