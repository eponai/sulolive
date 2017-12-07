(ns eponai.web.ui.content-item
  (:require
    [om.next :as om :refer [defui]]
    [eponai.client.routes :as routes]
    [eponai.common.analytics.google :as ga]
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
    [eponai.web.ui.button :as button]
    [eponai.web.seo :as seo]
    [eponai.common :as com]
    [eponai.common.format :as common.format]
    [eponai.common.photos :as photos]
    [eponai.common.format.date :as date]
    [eponai.client.videos :as videos]
    [eponai.client.client-env :as client-env]
    [eponai.common :as c]))

(defui StoreVod
  static om/IQuery
  (query [_]
    [:vod/timestamp
     {:vod/store [:db/id
                  :store/username
                  {:store/profile [:store.profile/name
                                   {:store.profile/photo [:photo/path :photo/id]}]}]}])
  Object
  (render [this]
    (let [{:vod/keys [store timestamp]
           :as props} (om/props this)
          {:keys [href]} (om/get-computed this)
          store-link (or href (routes/store-url store :store {} {:vod-timestamp timestamp}))]
      (dom/div
        (css/add-classes [:content-item :stream-item])
        (dom/a
          {:href store-link}
          (photo/vod-photo nil store nil)
          )

        (dom/div
          (css/add-classes [:content-item-text :is-live])

          (dom/a {:href store-link}
                 (dom/span nil (get-in store [:store/profile :store.profile/name]))))
        (when timestamp
          (dom/div
            (css/add-classes [:content-item-text])
            (dom/small nil (date/date->string (c/parse-long-safe timestamp)))))))))

(def ->StoreVod (om/factory StoreVod))

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
          store-link (routes/store-url store :store)
          subscriber-url (client-env/get-key (shared/by-key this :shared/client-env)
                                             :wowza-subscriber-url)]
      (dom/div
        (css/add-classes [:content-item :stream-item])
        (dom/a
          {:href store-link}
          (photo/stream-photo nil store nil))

        (dom/div
          (css/add-classes [:content-item-text :is-live])

          (dom/a {:href store-link}
                 (dom/span nil store-name)))))))

(def ->OnlineChannel (om/factory OnlineChannel))

(defui StoreItem
  static om/IQuery
  (query [this]
    [:db/id
     {:store/profile [:store.profile/name
                      :store.profile/tagline
                      {:store.profile/photo [:photo/path :photo/id]}]}
     {:store/locality [:sulo-locality/path]}
     {:store/status [:status/type]}
     {:stream/_store [:stream/state]}
     :store/visitor-count
     :store/username
     :store/created-at
     :store/featured
     {:store/owners [{:store.owner/user [:user/online?]}]}])
  Object
  (render [this]
    (let [{:store/keys [visitor-count] :as store} (om/props this)
          store-name (-> store :store/profile :store.profile/name)
          store-tagline (-> store :store/profile :store.profile/tagline)
          stream-state (-> store :stream/_store first :stream/state)
          store-owner-online? (true? (-> store :store/owners :store.owner/user :user/online?))
          online-status (cond
                          (= :stream.state/live stream-state) :is-live
                          store-owner-online? :is-online
                          :else :is-offline)]
      (dom/div
        (->> {:itemType (seo/item-type :brand)
              :itemScope true}
             (css/add-class :content-item)
             (css/add-class :store-item))
        (dom/a
          {:href (routes/store-url store :store)}
          (photo/store-photo store {:transformation :transformation/thumbnail-large}
                             (photo/overlay
                               nil
                               (dom/h6 nil "Visit shop"))))
        (dom/div
          (css/add-classes [:content-item-text :header online-status])
          (dom/a {:href (routes/store-url store :store)}
                 (dom/strong {:itemProp "name"} store-name)))
        ))))

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
  (open-modal [this]
    (om/update-state! this assoc :show-item? true)
    #?(:cljs
       (let [item (om/props this)]
         (.replaceState js/history nil nil (product/product-url item))
         (when-let [body (first (web.utils/elements-by-tagname "body"))]
           (web.utils/add-class-to-element body "scroll-disabled")))))

  (close-modal [this]
    (om/update-state! this assoc :show-item? false)
    #?(:cljs
       (let [{:keys [current-route]} (om/get-computed this)]
         (.replaceState js/history nil nil (routes/url (:route current-route)
                                                       (:route-params current-route)
                                                       (:query-params current-route)))
         (when-let [body (first (web.utils/elements-by-tagname "body"))]
           (web.utils/remove-class-to-element body "scroll-disabled")))))


  (on-window-resize [this]
    #?(:cljs (om/update-state! this assoc :breakpoint (web.utils/breakpoint js/window.innerWidth))))
  (componentDidMount [this]
    #?(:cljs
       (.addEventListener js/window "resize" (:resize-listener (om/get-state this)))))
  (componentWillUnmount [this]
    #?(:cljs (.removeEventListener js/window "resize" (:resize-listener (om/get-state this)))))
  (render [this]
    (let [item (om/props this)
          {:keys [current-route open-url? show-caption?]} (om/get-computed this)
          {:keys [show-item? breakpoint]} (om/get-state this)
          #?@(:cljs [open-url? (if (some? open-url?) open-url? (web.utils/bp-compare :large breakpoint >))]
              :clj  [open-url? (if (some? open-url?) open-url? false)])
          on-click (when-not open-url? #(.open-modal this))
          goods-href (when open-url? (product/product-url item))
          {:store.item/keys [photos price]
           item-name        :store.item/name
           store            :store/_items} item
          {:store.item.photo/keys [photo]} (first (sort-by :store.item.photo/index photos))

          store-live? (= :stream.state/live (-> item :store/_items :stream/_store first :stream/state))
          store-online? (true? (-> item :store/_items :store/owners :store.owner/user :user/online?))
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

        (product/product-schema-markup item)
        (when show-item?
          (common/modal
            {:on-close #(.close-modal this)
             :size     :large}
            (product/->Product (om/computed item
                                            {:on-leave #(.close-modal this)}))))

        (dom/a
          (->> {:onClick on-click

                :href    goods-href}
               (css/add-class :primary-photo))
          (photo/product-preview item
                                 nil
                                 (photo/overlay
                                   nil
                                   (dom/h6 nil "View")
                                   )))

        (dom/div
          (css/add-classes [:content-item-text :text-center])
          (dom/a {:onClick on-click
                  :href    goods-href}
                 (dom/span nil item-name)))
        (dom/a
          (cond->> (css/add-classes [:content-item-text :text :sl-tooltip] {:href (routes/store-url store :store)})
                   store-live?
                   (css/add-class :is-live))
          (dom/small
            nil
            (str "by " (:store.profile/name (:store/profile store)))))

        (dom/div
          (css/add-classes [:content-item-text :text])
          (dom/strong nil "$")
          (dom/strong nil (com/two-decimal-number price)))))))

(def ->ProductItem (om/factory ProductItem))