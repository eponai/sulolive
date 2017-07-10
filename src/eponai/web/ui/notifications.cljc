(ns eponai.web.ui.notifications
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.format.date :as date]
    [eponai.client.routes :as routes]
    [eponai.common.shared :as shared]
    #?(:cljs
       [eponai.web.firebase :as firebase])))

(defn save-notification [component {:keys [key ref value] :as n}]
  #?(:cljs
     (let [fb (shared/by-key component :shared/firebase)
           {:keys [notifications notifications-ref]} (om/get-state component)
           notification {:notification/id        key
                         :notification/ref       ref
                         :notification/timestamp (:timestamp value)
                         :notification/payload   {:payload/title    (:title value)
                                                  :payload/body     (:message value)
                                                  :payload/subtitle (:subtitle value)}}]
       ;;; If we have more than 5 notifications, remove the rest
       (when (<= 5 (count notifications))
         (let [removes (drop 4 (sort > (keys notifications)))
               updates (reduce (fn [m [_ n]] (assoc m (:notification/id n) nil)) {} (select-keys notifications removes))]
           (when (not-empty updates)
             (firebase/-update fb notifications-ref (clj->js updates)))))
       (om/update-state! component update :notifications (fn [ns] (assoc ns (:timestamp value) notification))))))

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
       (let [{:query/keys [auth]} (om/props this)
             {:keys [notifications-ref]} (om/get-state this)
             fb (shared/by-key this :shared/firebase)]
         (when fb
           (firebase/-off fb notifications-ref)))))

  (componentWillMount [this]
    #?(:cljs
       (let [{:query/keys [auth current-route owned-store]
              fb-token    :query/firebase} (om/props this)
             fb (shared/by-key this :shared/firebase)]
         (when (some? owned-store)
           (let [notifications-ref (firebase/-ref-notifications fb (:db/id auth))]
             (firebase/-on-child-added fb
                                       (fn [{:keys [key ref value]}]
                                         (if (is-watching-owned-store? (om/props this))
                                           (firebase/-remove fb ref)
                                           (save-notification this {:key   key
                                                                    :ref   ref
                                                                    :value value})))
                                       notifications-ref)

             (firebase/-on-child-removed fb
                                         (fn [{:keys [value]}]
                                           (remove-notification this (:timestamp value)))
                                         notifications-ref)
             (om/update-state! this assoc :notifications-ref notifications-ref))))))

  (componentWillReceiveProps [this next-props]
    (let [{:query/keys [current-route auth]} next-props
          {:keys [notifications-ref]} (om/get-state this)
          fb (shared/by-key this :shared/firebase)]
      #?(:cljs
         (when-not (= (:route current-route) (:route (:query/current-route (om/props this))))
           (when (is-watching-owned-store? next-props)
             (firebase/-remove fb notifications-ref))))))

  (render [this]
    (let [{:query/keys [owned-store current-route]} (om/props this)
          {:keys [notifications]} (om/get-state this)
          {:keys [route route-params]} current-route
          fb (shared/by-key this :shared/firebase)
          remove-ref (fn [r] #?(:cljs (firebase/-remove fb r)))]
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
                        :onClick #(remove-ref ref)}
                       (dom/span (css/add-classes [:icon :icon-chat]))
                       (dom/p nil
                              (dom/strong nil title)
                              (dom/br nil)
                              (dom/strong nil (dom/small nil subtitle))
                              (dom/br nil)
                              (dom/small nil body)))
                     (dom/a (css/add-class :close-button {:onClick #(remove-ref ref)}) "x"))))
               notifications))))))

(def ->Notifications (om/factory Notifications))