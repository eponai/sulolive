(ns eponai.web.history
  (:require [bidi.bidi :as bidi]
            [eponai.client.route-helper :as route-helper]
            [eponai.web.routes :as routes]
            [eponai.web.routes.ui-handlers :as ui-handlers]
            [eponai.client.ui :refer [update-query-params!]]
            [om.next :as om]
            [pushy.core :as pushy]))

(defonce history-atom (atom nil))

(defn url-handler-form-token []
  (->> @history-atom
       pushy/get-token
       (bidi/match-route routes/routes)
       :handler
       (get ui-handlers/route-handler->ui-component)))

(defn url-query-params [ui-handler]
  {:pre [(instance? route-helper/UiComponentMatch ui-handler)]}
  (let [{:keys [component factory]} ui-handler]
    {:url/component component
     :url/factory   {:value factory}}))

(defn set-page! [reconciler]
  (fn [{:keys [handler route-params]}]
    (let [ui-handler (get ui-handlers/route-handler->ui-component handler)]
      (when route-params
        (route-helper/handle-route-params ui-handler reconciler route-params))
      (update-query-params! (om/app-root reconciler)
                            update
                            merge
                            (url-query-params ui-handler)))))

(defn init-history [reconciler]
  (when-let [h @history-atom]
    (pushy/stop! h))

  (let [history (pushy/pushy (set-page! reconciler)
                             (partial bidi/match-route routes/routes))]
    (reset! history-atom history)))

(defn start! [history]
  (pushy/start! history))
