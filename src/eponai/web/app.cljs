(ns eponai.web.app
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
    [eponai.web.modules :as modules]
    [eponai.client.utils :as utils]
    [eponai.web.auth :as auth]
    [eponai.common.parser :as parser]
    [eponai.client.parser.read]
    [eponai.client.parser.mutate]
    [eponai.client.backend :as backend]
    [eponai.client.remotes :as remotes]
    [eponai.client.reconciler :as reconciler]
    [eponai.client.chat :as client.chat]
    [eponai.web.chat :as web.chat]
    [goog.dom :as gdom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [error debug warn info]]
    ;; Routing
    [bidi.bidi :as bidi]
    [pushy.core :as pushy]
    [eponai.client.routes :as routes]
    [eponai.common.routes :as common.routes]
    [eponai.common.ui.router :as router]
    [cljs.core.async :as async]))

(defn add-root! [reconciler]
  (binding [parser/*parser-allow-remote* false]
    (om/add-root! reconciler router/Router (gdom/getElement router/dom-app-id))))

(defn update-route-fn [reconciler-atom]
  (fn [{:keys [handler route-params] :as match}]
    (try
      (let [reconciler @reconciler-atom
            modules (get-in reconciler [:config :shared :shared/modules])
            loaded-route? (modules/loaded-route? modules handler)
            allow-remotes? parser/*parser-allow-remote*]
        (routes/transact-route! reconciler handler
                                {:route-params  route-params
                                 :queue?        (and loaded-route?
                                                     (some? (om/app-root reconciler)))
                                 :delayed-queue (when-not loaded-route?
                                                  (fn [queue-cb]
                                                    (modules/require-route!
                                                      modules handler
                                                      (fn []
                                                        (binding [parser/*parser-allow-remote* allow-remotes?]
                                                          (debug "Allow remotes?: " allow-remotes?)
                                                          (if-let [app-root (om/app-root reconciler)]
                                                            (do (debug "Required route: " handler "! Reindexing...")
                                                                (debug "query before reindex: " (om/get-query app-root))
                                                                (add-root! reconciler)
                                                                (debug "query after reindex: " (om/get-query (om/app-root reconciler)))
                                                                (debug "Re indexed! Queuing reads..")
                                                                (queue-cb))
                                                            (debug "No root query, nothing to queue..")))))))}))
      (catch :default e
        (error "Error when transacting route: " e)))))

(defn apply-once [f]
  (let [appliced? (atom false)]
    (fn [x]
      (if @appliced?
        x
        (do (reset! appliced? true)
            (f x))))))


(defn wrap-route-logging [route-matcher]
  (fn [url]
    (let [match (route-matcher url)]
      (if (some? (:handler match))
        (debug "Matched url: " url " to route handler: " (:handler match) " whole match:" match)
        (warn "Could not match url: " url " to any route."))
      match)))

(defn set-current-route! [history update-route-fn]
  (let [match-all-routes (partial bidi/match-route common.routes/routes)
        match (match-all-routes (pushy/get-token history))]
    (update-route-fn match)))

(defonce history-atom (atom nil))
(defonce reconciler-atom (atom nil))

(defn- run [{:keys [auth-lock modules]
             :or   {auth-lock (auth/auth0-lock)}
             :as   run-options}]
  (let [modules (or modules (modules/advanced-compilation-modules router/routes))
        init? (atom false)
        _ (when-let [h @history-atom]
            (pushy/stop! h))
        match-route (partial bidi/match-route (common.routes/without-coming-soon-route common.routes/routes))
        update-route! (update-route-fn reconciler-atom)
        history (pushy/pushy update-route! (wrap-route-logging match-route))
        _ (reset! history-atom history)
        conn (utils/create-conn)
        parser (parser/client-parser)
        remote-config (-> (reconciler/remote-config conn)
                          (update :remote/chat #(remotes/send-with-chat-update-basis-t % reconciler-atom)))
        add-schema-to-query-once (apply-once (fn [q]
                                               {:pre [(sequential? q)]}
                                               (into [:datascript/schema] q)))
        initial-module-loaded-chan (async/chan)
        initial-merge-chan (async/chan)
        send-fn (backend/send! reconciler-atom
                               ;; TODO: Make each remote's basis-t isolated from another
                               ;;       Maybe protocol it?
                               remote-config
                               {:did-merge-fn (apply-once
                                                (fn []
                                                  (reset! init? true)
                                                  (when-not @init?
                                                    (debug "Initial merge happened."))
                                                  (async/close! initial-merge-chan)))
                                :query-fn     (fn [query remote]
                                                (cond-> query (= :remote remote) (add-schema-to-query-once)))})
        reconciler (reconciler/create {:conn                       conn
                                       :parser                     parser
                                       :ui->props                  (utils/cached-ui->props-fn parser)
                                       :send-fn                    send-fn
                                       :remotes                    (:order remote-config)
                                       :shared/modules             modules
                                       :shared/browser-history     history
                                       :shared/store-chat-listener (web.chat/store-chat-listener reconciler-atom)
                                       :shared/auth-lock           auth-lock
                                       :instrument                 (::plomber run-options)})]

    (reset! reconciler-atom reconciler)
    (binding [parser/*parser-allow-remote* false]
      (pushy/start! history)
      ;; Pushy is configured to not work with all routes.
      ;; We ensure that routes has been inited
      (when-not (:route (routes/current-route reconciler))
        (set-current-route! history update-route!)))
    (modules/require-route! modules
                            (:route (routes/current-route reconciler))
                            (fn [route]
                              (debug "Initial module has loaded for route: " route)
                              (async/close! initial-module-loaded-chan)))
    (go
      (async/<! initial-module-loaded-chan)
      (utils/init-state! reconciler send-fn router/Router)
      (async/<! initial-merge-chan)
      (debug "Adding reconciler to root.")
      (add-root! reconciler)
      ;; Pre fetch a few routes that the user is likely to go to (or all of them?)
      (run! #(modules/prefetch-route modules %) [:index :store :browse]))))

(defn run-prod []
  (run {}))

(defn run-simple [& [deps]]
  (run (merge {:auth-lock (auth/fake-lock)}
              deps)))

(defn run-dev [& [deps]]
  (run (merge {:auth-lock (auth/fake-lock)
               :modules   (modules/dev-modules router/routes)}
              deps)))

(defn on-reload! []
  (when-let [chat-listener (some-> reconciler-atom (deref) :config :shared :shared/store-chat-listener)]
    (client.chat/shutdown! chat-listener))
  (run-dev))