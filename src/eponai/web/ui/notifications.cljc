(ns eponai.web.ui.notifications
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [taoensso.timbre :refer [debug error]]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.format.date :as date]
    [eponai.client.routes :as routes]
    [eponai.common.shared :as shared]
    #?(:cljs
       [eponai.web.utils :as web-utils])
    #?(:cljs
       [eponai.web.firebase :as firebase])
    [eponai.common.ui.elements.menu :as menu]))

(def form-inputs
  {::dropdown "sulo-notification-dropdown"})

(defn is-watching-owned-store? [current-route owned-store]
  (let [{:keys [route-params route]} current-route]
    (debug "Route: " route)
    (debug "Store id: " (:store-id route-params))
    (debug "Owned store: " (select-keys owned-store [:db/id :store/username]))
    (debug "Contains store route: " (contains? #{:store :store-dashboard/stream} route))
    (debug "Contains store-id " (contains? #{(str (:db/id owned-store)) (:store/username owned-store)} (:store-id route-params)))
    (and (contains? #{:store :store-dashboard/stream} route)
         (contains? #{(str (:db/id owned-store)) (:store/username owned-store)} (:store-id route-params)))))

(defn icon-for-type [type]
  (cond (= type "new-order")
        (dom/i {:classes ["fa fa-file-text-o fa-fw"]})))

(defn notifications-dropdown [component]
  (let [{:query/keys [notifications]} (om/props component)
        {:keys [dropdown-key]} (om/get-state component)]
    (dom/div
      (cond->> (css/add-class :dropdown-pane)
               (= dropdown-key :dropdown/notifications)
               (css/add-class :is-open))
      (menu/vertical
        nil
        (menu/vertical
          (css/add-class :nested)
          (if (first notifications)
            (map (fn [{:user.notification/keys [id value]}]
                   (menu/item
                     (css/add-class :notification-content)
                     (dom/a {:href (:click-action value)
                             :onClick #(.read-notification component id)}
                            (dom/span nil
                                      (icon-for-type (:type value))
                                      (dom/span nil (dom/strong nil (:title value)))
                                      (dom/br nil)
                                      (dom/small nil (:message value)))
                            (dom/i {:classes ["fa fa-chevron-right"]}))))
                 notifications)
            (menu/item
              (css/add-class :notification-content)
              (dom/p (css/add-class :empty)
                     (dom/span nil "You have no notifications")))))))))

(defn chat-notifications [component]
  (let [{:query/keys [notification-count]} (om/props component)
        notification-count (or notification-count 0)]
    (dom/div
      (css/add-class :sulo-notification)
      (dom/i
        (cond->> {:classes ["fa fa-comments"]}
                 (pos? notification-count)
                 (css/add-class :is-active)))
      (when (pos? notification-count)
        (dom/span (css/add-classes [:alert :badge]) notification-count)))))

(defn render-notifications [component]
  (let [{:query/keys [notifications]} (om/props component)
        notification-count (count notifications)]
    (dom/div
      (css/add-class :sulo-notification)
      (dom/i
        (cond->> {:classes ["fa fa-bell"]}
                 (pos? notification-count)
                 (css/add-class :is-active)))
      (when (pos? notification-count)
        (dom/span (css/add-classes [:alert :badge]) notification-count)))))

(defui Notifications
  static om/IQuery
  (query [_]
    [:query/current-route
     :query/firebase
     {:query/auth [:db/id]}
     {:query/owned-store [:db/id :store/username]}
     :query/notification-count
     {:query/notifications [:user.notification/id
                            :user.notification/value]}])
  Object
  (open-dropdown
    [this dd-key]
  #?(:cljs
     (let [{:keys [on-click-event-fn]} (om/get-state this)]
       (om/update-state! this assoc :dropdown-key dd-key)
       (.addEventListener js/document "click" on-click-event-fn))))

  (close-dropdown
    [this event]
  #?(:cljs
     (let [{:keys [dropdown-key on-click-event-fn]} (om/get-state this)
           id (::dropdown form-inputs)]
       (debug "Clicked: " event)
       (when-not (= (.-id (.-target event)) id)
         (om/update-state! this dissoc :dropdown-key)
         (.removeEventListener js/document "click" on-click-event-fn)))))

  (read-notifications [this new-notification]
    #?(:cljs
       (let [{:query/keys [current-route owned-store]} (om/props this)
             {:keys [type]} (om/get-computed this)]
         (if (= type :notification.type/chat)
           (do
             (when (is-watching-owned-store? current-route owned-store)
               (web-utils/scroll-to (web-utils/element-by-id "sulo-store") 250))
             (firebase/read-chat-notifications (shared/by-key this :shared/firebase)
                                               (get-in (om/props this) [:query/auth :db/id])))))))

  (read-notification [this notification-id]
    #?(:cljs
       (let [{:query/keys [current-route owned-store]} (om/props this)]
         (firebase/read-notification (shared/by-key this :shared/firebase)
                                     (get-in (om/props this) [:query/auth :db/id])
                                     notification-id))))

  (componentWillReceiveProps [this next-props]
    #?(:cljs
       (let [{:query/keys [current-route owned-store]} next-props
             {:keys [type]} (om/get-computed this)]
         (if (= type :notification.type/chat)
           (when-not (= (:route current-route) (:route (:query/current-route (om/props this))))
             (when (is-watching-owned-store? current-route owned-store)
               (.read-notifications this nil)))))))

  (initLocalState [this]
    {#?@(:cljs [:on-click-event-fn #(.close-dropdown this %)])})

  (render [this]
    (let [{:query/keys [owned-store current-route notifications]} (om/props this)
          {:keys [route route-params]} current-route
          {:keys [type href ]} (om/get-computed this)
          ]
      (debug "Notification route: " current-route)
      (debug "Notifications: " notifications)
      (if (= type :notification.type/chat)
        (menu/item
          (css/add-class :chat-notifications)
          (dom/a
            {:href    href
             :onClick #(.read-notifications this nil)
             :classes ["sulo-notifications" "chat"]}
            (chat-notifications this)))
        (menu/item-dropdown
          (->> {:dropdown (notifications-dropdown this)
                :classes  [:user-photo-item]
                :href     "#"
                :onClick  #(.open-dropdown this :dropdown/notifications)}
               (css/add-class :sulo-notifications))
          (render-notifications this))))))

(def ->Notifications (om/factory Notifications {:keyfn #(get-in % [::om/computed :type])}))