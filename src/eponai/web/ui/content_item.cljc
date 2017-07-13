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
    #?(:cljs [eponai.web.utils :as web.utils])
    [eponai.common.ui.common :as common]
    [eponai.common.ui.utils :as ui-utils]
    [eponai.web.ui.button :as button]))

(defui OnlineChannel
  static om/IQuery
  (query [_]
    [:db/id
     :stream/title
     :stream/state
     {:stream/store [:db/id
                     {:store/locality [:sulo-locality/path]}
                     {:store/profile [:store.profile/name
                                      {:store.profile/photo [:photo/path :photo/id]}]}
                     {:store/status [:status/type]}
                     :store/visitor-count
                     :store/username]}])
  Object
  (render [this]
    (let [channel (om/props this)
          {:stream/keys [store]
           stream-name  :stream/title} channel
          {{:store.profile/keys [photo]
            store-name          :store.profile/name} :store/profile} store
          visitor-count (or (:store/visitor-count store) 0)
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
     :store/visitor-count
     :store/username
     :store/online
     :store/created-at
     :store/featured
     :store/featured-img-src
     {:store/owners [{:store.owner/user [:user/online?]}]}
     {:store/items [:db/id {:store.item/photos [{:store.item.photo/photo [:photo/path :photo/id]}
                                                :store.item.photo/index]}]}])
  Object
  (render [this]
    (let [{:store/keys [visitor-count] :as store} (om/props this)
          store-name (-> store :store/profile :store.profile/name)
          stream-state (-> store :stream/_store first :stream/state)
          store-owner-online? (-> store :store/owners :store.owner/user :user/online?)
          online-status (cond
                          (= :stream.state/live stream-state) :is-live
                          store-owner-online? :is-online
                          :else :is-offline)]
      (dom/div
        (->> (css/add-class :content-item)
             (css/add-class :store-item))
        (dom/a
          {:href (routes/store-url store :store)}
          (photo/store-photo store {:transformation :transformation/thumbnail-large}
                             (when (pos? (or visitor-count 0))
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
  (initLocalState [this]
    #?(:cljs
       {:resize-listener #(.on-window-resize this)
        :breakpoint      (web.utils/breakpoint js/window.innerWidth)}))
  (on-window-resize [this]
    #?(:cljs (om/update-state! this assoc :breakpoint (web.utils/breakpoint js/window.innerWidth))))
  (componentDidMount [this]
    #?(:cljs
       (.addEventListener js/window "resize" (:resize-listener (om/get-state this)))))
  (componentWillUnmount [this]
    #?(:cljs (.removeEventListener js/window "resize" (:resize-listener (om/get-state this)))))
  (render [this]
    (let [product (om/props this)
          {:keys [current-route open-url?]} (om/get-computed this)
          {:keys [show-item? breakpoint]} (om/get-state this)
          on-click #(om/update-state! this assoc :show-item? true)
          #?@(:cljs [open-url? (if (some? open-url?) open-url? (web.utils/bp-compare :large breakpoint >))]
              :clj  [open-url? (if (some? open-url?) open-url? false)])
          goods-href (when (or open-url? (nil? on-click)) (product/product-url product))
          on-click (when-not open-url? on-click)
          {:store.item/keys [photos price]
           item-name        :store.item/name
           store            :store/_items} product
          {:store.item.photo/keys [photo]} (first (sort-by :store.item.photo/index photos))

          store-live? (= :stream.state/live (-> product :store/_items :stream/_store first :stream/state))
          store-online? (-> product :store/_items :store/owners :store.owner/user :user/online?)
          store-status (when (show-status current-route)
                         (cond store-live? :live
                               store-online? :online
                               :else :is-offline))]
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