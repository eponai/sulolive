(ns eponai.web.ui.notifications
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.format.date :as date]))

(defn save-notification [component {:keys [key ref value] :as n}]
  #?(:cljs
     (let [notification {:notification/id        key
                         :notification/ref ref
                         :notification/timestamp (:timestamp value)
                         :notification/payload   {:payload/title    (:title value)
                                                  :payload/body     (:message value)
                                                  :payload/subtitle (:subtitle value)}}]
       (om/update-state! component update :notifications (fn [ns]
                                                           (let [tot (count ns)]
                                                             (cond-> (assoc ns (:timestamp value) notification)
                                                                     (<= 5 tot)
                                                                     (dissoc (apply min (keys ns))))))))))

(defn remove-notification [component timestamp]
  (om/update-state! component update :notifications dissoc timestamp))

(defui Notifications
  static om/IQuery
  (query [_]
    [:query/current-route
     {:query/auth [:db/id]}])
  Object
  (initLocalState [this]
    {:notifications (sorted-map)})
  (componentWillUnmount [this]
    #?(:cljs
       (let [{:query/keys [auth]} (om/props this)]
         (-> (.database js/firebase)
             (.ref (str "server/sulolive/notifications/" (:db/id auth)))
             (.off)))))

  (componentWillMount [this]
    #?(:cljs
       (let [{:query/keys [auth]} (om/props this)
             notifications-ref (-> (.database js/firebase)
                                   (.ref (str "server/sulolive/notifications/" (:db/id auth))))]

         (.on notifications-ref "child_added" (fn [snapshot]
                                                (save-notification this {:key   (.-key snapshot)
                                                                         :ref   (.-ref snapshot)
                                                                         :value (js->clj (.val snapshot) :keywordize-keys true)})))

         (.on notifications-ref "child_removed" (fn [snapshot]
                                                  (let [v (js->clj (.val snapshot) :keywordize-keys true)]
                                                    (remove-notification this (:timestamp v))))))))

  (render [this]
    (let [{:keys [notifications]} (om/get-state this)]
      (dom/div
        {:id "sulo-notifications"}
        (map (fn [[timestamp {:notification/keys [payload id ref]}]]
               (let [{:payload/keys [title subtitle body]} payload]
                 (callout/callout
                   (css/add-classes [:sulo :sulo-notification])
                   (dom/span (css/add-classes [:icon :icon-chat]))
                   (dom/p nil
                          (dom/strong nil title)
                          (dom/br nil)
                          (dom/strong nil (dom/small nil subtitle))
                          (dom/br nil)
                          (dom/small nil body))
                   (dom/a (css/add-class :close-button {:onClick #(.remove ref)}) "x"))))
             notifications)))))

(def ->Notifications (om/factory Notifications))