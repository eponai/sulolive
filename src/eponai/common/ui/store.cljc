(ns eponai.common.ui.store
  (:require
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.product-item :as pi]
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
    [eponai.common.mixpanel :as mixpanel]))


(defn about-section [component]
  (let [{:query/keys [store]} (om/props component)
        {:store.profile/keys [description name]} (:store/profile store)]
    (grid/row-column
      nil
      (dom/div
        (css/callout)
        (dom/p nil (dom/strong nil (str "About " name)))
        (quill/->QuillRenderer {:html (f/bytes->str description)})))))

(defn policies-section [component]
  (let [{:query/keys [store]} (om/props component)
        {:store.profile/keys [return-policy shipping-policy]} (:store/profile store)]
    (grid/row-column
      nil
      (dom/div
        (css/callout)
        (dom/p nil (dom/strong nil "Shipping"))
        (quill/->QuillRenderer {:html (f/bytes->str shipping-policy)}))
      (dom/div
        (css/callout)
        (dom/p nil (dom/strong nil "Returns"))
        (quill/->QuillRenderer {:html (f/bytes->str return-policy)})))))

(defn store-url [store-id]
  #?(:cljs (str js/window.location.origin (routes/url :store {:store-id store-id}))
     :clj nil))
(defui Store
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:proxy/stream (om/get-query stream/Stream)}
     {:proxy/chat (om/get-query chat/StreamChat)}
     {:query/store [:db/id
                    {:store/sections [:store.section/label :store.section/path :db/id]}
                    ;{:store/items (om/get-query item/Product)}
                    {:stream/_store [:stream/state :stream/title]}
                    {:store/profile [:store.profile/name
                                     :store.profile/description
                                     :store.profile/tagline
                                     :store.profile/return-policy
                                     :store.profile/shipping-policy
                                     {:store.profile/photo [:photo/path :photo/id]}
                                     {:store.profile/cover [:photo/path :photo/id]}]}]}
     {:query/store-items (om/get-query item/Product)}
     :query/current-route])
  Object
  (initLocalState [this]
    {:selected-navigation :all-items})
  (render [this]
    (let [{:keys [fullscreen? selected-navigation] :as st} (om/get-state this)
          {:query/keys [store store-items current-route]
           :proxy/keys [navbar] :as props} (om/props this)
          {:store/keys [profile]
           stream      :stream/_store} store
          {:store.profile/keys [photo cover tagline description]
           store-name          :store.profile/name} profile
          stream (first stream)
          is-live? (= :stream.state/live (:stream/state stream))
          show-chat? (:show-chat? st is-live?)
          {:keys [route route-params]} current-route]
      (common/page-container
        {:navbar navbar
         :id     "sulo-store"}

        (grid/row
          (->> (grid/columns-in-row {:small 1})
               (css/add-class :collapse)
               (css/add-class :expanded))
          (grid/column
            (grid/column-order {:small 2 :medium 1})
            (cond
              is-live?
              (dom/div
                (cond->> (css/add-class :stream-container)
                         show-chat?
                         (css/add-class :sulo-show-chat)
                         fullscreen?
                         (css/add-class :fullscreen))
                (stream/->Stream (om/computed (:proxy/stream props)
                                              {:stream-title         (:stream/title stream)
                                               :widescreen?          true
                                               :store                store
                                               :on-fullscreen-change #(om/update-state! this assoc :fullscreen? %)}))
                (chat/->StreamChat (om/computed (:proxy/chat props)
                                                {:on-toggle-chat  (fn [show?]
                                                                    (om/update-state! this assoc :show-chat? show?))
                                                 :store           store
                                                 :stream-overlay? true
                                                 :show?           is-live?})))
              (some? cover)
              (dom/div
                (css/add-class :stream-container)
                (photo/cover {:photo-id   (:photo/id cover)
                          :transformation :transformation/full})

                (chat/->StreamChat (om/computed (:proxy/chat props)
                                                {:on-toggle-chat  (fn [show?]
                                                                    (om/update-state! this assoc :show-chat? show?))
                                                 :store           store
                                                 :stream-overlay? true
                                                 :show?           is-live?})))))


          (grid/column
            (->> (grid/column-order {:small 1 :medium 2})
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
                         (common/contact-button nil))))
            ))
        (grid/row
          (css/add-class :collapse)
          (grid/column
            nil

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
                  (social/share-button {:on-click #(mixpanel/track "Share on social media" {:platform "twitter"
                                                                                            :object   "store"})
                                        :platform :social/twitter
                                        :description (:store.profile/name profile)
                                        :href     store-url}))
                (menu/item
                  nil
                  (social/share-button {:on-click #(mixpanel/track "Share on social media" {:platform "pinterest"
                                                                                            :object   "store"})
                                        :platform    :social/pinterest
                                        :href        store-url
                                        :description (:store.profile/name profile)
                                        :media       (photos/transform (:photo/id (:store.profile/photo profile))
                                                                       :transformation/full)}))
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
                                   (pi/->ProductItem {:product p}))))))))))

(def ->Store (om/factory Store))

(router/register-component :store Store)