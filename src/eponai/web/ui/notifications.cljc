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
  (let [{:keys [unread]} (om/get-state component)]
    (dom/div
      (css/add-class :sulo-notification)
      (dom/i
        (cond->> {:classes ["fa fa-comments"]}
                 (pos? (count unread))
                 (css/add-class :is-active)))
      (when (pos? (count unread))
        (dom/span (css/add-classes [:alert :badge]) (count unread))))))

(defui Notifications
  static om/IQuery
  (query [_]
    [:query/current-route
     :query/firebase
     {:query/auth [:db/id]}
     {:query/owned-store [:db/id :store/username]}])
  Object
  (read-notifications [this new-notification]
    (let [{:keys [unread unread-ref read-ref]} (om/get-state this)
          fb (shared/by-key this :shared/firebase)]

      #?(:cljs
         (doseq [v (cond-> (vec (vals unread))
                           (some? new-notification)
                           (conj new-notification))]
           (debug "Remove v: " v)
           (firebase/-remove fb (:ref v))
           (debug "Push read: " read-ref)
           (let [new-read-ref (firebase/-push fb read-ref)]
             (firebase/-set fb new-read-ref (clj->js (:value v))))))))

  (initLocalState [this]
    {:notifications (sorted-map)})
  (componentWillUnmount [this]
    #?(:cljs
       (let [{:query/keys [auth]} (om/props this)
             {:keys [unread-ref read-ref]} (om/get-state this)
             fb (shared/by-key this :shared/firebase)]
         (when unread-ref
           (firebase/-off fb unread-ref))
         (when unread-ref
           (firebase/-off fb read-ref)))))

  (componentWillMount [this]
    #?(:cljs
       (let [{:query/keys [auth current-route owned-store]
              fb-token    :query/firebase} (om/props this)

             fb (shared/by-key this :shared/firebase)]

         (when (some? owned-store)
           (let [unread-ref (->> (firebase/-ref fb (str (str "notifications/" (:db/id auth) "/unread/chat")))
                                 (firebase/-limit-to-last fb 100))
                 read-ref (firebase/-ref fb (str (str "notifications/" (:db/id auth) "/read/chat")))]
             (firebase/-on-child-added fb
                                       (fn [{:keys [key ref value] :as n}]
                                         (let [{:query/keys [current-route owned-store]} (om/props this)
                                               {:keys [route route-params]} current-route]
                                           (debug "FIREBASE - added value: " value)
                                           ;(if (is-watching-owned-store? current-route owned-store)
                                           ;  (.read-notifications this n))
                                           (save-notification this {:key   key
                                                                    :ref   ref
                                                                    :value value})))
                                       unread-ref)

             (firebase/-on-child-removed fb
                                         (fn [{:keys [value]}]
                                           (debug "FIREBASE - removed value: " value)
                                           (om/update-state! this update :unread dissoc (:timestamp value)))
                                         unread-ref)
             (om/update-state! this assoc :unread-ref unread-ref :read-ref read-ref))))))

  (componentWillReceiveProps [this next-props]
    (let [{:query/keys [current-route owned-store auth]} next-props
          {:keys [unread-ref]} (om/get-state this)
          fb (shared/by-key this :shared/firebase)]
      ;(debug "NOtifications:  is on owned store: " (is-watching-owned-store? next-props) {:new current-route
      ;                                                                                    :old (:query/current-route (om/props this))})
      #?(:cljs
         (when-not (= (:route current-route) (:route (:query/current-route (om/props this))))
           (when (is-watching-owned-store? current-route owned-store)
             (.read-notifications this nil))))))

  (render [this]
    (let [{:query/keys [owned-store current-route]} (om/props this)
          {:keys [notifications]} (om/get-state this)
          {:keys [route route-params]} current-route
          {:keys [type href]} (om/get-computed this)
          fb (shared/by-key this :shared/firebase)
          remove-ref (fn [r] (debug "FIREBASE - remove ref")
                       #?(:cljs (firebase/-remove fb r)))]
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