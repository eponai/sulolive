(ns eponai.server.external.request-handler
  (:require
    [com.stuartsierra.component :as c]
    [suspendable.core :as suspendable]
    [manifold.deferred :as deferred]
    [compojure.core :as compojure]
    [taoensso.timbre :refer [debug]]
    [eponai.server.middleware :as m]
    [eponai.common.parser :as parser]
    [eponai.server.routes :as server-routes]
    [eponai.server.log :as log]
    [medley.core :as medley]))

(defn to-queued-request
  "Queue request and put the response on the deferred response."
  [request deferred-response]
  (fn [handler]
    (let [response (handler request)]
      (if (deferred/deferrable? response)
        (do
          (debug "Resumed deferrable response")
          @(deferred/chain (deferred/->deferred response)
                           #(deferred/success! deferred-response %))
          (debug "Deferrable response finished.")))
      (do
        (debug "Resumed non-deferrable response")
        (deferred/success! deferred-response response)))))

;; Leaving this here because it's hard to say where it should go?
(defrecord RequestHandler [in-production? cljs-build-id disable-ssl disable-anti-forgery]
  c/Lifecycle
  (start [this]
    (if (::started? this)
      this
      (let [system (into {}
                         (comp (filter #(= "system" (namespace %)))
                               (map (juxt identity #(get this %))))
                         (keys this))
            conn (:conn (:system/datomic system))
            suspended-state (atom {:queued-requests (medley/queue)
                                   :suspended? false})
            wrap-suspendable (fn [handler]
                               (fn [request]
                                 (if (:suspended? @suspended-state)
                                   (let [deferred-response (deferred/deferred)
                                         queued-request (to-queued-request request deferred-response)]
                                     (do
                                       (debug "Suspended request " (hash queued-request))
                                       (swap! suspended-state update :queued-requests conj queued-request))
                                     deferred-response)
                                   (handler request))))
            logger (log/async-logger (log/->TimbreLogger))
            handler (-> (compojure/routes server-routes/site-routes)
                        (cond-> (not in-production?) (m/wrap-node-modules))
                        m/wrap-post-middlewares
                        m/wrap-format
                        (m/wrap-authenticate conn (:system/auth0 system))
                        (m/wrap-state {::m/conn                conn
                                       ::m/in-production?      in-production?
                                       ::m/empty-datascript-db (m/init-datascript-db conn)
                                       ;; Construct a new parser each request.
                                       ::m/parser-fn           #(parser/server-parser)
                                       ::m/cljs-build-id       (or cljs-build-id "dev")
                                       ::m/system              system
                                       ::m/logger              logger})
                        ;; Places wrap-suspendable under wrap-js-files, so figwheel can get the
                        ;; javascript files from our server, but ajax requests waits until the
                        ;; system is restarted.
                        (cond-> (not in-production?) wrap-suspendable)
                        (m/wrap-js-files cljs-build-id)
                        (m/wrap-defaults in-production? disable-anti-forgery)
                        m/wrap-trace-request
                        (cond-> (and in-production? (not disable-ssl))
                                m/wrap-ssl)
                        (m/wrap-error in-production?))
            ;; Wraps handler in an atom in development, so we can swap implementation
            ;; at runtime/reload-time.
            handler-atom (atom handler)
            handler (if in-production?
                      handler
                      (fn [request]
                        (@handler-atom request)))]
        (assoc this ::started? true
                    ::handler-atom handler-atom
                    ::suspended-state suspended-state
                    :handler handler
                    :logger logger))))
  (stop [this]
    (when-some [l (:logger this)]
      (c/stop l))
    (dissoc this ::started? ::handler-atom :handler :logger))
  suspendable/Suspendable
  (suspend [this]
    (debug "Suspending request handler.")
    (swap! (::suspended-state this) assoc :suspended? true)
    this)
  (resume [this old-this]
    (let [this (c/start this)]
      (let [old-handler (:handler old-this)
            old-atom (::handler-atom old-this)]
        (if (and old-handler old-atom)
          (do
            (swap! (::suspended-state this) assoc :queued-requests (-> old-this ::suspended-state deref :queued-requests))
            (reset! old-atom @(::handler-atom this))
            (assoc this :handler old-handler
                        ::handler-atom old-atom
                        :logger (:logger old-this)))
          (do
            (c/stop old-this)
            this))))))

(defn resume-requests [request-handler]
  (let [requests (-> request-handler ::suspended-state deref :queued-requests)]
    (swap! (::suspended-state request-handler) update :queued-requests empty)
    (debug "Will resume suspended requests: " (count requests))
    (run! (fn [queued-request]
            (debug "Resuming request: " (hash queued-request))
            ;; Queued requests take the handler instead of the other way around.
            ;; See where :queued-requests are queued.
            (queued-request (:handler request-handler)))
          requests)
    (when (seq requests)
      (debug "Resumed all suspended requests."))))
