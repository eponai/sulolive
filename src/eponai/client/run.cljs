(ns eponai.client.run
  (:require
    [eponai.client.utils :as utils]
    [eponai.client.auth :as auth]
    [eponai.common.parser :as parser]
    [eponai.client.parser.read]
    [eponai.client.parser.mutate]
    [eponai.client.parser.merge :as merge]
    [eponai.client.backend :as backend]
    [eponai.client.remotes :as remotes]
    [medley.core :as medley]
    [goog.dom :as gdom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [error debug warn]]
    ;; Routing
    [cemerick.url :as url]
    [bidi.bidi :as bidi]
    [pushy.core :as pushy]
    [eponai.client.local-storage :as local-storage]
    [eponai.client.routes :as routes]
    [eponai.common.routes :as common.routes]
    [eponai.common.ui.router :as router]))

(defn update-route-fn [reconciler-atom]
  (fn [{:keys [handler route-params] :as match}]
    (let [r @reconciler-atom]
      (try (routes/transact-route! r handler {:route-params route-params
                                              :queue?       (some? (om/app-root r))})
           (catch :default e
             (debug "Tried to set route: " match ", got error: " e))))))

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

(defonce history-atom (atom nil))
(defonce reconciler-atom (atom nil))
(defonce init? (atom false))

(defn- run [{:keys [auth-lock]
             :or   {auth-lock (auth/auth0-lock)}}]
  (let [
        reconciler-atom (atom nil)
        _ (when-let [h @history-atom]
            (pushy/stop! h))
        match-route (partial bidi/match-route (common.routes/without-coming-soon-route common.routes/routes))
        update-route! (update-route-fn reconciler-atom)
        history (pushy/pushy update-route! (wrap-route-logging match-route))
        _ (reset! history-atom history)
        current-route-fn #(:handler (match-route (pushy/get-token history)))
        conn (utils/create-conn)
        local-storage (local-storage/->local-storage)
        parser (parser/client-parser
                 (parser/client-parser-state
                   {::parser/get-route-params #(let [route-params (or (:route-params (routes/current-route conn))
                                                                      (:route-params (current-route-fn)))
                                                     url (pushy/get-token history)
                                                     query-params (medley/map-keys keyword (:query (url/url url)))]
                                                 ;;TODO: These are merged for now. Separate them?
                                                 (merge route-params query-params))}))
        remotes [:remote :remote/user :remote/chat]
        send-fn (backend/send! reconciler-atom
                               ;; TODO: Make each remote's basis-t isolated from another
                               ;;       Maybe protocol it?
                               {:remote      (-> (remotes/post-to-url "/api")
                                                 (remotes/read-basis-t-remote-middleware conn))
                                :remote/user (-> (remotes/post-to-url "/api/user")
                                                 (remotes/read-basis-t-remote-middleware conn))
                                :remote/chat (-> (remotes/post-to-url "/api/chat")
                                                 (remotes/read-basis-t-remote-middleware conn))}
                               {:did-merge-fn #(when-not @init?
                                                 (reset! init? true)
                                                 (debug "First merge happened. Adding reconciler to root.")
                                                 (binding [parser/*parser-allow-remote* false]
                                                   (om/add-root! @reconciler-atom router/Router (gdom/getElement router/dom-app-id))))
                                :query-fn     (apply-once (fn [q]
                                                            {:pre [(sequential? q)]}
                                                            (into [:datascript/schema] q)))})
        reconciler (om/reconciler {:state     conn
                                   :ui->props (utils/cached-ui->props-fn parser)
                                   :parser    parser
                                   :remotes   remotes
                                   :send      send-fn
                                   :merge     (merge/merge!)
                                   :shared    {:shared/history       history
                                               :shared/local-storage local-storage
                                               :shared/auth-lock     auth-lock}
                                   :migrate   nil})]
    (reset! reconciler-atom reconciler)
    (binding [parser/*parser-allow-remote* false]
      (pushy/start! history)
      ;; For landing page stuff, which we don't want routing for right now.
      (let [match-all-routes (partial bidi/match-route common.routes/routes)
            match (match-all-routes (pushy/get-token history))]
        (when (contains? #{:coming-soon :sell-soon} (:handler match))
          (update-route! match))))
    (utils/init-state! reconciler remotes send-fn parser router/Router)))

(defn run-prod []
  (run {}))

(defn run-dev []
  (run {:auth-lock (auth/fake-lock)}))

(defn on-reload! []
  (run-dev)
  (comment
    "We want to try to use this instead of re-building/initing everything."
    "But for now we'll just (run-dev)."
    (if-not @init?

     (om/add-root! @reconciler-atom router/Router (gdom/getElement router/dom-app-id)))))