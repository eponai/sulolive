(ns eponai.common.ui.store
  (:require
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.chat :as chat]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.product :as item]
    [eponai.common.ui.stream :as stream]
    [eponai.common.ui.om-quill :as quill]
    [eponai.common.ui.router :as router]
    [eponai.common.format :as f]
    [eponai.client.routes :as routes]
    [eponai.common.ui.dom :as dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.web.ui.photo :as photo]
    [eponai.web.social :as social]
    [eponai.common.photos :as photos]
    [eponai.common.mixpanel :as mixpanel]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.web.ui.button :as button]
    #?(:cljs [eponai.web.firebase :as firebase])
    [eponai.common.shared :as shared]
    [eponai.common.ui.product :as product]
    [eponai.common.database :as db]
    [eponai.client.auth :as client.auth]
    [eponai.web.ui.content-item :as ci]))


(defn about-section [component]
  (let [{:query/keys [store]} (om/props component)
        {:store.profile/keys [description name]} (:store/profile store)]
    (grid/row-column
      nil
      (dom/div
        (css/callout)
        (quill/->QuillRenderer {:html (f/bytes->str description)})))))

(defn policies-section [component]
  (let [{:query/keys [store]} (om/props component)
        {:store.profile/keys [return-policy]} (:store/profile store)
        {shipping-policy :shipping/policy} (:store/shipping store)]
    (grid/row-column
      nil
      (dom/div
        (css/callout)
        (dom/p nil (dom/strong nil "Returns"))
        (quill/->QuillRenderer {:html (f/bytes->str return-policy)}))
      (dom/div
        (css/callout)
        (dom/p nil (dom/strong nil "Shipping"))
        (quill/->QuillRenderer {:html (f/bytes->str shipping-policy)})))))

(defn store-not-found [component]
  (let [{:query/keys [store featured-stores locations]} (om/props component)]
    [
     (grid/row-column
       (css/add-class :store-not-found (css/text-align :center))
       (dom/div (css/add-classes [:not-found-code])
                (dom/p (css/add-class :stat) "404"))
       (dom/h1 nil "Store not found")
       (dom/div (css/add-class :empty-container)
                (dom/p (css/add-class :shoutout) "Oops, that store doesn't seem to exist."))
       (button/store-navigation-default
         {:href (routes/url :live {:locality (:sulo-locality/path locations)})}
         (dom/span nil "Browse stores")))
     (grid/row-column
       nil
       (dom/hr nil)
       (dom/div
         (css/add-class :section-title)
         (dom/h3 nil "New stores")))
     (grid/row
       (grid/columns-in-row {:small 2 :medium 5})
       (map (fn [store]
              (let [store-name (get-in store [:store/profile :store.profile/name])]
                (grid/column
                  nil
                  (dom/div
                    (->> (css/add-class :content-item)
                         (css/add-class :stream-item))
                    (dom/a
                      {:href (routes/url :store {:store-id (:db/id store)})}
                      (photo/store-photo store {:transformation :transformation/thumbnail-large}))
                    (dom/div
                      (->> (css/add-class :text)
                           (css/add-class :header))
                      (dom/a {:href (routes/url :store {:store-id (:db/id store)})}
                             (dom/strong nil store-name)))))))
            (take 5 featured-stores)))
     ;(grid/row-column
     ;     nil
     ;     (dom/hr nil)
     ;     (dom/div
     ;       (css/add-class :section-title)
     ;       (dom/h3 nil "New arrivals")))
     ;(grid/row
     ;  (->>
     ;    (grid/columns-in-row {:small 2 :medium 3 :large 6}))
     ;  (map
     ;    (fn [p]
     ;      (grid/column
     ;        (css/add-class :new-arrival-item)
     ;        (pi/product-element {:open-url? true} p)))
     ;    (take 6 featured-items)))
     ]))

(defn store-url [store-id]
  #?(:cljs (str js/window.location.origin (routes/url :store {:store-id store-id}))
     :clj  nil))
