(ns eponai.web.history
  (:require [bidi.bidi :as bidi]
            [eponai.client.route-helper :as route-helper]
            [eponai.web.routes :as routes]
            [eponai.web.routes.ui-handlers :as ui-handlers]
            [eponai.web.ui.all-transactions :refer [AllTransactions]]
            [eponai.web.ui.utils :as utils]
            [eponai.common.parser :as parser]
            [om.next :as om]
            [pushy.core :as pushy]
            [taoensso.timbre :refer-macros [debug error]]))

(defonce history-atom (atom nil))

(defn route-handler->ui-handler [route-handler]
  (get ui-handlers/route-handler->ui-component route-handler))

(defn set-page! [reconciler]
  (fn [{:keys [handler route-params] :as match}]
    (debug "Setting page with match:" match)
    (let [ui-handler (route-handler->ui-handler handler)
          param-mutations (route-helper/route-params-mutations ui-handler route-params)]

      ;; Sets the root/App's app content.
      (om/transact! reconciler (vec (cons `(root/set-app-content {:handler ~ui-handler})
                                          param-mutations))))))

(defn init-history [reconciler]
  (when-let [h @history-atom]
    (pushy/stop! h))
  ;; Do not call dispatch-fn on the initial dispatch, because we have to handle the
  ;; route params before om/add-root!, and we shouldn't set app-root's query twice... or something.
  (let [initial-dispatch (atom false)
        dispatch-fn (set-page! reconciler)
        match-fn (partial bidi/match-route (routes/routes))
        history (pushy/pushy (fn [& args]
                               (try
                                 (if @initial-dispatch
                                   (reset! initial-dispatch false)
                                   (apply dispatch-fn args))
                                    (catch js/Error e
                                      (error "Error when setting page: " e))
                                    (catch :default e
                                      (error "Error when setting page: " e))))
                             match-fn)]
    ;; Set the app state given an url manually, instead of doing this on (pushy/start! history).
    ;;(update-app-state-with-route-match! reconciler (-> (pushy/get-token history) (match-fn)))
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
