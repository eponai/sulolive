(ns eponai.client.history
  (:require [bidi.bidi :as bidi]
            [eponai.client.routes :as routes]
            [om.next :as om]
            [pushy.core :as pushy]))

(defonce history-atom (atom nil))

(defn url-handler-form-token []
  (->> @history-atom
       pushy/get-token
       (bidi/match-route routes/routes)
       :handler))

(defn url-query-params [url-handler]
  {:pre [(instance? routes/UiComponentMatch url-handler)]}
  (let [{:keys [component factory]} url-handler]
    {:url/component (om/get-query component)
     :url/factory   {:value factory}}))

(defn set-page! [reconciler]
  (fn [{:keys [handler route-params] :as match}]
    (when route-params
      (routes/handle-route-params handler route-params reconciler))
    (om/set-query! (om/app-root reconciler)
                   {:params (url-query-params handler)}
                   [:proxy/app-content])))

(defn init-history [reconciler]
  (when-let [h @history-atom]
    (pushy/stop! h))

  (let [history (pushy/pushy (set-page! reconciler)
                             (partial bidi/match-route routes/routes))]
    (reset! history-atom history)))

(defn start! [history]
  (pushy/start! history))