(defui Store
  static om/IQuery
  (query [_]
    [
     ;{:proxy/stream (om/get-query stream/Stream)}
     ;{:query/stream [:stream/state]}
     {:proxy/chat (om/get-query chat/StreamChat)}
     {:query/store [:db/id
                    {:store/locality [:sulo-locality/path]}
                    {:store/sections [:store.section/label :store.section/path :db/id]}
                    :store/visitor-count
                    :store/username
                    ;{:store/items (om/get-query item/Product)}
                    :store/not-found?
                    {:store/status [:status/type]}
                    {:stream/_store [:stream/state :stream/title]}
                    {:store/profile [:store.profile/name
                                     :store.profile/description
                                     :store.profile/tagline
                                     :store.profile/return-policy
                                     {:store.profile/photo [:photo/path :photo/id]}
                                     {:store.profile/cover [:photo/path :photo/id]}]}
                    {:store/owners [{:store.owner/user [:user/online?]}]}
                    {:store/shipping [:shipping/policy]}]}
     {:query/featured-items [:db/id
                             :store.item/name
                             :store.item/price
                             :store.item/created-at
                             {:store.item/photos [{:store.item.photo/photo [:photo/path :photo/id]}
                                                  :store.item.photo/index]}
                             {:store/_items [{:store/profile [:store.profile/name]}]}]}
     {:query/featured-stores [:db/id
                              {:store/profile [:store.profile/name
                                               {:store.profile/photo [:photo/path :photo/id]}]}
                              :store/created-at
                              :store/featured
                              {:store/items [:db/id {:store.item/photos [{:store.item.photo/photo [:photo/path :photo/id]}
                                                                         :store.item.photo/index]}]}]}
     {:query/store-items (om/get-query ci/ProductItem)}
     :query/locations
     :query/current-route])
  Object
  (initLocalState [this]
    {:selected-navigation :all-items})
  ;(componentDidMount [this]
  ;  #?(:cljs
  ;     (let [{:query/keys [store]} (om/props this)]
  ;       (firebase/register-store-visit
  ;         (shared/by-key this :shared/firebase)
  ;         (:db/id store)
  ;         (client.auth/current-auth this)
  ;         (get-in store [:store/locality :sulo-locality/path])))))
  ;(componentWillUnmount [this]
  ;  #?(:cljs
  ;     (let [{:query/keys [store]} (om/props this)]
  ;       (firebase/unregister-store-visit (shared/by-key this :shared/firebase)
  ;                                        (:db/id store)
  ;                                        (client.auth/current-auth this)))))
  (render [this]
    (let [{:keys [fullscreen? selected-navigation] :as st} (om/get-state this)
          {:query/keys [store store-items current-route] :as props} (om/props this)
          {:store/keys [profile visitor-count owners]
           stream      :stream/_store} store
          {:store.profile/keys [photo cover tagline description]
           store-name          :store.profile/name} profile
          stream (first stream)
          is-live? (= :stream.state/live (:stream/state stream))
          show-chat? (:show-chat? st true)
          {:keys [route route-params]} current-route
          store-status (get-in store [:store/status :status/type])
          store-owner-online? (-> owners :store.owner/user :user/online?)]

      (dom/div
        {:id "sulo-store"}

        (if (:store/not-found? store)
          (store-not-found this)
          [
           (dom/h1 (css/show-for-sr) store-name)
           (when (or (nil? store-status)
                     (= :status.type/closed store-status))
             (callout/callout-small
               (->> (css/text-align :center {:id "store-closed-banner"})
                    (css/add-classes [:alert :store-closed]))
               (dom/div (css/add-class :sl-tooltip)
                        (dom/h3
                          (css/add-class :closed)
                          (dom/strong nil "Closed - "))
                        (dom/span (css/add-class :sl-tooltip-text)
                                  "Only you can see your store. Customers who try to view your store will see a not found page."))
               (dom/a {:href (routes/url :store-dashboard/profile#options route-params)}
                      (dom/span nil "Go to options"))))
           (grid/row
             (->> (grid/columns-in-row {:small 1})
                  (css/add-class :collapse)
                  (css/add-class :expanded))
             (grid/column
               (grid/column-order {:small 3 :medium 1})
               (dom/div
                 (cond->> (css/add-class :stream-container)
                          show-chat?
                          (css/add-class :sulo-show-chat)
                          fullscreen?
                          (css/add-class :fullscreen))
                 (cond
                   is-live?
                   (stream/->Stream (om/computed {:stream stream}
                                                 {:stream-title         (:stream/title stream)
                                                  :widescreen?          true
                                                  :store                store
                                                  :on-fullscreen-change #(om/update-state! this assoc :fullscreen? %)}))
                   (some? cover)
                   (photo/store-cover store nil))

                 (chat/->StreamChat (om/computed (:proxy/chat props)
                                                 {:on-toggle-chat      (fn [show?]
                                                                         (om/update-state! this assoc :show-chat? show?))
                                                  :store               store
                                                  :stream-overlay?     true
                                                  :visitor-count       visitor-count
                                                  :show?               show-chat?
                                                  :store-online-status store-owner-online?}))))




             (grid/column
               (->> (grid/column-order {:small 1 :medium 3})
                    (css/add-class :store-container))

               (grid/row
                 (->> (css/align :middle)
                      (css/align :center))

                 (grid/column
                   (grid/column-size {:small 12 :medium 2})
                   (photo/store-photo store {:transformation :transformation/thumbnail}))

                 (grid/column
                   (css/add-class :shrink)
                   (dom/div (css/add-class :store-name) (dom/strong nil store-name))
                   (dom/p (css/add-class :tagline)
                          (dom/span nil tagline)))
                 (grid/column
                   (->> (grid/column-size {:small 12 :medium 4 :large 3})
                        (css/text-align :center)
                        (css/add-class :follow-section))
                   (dom/div nil
                            (common/follow-button nil)
                            ;(common/contact-button nil)
                            )))
               ))

           (grid/row
             (css/add-class :collapse)
             (grid/column
               (css/add-class :store-submenu)

               ;(dom/h3 (css/add-class :sl-tooltip)
               ;        (dom/span
               ;          (cond->> (css/add-classes [:label ])
               ;                   (= stream-state :stream.state/offline)
               ;                   (css/add-class :primary)
               ;                   (= stream-state :stream.state/online)
               ;                   (css/add-class :success)
               ;                   (= stream-state :stream.state/live)
               ;                   (css/add-class :highlight))
               ;          (name stream-state))
               ;        (when (= stream-state :stream.state/offline)
               ;          (dom/span (css/add-class :sl-tooltip-text)
               ;                    "See the help checklist below to get started streaming")))
               (dom/div
                 (css/add-class :store-stats)
                 (dom/div (css/add-class :sl-tooltip)
                          (dom/h6 nil
                                  (dom/small nil "Active visitors: ")
                                  (dom/span nil (str visitor-count)))
                          (dom/span (css/add-class :sl-tooltip-text) "Visitors in store right now")))
               (let [store-url (store-url (:store-id route-params))]
                 (menu/horizontal
                   (->> (css/align :right)
                        (css/add-class :share-menu))
                   (menu/item
                     nil
                     (social/share-button {:on-click #(mixpanel/track "Share on social media" {:platform "facebook"
                                                                                               :object   "store"})
                                           :platform :social/facebook
                                           :href     store-url}))
                   (menu/item
                     nil
                     (social/share-button {:on-click    #(mixpanel/track "Share on social media" {:platform "twitter"
                                                                                                  :object   "store"})
                                           :platform    :social/twitter
                                           :description (:store.profile/name profile)
                                           :href        store-url}))
                   (menu/item
                     nil
                     (social/share-button {:on-click    #(mixpanel/track "Share on social media" {:platform "pinterest"
                                                                                                  :object   "store"})
                                           :platform    :social/pinterest
                                           :href        store-url
                                           :description (:store.profile/name profile)
                                           :media       (photos/transform (:photo/id (:store.profile/photo profile))
                                                                          :transformation/thumbnail)}))
                   ;(menu/item
                   ;  {:title "Share on email"}
                   ;  (social/share-button nil {:platform :social/email}))
                   ))))

           (dom/div
             {:id "shop"}
             (grid/row
               (->> (css/add-class :collapse)
                    (css/add-class :menu-container))
               (grid/column
                 nil
                 (menu/horizontal
                   (css/add-class :navigation)

                   (menu/item (cond->> (css/add-class :about)
                                       (= selected-navigation :about)
                                       (css/add-class ::css/is-active))
                              (dom/a {:onClick #(om/update-state! this assoc :selected-navigation :about)}
                                     (dom/span nil "About")))
                   (menu/item (cond->> (css/add-class :about)
                                       (= selected-navigation :policies)
                                       (css/add-class ::css/is-active))
                              (dom/a {:onClick #(om/update-state! this assoc :selected-navigation :policies)}
                                     (dom/span nil "Policies")))
                   (menu/item (when (and (= route :store) (= selected-navigation :all-items))
                                (css/add-class ::css/is-active))
                              (dom/a {:onClick #(om/update-state! this assoc :selected-navigation :all-items)}
                                     (dom/span nil "All Items")))
                   (map-indexed
                     (fn [i s]
                       (let [{:store.section/keys [label]} s
                             is-active? (and (= route :store) (= selected-navigation (:db/id s)))]
                         (menu/item
                           (cond->> {:key (+ 10 i)}
                                    is-active?
                                    (css/add-class ::css/is-active))
                           (dom/a
                             {:onClick #(om/update-state! this assoc :selected-navigation (:db/id s))
                              :href    (routes/url :store route-params)}
                             (dom/span nil label)))))
                     (:store/sections store)))))
             (cond (= selected-navigation :about)
                   (about-section this)
                   (= selected-navigation :policies)
                   (policies-section this)
                   :else
                   (let [products (sort-by :store.item/index
                                           (if (and (= route :store) (number? selected-navigation))
                                             (filter #(= (get-in % [:store.item/section :db/id]) selected-navigation) store-items)
                                             store-items))]
                     (grid/products products
                                    (fn [p]
                                      (ci/->ProductItem (om/computed p
                                                                     {:current-route current-route})))))))])))))

(def ->Store (om/factory Store))

(router/register-component :store Store)