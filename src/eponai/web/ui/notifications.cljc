(ns eponai.web.ui.notifications
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.callout :as callout]))

(defui Notifications
  static om/IQuery
  (query [_]
    [:query/current-route
     {:query/notifications [:notification/id :notification/payload]}])
  Object
  (render [this]
    (let [{:query/keys [notifications]} (om/props this)]
      (debug "Firebase - Got notifications: " notifications)
      (dom/div
        {:id "sulo-notifications"}
        (map (fn [{:notification/keys [payload]}]
               (let [{:payload/keys [title subtitle body]} payload]
                 (callout/callout
                   (css/add-classes [:sulo :sulo-notification])
                   (dom/span (css/add-classes [:icon :icon-chat]))
                   (dom/p nil
                          (dom/strong nil title)
                          (dom/br nil)
                          (dom/strong nil (dom/small nil subtitle))
                          (dom/br nil)
                          (dom/small nil body)))))
             notifications)))))

(def ->Notifications (om/factory Notifications))