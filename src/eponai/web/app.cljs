(ns eponai.web.app
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
    [eponai.web.modules :as modules]
    [eponai.client.utils :as client.utils]
    [eponai.web.auth :as auth]
    [eponai.common.parser :as parser]
    [eponai.client.parser.read]
    [eponai.client.parser.mutate]
    [eponai.client.backend :as backend]
    [eponai.client.remotes :as remotes]
    [eponai.client.reconciler :as reconciler]
    [goog.dom :as gdom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [error debug warn info]]
    [bidi.bidi :as bidi]
    [pushy.core :as pushy]
    [eponai.client.routes :as routes]
    [eponai.common.routes :as common.routes]
    [eponai.common.ui.router :as router]
    [eponai.common.ui.loading-bar :as loading-bar]
    ;; TODO: Fix the scroll bar FFFFS!
    ;; [eponai.web.scroll-helper :as scroll-helper]
    [cljs.core.async :as async]
    [eponai.common.shared :as shared]
    [eponai.client.chat :as client.chat]
    [cemerick.url :as url]
    [medley.core :as medley]
    [eponai.client.auth :as client.auth]
    [eponai.common.database :as db]
    [eponai.client.cart :as client.cart]
    [eponai.web.utils :as web.utils]))

(defn add-root! [reconciler]
  (binding [parser/*parser-allow-remote* false]
    (om/add-root! reconciler router/Router (gdom/getElement router/dom-app-id))))

(defn validate-location [{:keys [handler route-params]}]
  (when (and (some? handler)
             (not (common.routes/location-independent-route? handler))
             (nil? (web.utils/get-locality)))
    (throw (ex-info (str "Unable to set route: " handler
                         " route-params: " route-params
                         " because we didn't have location set.")
                    {:route handler
                     :route-params route-params
                     :type ::location-not-set}))))

(defn update-route-fn [reconciler-atom]
  (fn [{:keys [handler route-params] :as match}]
    (try
      (validate-location match)
      (let [reconciler @reconciler-atom
            modules (shared/by-key reconciler :shared/modules)
            loaded-route? (modules/loaded-route? modules handler)
            allow-remotes? parser/*parser-allow-remote*
            path (->> (shared/by-key reconciler :shared/browser-history)
                      (pushy/get-token))
            query-params (->> path
                              (url/url)
                              :query
                              (medley/map-keys keyword))]
        (debug "updating route: " handler " query: " query-params)
        (try
          (when (exists? js/ga)
            (js/ga "set" "page" path)
            (js/ga "send" "pageview"))
          (catch :default e
            (error "Google analytics error: " e)))                        ;('set', 'page', '/new-page.html')
        (routes/transact-route! reconciler handler
                                {:route-params  route-params
                                 :query-params  query-params
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
        (let [data (ex-data e)]
          (if (= ::location-not-set (:type data))
            (do
              (debug "Location was not set, setting new url with pushy.")
              (-> (shared/by-key @reconciler-atom :shared/browser-history)
                  (pushy/set-token! (routes/url :landing-page/locality))))
            (error "Error when transacting route: " e)))))))

(def skus-pattern [{:store.item/_skus
                    [:store.item/price
                     {:store.item/photos [:photo/path]}
                     :store.item/name
                     {:store/_items
                      [{:store/profile
                        [:store.profile/name]}]}]}])

(defn init-user-cart! [reconciler]
  (let [skus (client.cart/get-skus reconciler)]
    (if (client.auth/current-auth (db/to-db reconciler))
      (when (seq skus)
        (debug "User is logged in and there's a cart."
               " Remove cart and transact skus.")
        ;; Clearing the cart first just in case something bad happens
        ;; and we don't want to continously transact the items
        (client.cart/remove-cart reconciler)
        (binding [parser/*parser-allow-local-read* false]
          (om/transact! reconciler `[(shopping-bag/add-items ~{:skus (vec skus)})
                                     {:query/cart [{:user.cart/items ~skus-pattern}]}])))
      (do (debug "User was not logged in. Restoring cart.")
          (client.cart/restore-cart reconciler)
          (when (seq skus)
            (debug "Transacting skus: " skus)
            (binding [parser/*parser-allow-local-read* false]
              (om/transact!
                reconciler `[({:query/skus ~skus-pattern} ~{:sku-ids (vec skus)})])))))))

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

(defn- run [{:keys [auth-lock modules loading-bar stripe]
             :or   {auth-lock   (auth/auth0-lock)
                    loading-bar (loading-bar/loading-bar)
                    stripe      ::shared/prod}
             :as   run-options}]
  (let [modules (or modules (modules/advanced-compilation-modules router/routes))
        init? (atom false)
        _ (when-let [h @history-atom]
            (pushy/stop! h))

        scroll-helper nil
        ;; (scroll-helper/init-scroll!)
        match-route (partial bidi/match-route common.routes/routes)
        update-route! (update-route-fn reconciler-atom)
        history (pushy/pushy update-route! (wrap-route-logging match-route))
        _ (reset! history-atom history)
        conn (client.utils/create-conn)
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
                                       :ui->props                  (client.utils/cached-ui->props-fn parser)
                                       :send-fn                    send-fn
                                       :remotes                    (:order remote-config)
                                       :shared/stripe              stripe
                                       :shared/scroll-helper       scroll-helper
                                       :shared/loading-bar         loading-bar
                                       :shared/modules             modules
                                       :shared/browser-history     history
                                       :shared/store-chat-listener ::shared/prod
                                       :shared/auth-lock           auth-lock
                                       :instrument                 (::plomber run-options)})]
    (reset! reconciler-atom reconciler)
    (binding [parser/*parser-allow-remote* false]
      (pushy/start! history))
    (modules/require-route! modules
                            (:route (routes/current-route reconciler))
                            (fn [route]
                              (debug "Initial module has loaded for route: " route)
                              (async/close! initial-module-loaded-chan)))
    (go
      (async/<! initial-module-loaded-chan)
      (client.utils/init-state! reconciler send-fn (om/get-query router/Router))
      (async/<! initial-merge-chan)
      (debug "Adding reconciler to root.")
      (init-user-cart! reconciler)
      (add-root! reconciler)
      ;; Pre fetch all routes so the scroll doesn't freak out as much.
      (run! #(modules/prefetch-route modules %) [:index :store :browse]))))

(defn run-prod []
  (run {}))

(defn run-simple [& [deps]]
  (run (merge {:auth-lock (auth/fake-lock)}
              deps)))

(defn run-dev [& [deps]]
  (run (merge {
               :auth-lock (auth/fake-lock)
               :modules   (modules/dev-modules router/routes)
               :stripe    ::shared/dev
               }
              deps)))

(defn on-reload! []
  (when-let [chat-listener (some-> reconciler-atom (deref) (shared/by-key :shared/store-chat-listener))]
    (client.chat/shutdown! chat-listener))
  (run-dev))