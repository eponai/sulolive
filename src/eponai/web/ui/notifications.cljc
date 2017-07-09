(ns eponai.web.ui.notifications
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.format.date :as date]
    [eponai.client.routes :as routes]))

(defn save-notification [component {:keys [key ref value] :as n}]
  #?(:cljs
     (let [notification {:notification/id        key
                         :notification/ref ref
                         :notification/timestamp (:timestamp value)
                         :notification/payload   {:payload/title    (:title value)
                                                  :payload/body     (:message value)
                                                  :payload/subtitle (:subtitle value)}}]
       (debug "Notification : " notification)
       (om/update-state! component update :notifications (fn [ns]
                                                           (let [tot (count ns)]
                                                             (cond-> (assoc ns (:timestamp value) notification)
                                                                     (<= 5 tot)
                                                                     (dissoc (apply min (keys ns))))))))))

(defn remove-notification [component timestamp]
  (om/update-state! component update :notifications dissoc timestamp))

(defn is-watching-owned-store? [props]
  (let [{:query/keys [auth current-route owned-store]} props
        {:keys [route route-params]} current-route]
    (and (contains? #{:store :store-dashboard/stream} route)
         (contains? #{(str (:db/id owned-store)) (:store/username owned-store)} (:store-id route-params)))))

(defui Notifications
  static om/IQuery
  (query [_]
    [:query/current-route
     :query/firebase
     {:query/auth [:db/id]}
     {:query/owned-store [:db/id :store/username]}])
  Object
  (initLocalState [this]
    {:notifications (sorted-map)})
  (componentWillUnmount [this]
    #?(:cljs
       (let [{:query/keys [auth]} (om/props this)]
         (-> (.database js/firebase)
             (.ref (str "notifications/" (:db/id auth)))
             (.off)))))

  (componentWillMount [this]
    #?(:cljs
       (let [{:query/keys [auth current-route owned-store]
              fb-token :query/firebase} (om/props this)]
         (when (some? owned-store)
           (let [notifications-ref (-> (.database js/firebase)
                                       (.ref (str "notifications/" (:db/id auth))))]

             (.on notifications-ref "child_added" (fn [snapshot]
                                                    (debug "FIREBASE snapsho: " (.val snapshot))

                                                    (if (is-watching-owned-store? (om/props this))
                                                      (.remove (.-ref snapshot))
                                                      (save-notification this {:key   (.-key snapshot)
                                                                               :ref   (.-ref snapshot)
                                                                               :value (js->clj (.val snapshot) :keywordize-keys true)}))))

             (.on notifications-ref "child_removed" (fn [snapshot]
                                                      (let [v (js->clj (.val snapshot) :keywordize-keys true)]
                                                        (remove-notification this (:timestamp v))))))))))

  (componentWillReceiveProps [this next-props]
    (let [{:query/keys [current-route auth]} next-props]
      #?(:cljs
         (when-not (= (:route current-route) (:route (:query/current-route (om/props this))))
           (when (is-watching-owned-store? next-props)
             (-> (.database js/firebase)
                 (.ref (str "notifications/" (:db/id auth)))
                 (.remove)))))))

  (render [this]
    (let [{:query/keys [owned-store current-route]} (om/props this)
          {:keys [notifications]} (om/get-state this)
          {:keys [route route-params]} current-route]
      (debug "Notification route: " current-route)
      (dom/div
        {:id "sulo-notifications"}
        (if (and (some? route) (or (= route :store-dashboard) (= (namespace route) "store-dashboard")))
          (dom/div
            (css/add-class :sulo-notification)
            (dom/span (css/add-classes [:icon :icon-chat]))
            (when (pos? (count notifications))
              (dom/p nil (dom/span (css/add-classes [:alert :badge]) (count notifications)))))
          (map (fn [[timestamp {:notification/keys [payload id ref]}]]
                 (let [{:payload/keys [title subtitle body]} payload]
                   (callout/callout
                     (css/add-classes [:sulo :sulo-notification])
                     (dom/a
                       {:href    (routes/store-url owned-store :store)
                        :onClick #(.remove ref)}
                       (dom/span (css/add-classes [:icon :icon-chat]))
                       (dom/p nil
                              (dom/strong nil title)
                              (dom/br nil)
                              (dom/strong nil (dom/small nil subtitle))
                              (dom/br nil)
                              (dom/small nil body)))
                     (dom/a (css/add-class :close-button {:onClick #(.remove ref)}) "x"))))
               notifications))))))

(def ->Notifications (om/factory Notifications))