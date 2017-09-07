(ns eponai.web.app
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [eponai.common.macros :refer [when-not-timbre-level]])
  (:require
    [eponai.web.modules :as modules]
    [eponai.client.utils :as client.utils]
    [eponai.client.auth :as auth]
    [eponai.common.parser :as parser]
    [eponai.client.parser.read]
    [eponai.client.parser.mutate]
    [eponai.client.backend :as backend]
    [eponai.client.remotes :as remotes]
    [eponai.client.reconciler :as reconciler]
    [eponai.client.client-env :as client-env]
    [goog.dom :as gdom]
    [om.next :as om :refer [defui ui]]
    [om.dom :as dom]
    [taoensso.timbre :as timbre :refer [error debug warn info]]
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
    ;[cljsjs.firebase]
    [eponai.client.chat :as client.chat]
    [cemerick.url :as url]
    [medley.core :as medley]
    [eponai.client.auth :as client.auth]
    [eponai.common.database :as db]
    [eponai.client.cart :as client.cart]
    [eponai.web.firebase :as firebase]
    [eponai.web.utils :as web.utils]
    [eponai.client.routes :as client.routes]
    [clojure.data :as data]
    [eponai.web.header :as header]
    [eponai.web.seo :as web.seo]))

(defn add-root! [reconciler]
  (binding [parser/*parser-allow-remote* false]
    (om/add-root! reconciler router/Router (gdom/getElement router/dom-app-id))))

;(defn validate-location [reconciler {:keys [handler route-params]}]
;  (when (and (some? handler)
;             (not (common.routes/location-independent-route? handler))
;             (nil? (web.utils/get-locality)))
;    (let [loc (:locality route-params)
;          loc-entity (db/lookup-entity (db/to-db reconciler) [:sulo-locality/path loc])]
;      (debug "Location was not set, setting new locality: " loc " entity " loc-entity)
;      (web.utils/set-locality loc-entity)
;      (om/transact! reconciler [(list 'client/set-locality {:locality loc-entity})
;                                      :query/locations]))))

(defn update-route-fn [reconciler-atom]
  (fn [{:keys [handler route-params] :as match}]
    (try
      ;(validate-location @reconciler-atom match)
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
            (error "Google analytics error: " e)))          ;('set', 'page', '/new-page.html')

        ;; There's no reason to read local reads when transacting.
        ;; Should this go into om.next?
        (binding [parser/*parser-allow-local-read* false]
          (router/transact-route! reconciler handler
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
                                                                  ;; Make sure we allow local reads when adding root.
                                                                  (binding [parser/*parser-allow-local-read* true]
                                                                    (add-root! reconciler))
                                                                  (debug "query after reindex: " (om/get-query (om/app-root reconciler)))
                                                                  (debug "Re indexed! Queuing reads..")
                                                                  ;; Don't allow local reads for the queued reads.
                                                                  ;; The reads will be called with om/transact!
                                                                  ;; and there's no use for the local return values.
                                                                  (binding [parser/*parser-allow-local-read* false]
                                                                    (queue-cb))
                                                                  (debug "called queue-cb!"))
                                                              (debug "No root query, nothing to queue..")))))))})))
      (catch :default e
        (error "Error when transacting route: " e)
        (let [data (ex-data e)]
          ;(if (= ::location-not-set (:type data))
          ;  (let [loc (:locality route-params)
          ;        loc-entity (db/lookup-entity (db/to-db @reconciler-atom) [:sulo-locality/path loc])]
          ;    (debug "Location was not set, setting new url with pushy: " loc " entity " loc-entity)
          ;    (debug "Route params: " route-params)
          ;    (web.utils/set-locality loc-entity)
          ;    (om/transact! @reconciler-atom [(list 'client/set-locality {:locality loc-entity})
          ;                        :query/locations])
          ;
          ;    (-> (shared/by-key @reconciler-atom :shared/browser-history)
          ;        (pushy/set-token! (routes/url )))
          ;    )
          ;  (error "Error when transacting route: " e))
          )))))

(defn fetch-index-route-data!
  "Fetches and merges client data given a route-map with route, route-params and query-params."
  [reconciler send-fn]
  (if-let [index-var (resolve 'eponai.common.ui.index/Index)]
    (do (debug "Resolved index-var, sending query.")
        (client.utils/send! reconciler send-fn
                            {:remote (client.utils/parse reconciler
                                                         (om/get-query index-var)
                                                         :remote)}))
    (debug "Unable to resolve index-var :(")))

(defn fetch-cover-photos! [reconciler]
  (binding [parser/*parser-allow-local-read* false]
    (om/transact! reconciler '[{:query/stores [{:store/status [:status/type]}
                                               {:store/profile [{:store.profile/cover [:photo/path :photo/id]}]}]}])))

;; We've currently copied the query/cart pattern to here
;; for handling anonymous carts.
(def skus-pattern [{:store.item/_skus
                    [:store.item/price
                     {:store.item/photos [:photo/path]}
                     :store.item/name
                     {:store/_items
                      [{:store/profile
                        [:store.profile/name]}]}]}])

(defn register-user-presence [reconciler]
  (if-let [user-id (client.auth/current-auth reconciler)]
    (let [db (db/to-db reconciler)
          owned-store (db/one-with db {:where   '[[?e :store/owners ?owner]
                                                  [?owner :store.owner/user ?user]]
                                       :symbols {'?user user-id}})
          fb-token (db/singleton-value db :ui.singleton.firebase/token)]
      (debug "Firebase token: " fb-token)
      (-> (.auth js/firebase)
          (.signInWithCustomToken (:token fb-token))
          (.catch (fn [err]
                    (debug "Firebase could not sign in: " err))))
      (when (some? owned-store)
        (firebase/register-store-owner-presence
          (shared/by-key reconciler :shared/firebase)
          user-id)))
    (-> (.auth js/firebase)
        (.signInAnonymously))))

(defn initialize-firebase [reconciler]
  (firebase/initialize reconciler)
  (register-user-presence reconciler))

(defn init-user-cart!
  "Initializing user cart. If the user has done anything when it was not
  logged in, it gets put in the cart once it has logged in."
  [reconciler]
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
                                     {:query/cart [{:user.cart/items ~skus-pattern}
                                                   {:user/_cart [:db/id]}]}])))
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
      (when-not (= :logout (:handler match))
        match))))

(defn set-current-route! [history update-route-fn]
  (let [match-all-routes (partial bidi/match-route common.routes/routes)
        match (match-all-routes (pushy/get-token history))]
    (update-route-fn match)))

(defonce history-atom (atom nil))
(defonce reconciler-atom (atom nil))




(defn- run [{:keys        [loading-bar]
             :shared/keys [modules]
             :or          {loading-bar (loading-bar/loading-bar)}
             :as          run-options}]
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
                                               (into [:datascript/schema
                                                      :query/client-env
                                                      ;; For firebase
                                                      {:query/owned-store [:db/id
                                                                           {:store/locality [:sulo-locality/path]}
                                                                           {:store/owners [{:store.owner/user [:db/id]}]}]}]
                                                     q)))
        initial-module-loaded-chan (async/chan)
        initial-merge-chan (async/chan)
        send-fn (backend/send! reconciler-atom
                               ;; TODO: Make each remote's basis-t isolated from another
                               ;;       Maybe protocol it?
                               remote-config
                               {:will-send-fn (fn [reconciler]
                                                ;(header/update-header! (shared/by-key reconciler :shared/header)
                                                ;                       (client.routes/current-route reconciler)
                                                ;                       (om/app-state reconciler))
                                                )
                                :did-merge-fn (juxt (apply-once
                                                      (fn [reconciler]
                                                        (reset! init? true)
                                                        (when-not @init?
                                                          (debug "Initial merge happened."))
                                                        (async/close! initial-merge-chan)))
                                                    (fn [reconciler]
                                                      (header/update-header! (shared/by-key reconciler :shared/header)
                                                                             (client.routes/current-route reconciler)
                                                                             reconciler)))
                                :query-fn     (fn [query remote]
                                                ;; The reads of the query have been deduped once, but multiple
                                                ;; calls to om/transact! will queue more reads. These reads will
                                                ;; need to be deduped again, to make sure no data is discarded.
                                                ;; Mutations must NOT be run again, as they contain important
                                                ;; information from when the time they were run.
                                                (let [{mutations true reads false} (group-by parser/mutation? query)
                                                      deduped-reads (client.utils/parse @reconciler-atom reads remote)
                                                      deduped-query (into (or (not-empty mutations) [])
                                                                          deduped-reads)]
                                                  (debug "This was a bug when it's 'true': " (not= deduped-query query))
                                                  (cond-> deduped-query
                                                          (= :remote remote)
                                                          (add-schema-to-query-once))))})
        reconciler (reconciler/create
                     ;; By default, keep all the shared keys from run-options
                     ;; i.e. what's passed from run-dev/prod/shared.
                     (merge (into {} (filter (comp #{"shared"} namespace key)) run-options)
                            {:conn                       conn
                             :parser                     parser
                             :ui->props                  (client.utils/cached-ui->props-fn parser)
                             :send-fn                    send-fn
                             :remotes                    (:order remote-config)
                             :shared/scroll-helper       scroll-helper
                             :shared/loading-bar         loading-bar
                             :shared/modules             modules
                             :shared/browser-history     history
                             :shared/store-chat-listener ::shared/prod
                             :shared/stripe              ::shared/client-env
                             :shared/header              (header/header web.seo/head-meta-data)
                             :shared/client-env          (client-env/cljs-client-env reconciler-atom)
                             :instrument                 (::plomber run-options)}))]

    (reset! reconciler-atom reconciler)
    (binding [parser/*parser-allow-remote* false]
      (pushy/start! history))
    (modules/require-route! modules
                            (:route (routes/current-route reconciler))
                            (fn [route]
                              (debug "Initial module has loaded for route: " route)
                              (async/close! initial-module-loaded-chan)))
    (go
      (try
        (do
          (async/<! initial-module-loaded-chan)
          (client.utils/init-state! reconciler send-fn (om/get-query router/Router))
          (async/<! initial-merge-chan)
          (debug "init user cart...")
          (init-user-cart! reconciler)
          (debug "init firebase...")
          (initialize-firebase reconciler)
          (debug "Adding reconciler to root!")
          (add-root! reconciler)
          ; Pre fetch data which makes the site less jumpy

          ;; Prefetch :index when not on :index and some other routes:
          (debug "Fetching all cover photos...")
          (fetch-cover-photos! reconciler)
          (debug "Pre-fetching the :index route...")
          ;; TODO: Should make sure we're only fetching-index-route-data when we
          ;; have locality
          ;(when-not (contains? #{:index :landing-page}
          ;                     (:route (routes/current-route reconciler)))
          ;  (modules/require-route! modules
          ;                          :index
          ;                          #(fetch-index-route-data! reconciler send-fn)))
          ;; Pre fetch all routes
          (debug "Pre-fetching modules...")
          (run! #(modules/prefetch-route modules %) [:index :store :browse])
          (debug "Initial app load done!"))
        (catch :default e
          (error "Init app error: " e)
          (error "Stack: " (.-stack e)))))))

(defn run-prod []
  (run {:shared/auth0    :env/prod
        :shared/firebase :env/prod
        :shared/photos   :env/prod
        :shared/vods     :env/prod
        :shared/login    (auth/login reconciler-atom)
        :shared/modules  (modules/dev-modules router/routes)}))

(defn run-simple [& [deps]]
  (when-not-timbre-level
    (timbre/set-level! :debug))
  (run (merge {:shared/auth0    :env/dev
               :shared/firebase :env/prod
               :shared/photos   :env/dev
               :shared/vods     :env/dev
               :shared/login    (auth/login reconciler-atom)
               :shared/modules  (modules/dev-modules router/routes)}
              deps)))

(defn run-dev [& [deps]]
  (run (merge {
               :shared/auth0    :env/dev
               :shared/firebase :env/prod
               :shared/photos   :env/dev
               :shared/vods     :env/dev
               :shared/login    (auth/login reconciler-atom)
               :shared/modules  (modules/dev-modules router/routes)
               }
              deps)))

(defn add-fake-root! [reconciler]
  (debug "This will flash!")
  (debug "Adding fake root first, then adding real root to make sure our components unmount and mount.")
  (om/add-root! reconciler
                (om/ui
                  Object
                  (render [this]
                          (dom/div nil)))
                (gdom/getElement router/dom-app-id)))

(defn on-reload! []
  (shared/clear-components!)
  (if-let [reconciler @reconciler-atom]
    (do
      (add-fake-root! reconciler)
      (js/setTimeout #(add-root! reconciler) 0))
    (run-dev)))
