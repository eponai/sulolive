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
       [eponai.web.firebase :as firebase])))

(defn save-notification [component {:keys [key ref value] :as n}]
  #?(:cljs
     ;(let [notification {:notification/id        key
     ;                    :notification/ref       ref
     ;                    :notification/timestamp (:timestamp value)
     ;                    :notification/type      (keyword "notification.type" (:type value))
     ;                    :notification/payload   {:payload/title    (:title value)
     ;                                             :payload/body     (:message value)
     ;                                             :payload/subtitle (:subtitle value)}}])
     (om/update-state! component update :unread (fn [ns] (assoc ns (:timestamp value) {:key   key
                                                                                       :ref   ref
                                                                                       :value value})))))

(defn is-watching-owned-store? [current-route owned-store]
  (let [{:keys [route-params route]} current-route]
    (debug "Route: " route)
    (debug "Store id: " (:store-id route-params))
    (debug "Owned store: " (select-keys owned-store [:db/id :store/username]))
    (debug "Contains store route: " (contains? #{:store :store-dashboard/stream} route))
    (debug "Contains store-id " (contains? #{(str (:db/id owned-store)) (:store/username owned-store)} (:store-id route-params)))
    (and (contains? #{:store :store-dashboard/stream} route)
         (contains? #{(str (:db/id owned-store)) (:store/username owned-store)} (:store-id route-params)))))

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

(defui Notifications
  static om/IQuery
  (query [_]
    [:query/current-route
     :query/firebase
     {:query/auth [:db/id]}
     {:query/owned-store [:db/id :store/username]}
     :query/notification-count])
  Object
  (read-notifications [this new-notification]
    #?(:cljs
       (firebase/read-chat-notifications (shared/by-key this :shared/firebase)
                                         (get-in (om/props this) [:query/auth :db/id]))))

  (componentWillReceiveProps [this next-props]
    #?(:cljs
       (let [{:query/keys [current-route owned-store]} next-props]
         (when-not (= (:route current-route) (:route (:query/current-route (om/props this))))
           (when (is-watching-owned-store? current-route owned-store)
             (.read-notifications this nil))))))

  (render [this]
    (let [{:query/keys [owned-store current-route]} (om/props this)
          {:keys [route route-params]} current-route
          {:keys [type href]} (om/get-computed this)
          ]
      (debug "Notification route: " current-route)
      (dom/a
        {:href    href
         :onClick #(do
                    #?(:cljs
                       (when (is-watching-owned-store? current-route owned-store)
                         (web-utils/scroll-to (web-utils/element-by-id "sulo-store") 250)))
                    (.read-notifications this nil))
         :classes ["sulo-notifications" (name type)]}
        (cond (= type :notification.type/chat)
              (chat-notifications this)
              ;:else
              ;(when-not (and (some? route) (or (= route :store-dashboard)
              ;                                 (= (namespace route) "store-dashboard")))
              ;  (map (fn [[timestamp {:notification/keys [payload id ref]}]]
              ;         (let [{:payload/keys [title subtitle body]} payload]
              ;           (callout/callout
              ;             (css/add-classes [:sulo :sulo-notification])
              ;             (dom/a
              ;               {:href    (routes/store-url owned-store :store)
              ;                :onClick #(remove-ref ref)}
              ;               (dom/span (css/add-classes [:icon :icon-chat]))
              ;               (dom/p nil
              ;                      (dom/strong nil title)
              ;                      (dom/br nil)
              ;                      (dom/strong nil (dom/small nil subtitle))
              ;                      (dom/br nil)
              ;                      (dom/small nil body)))
              ;             (dom/a (css/add-class :close-button {:onClick #(remove-ref ref)}) "x"))))
              ;       notifications))
              )
        ))))

(def ->Notifications (om/factory Notifications))