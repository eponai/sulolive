(ns eponai.web.history
  (:require [bidi.bidi :as bidi]
            [eponai.client.route-helper :as route-helper]
            [eponai.web.routes :as routes]
            [eponai.web.routes.ui-handlers :as ui-handlers]
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
     :url/factory   factory}))

(defn route-handler->ui-handler [route-handler]
  (get ui-handlers/route-handler->ui-component route-handler))

(defn update-app-state-with-route-match! [reconciler {:keys [handler route-params]}]
  (when route-params
    (route-helper/handle-route-params (route-handler->ui-handler handler) reconciler route-params)))

(defn set-page! [reconciler]
  (fn [{:keys [handler] :as match}]
    (update-app-state-with-route-match! reconciler match)
    (om/set-query! (om/app-root reconciler) {:params (url-query-params (route-handler->ui-handler handler))})))

(defn init-history [reconciler]
  (when-let [h @history-atom]
    (pushy/stop! h))
  ;; Do not call dispatch-fn on the initial dispatch, because we have to handle the
  ;; route params before om/add-root!, and we shouldn't set app-root's query twice... or something.
  (let [initial-dispatch (atom true)
        dispatch-fn (set-page! reconciler)
        match-fn (partial bidi/match-route routes/routes)
        history (pushy/pushy (fn [& args] (if @initial-dispatch
                                            (reset! initial-dispatch false)
                                            (apply dispatch-fn args)))
                             match-fn)]
    ;; Set the app state given an url manually, instead of doing this on (pushy/start! history).
    (update-app-state-with-route-match! reconciler (-> (pushy/get-token history) (match-fn)))
    (reset! history-atom history)))

;; Before adding root, we want to set the reconcilers state according to the url.
;; Somehow execute the ui_handlers.

;; We don't want to set the reconciler's page when we're starting history,
;; We want to do what I wrote above this comment. Because setting the page
;; will send to remote.

;; This is hopefully an insane perf improvement.

;; When we're done with this, check out App and Project's queries.

(defn start! [history]
  (pushy/start! history))
