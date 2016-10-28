(ns eponai.server.core
  (:gen-class)
  (:require [clojure.core.async :as async :refer [<! go chan]]
            [compojure.core :refer :all]
            [environ.core :refer [env]]
            [datomic.api :as d]
            [eponai.common.parser :as parser]
            [eponai.common.validate]
            [eponai.server.email :as email]
            [eponai.server.external.openexchangerates :as exch]
            [eponai.server.datomic-dev :as datomic_dev]
            [eponai.server.parser.read]
            [eponai.server.parser.mutate]
            [eponai.server.routes :refer [site-routes api-routes]]
            [eponai.server.middleware :as m]
            [eponai.server.external.stripe :as stripe]
            [ring.adapter.jetty :as jetty]
            [taoensso.timbre :refer [debug error info]]
    ;; Debug/dev requires
            [ring.middleware.reload :as reload]
            [prone.middleware :as prone]))

(defonce in-production? (atom true))

(defn app* [conn extra-middleware]
  (-> (routes api-routes site-routes)
      (cond-> (some? extra-middleware) extra-middleware)
      m/wrap-post-middlewares
      (m/wrap-authenticate conn @in-production?)
      m/wrap-login-parser
      m/wrap-logout
      (cond-> @in-production? m/wrap-error)
      m/wrap-format
      (m/wrap-state {::m/conn                     conn
                     ::m/parser                   (parser/server-parser)
                     ::m/currencies-fn            #(exch/currencies (when @in-production? (env :open-exchange-app-id)))
                     ::m/currency-rates-fn        (exch/currency-rates-fn (when @in-production? (env :open-exchange-app-id)))
                     ;::m/send-email-fn     (e/send-email-fn conn)
                     ::stripe/stripe-fn           (fn [k p]
                                                    (stripe/stripe (env :stripe-secret-key-test) k p))
                     ::email/send-verification-fn (partial email/send-verification-email @in-production?)
                     ::email/send-invitation-fn   (partial email/send-invitation-email @in-production?)
                     ::m/playground-user-uuid-fn  (if (env :playground-user-uuid)
                                                    (constantly (java.util.UUID/fromString (env :playground-user-uuid)))
                                                    (when-not @in-production?
                                                      ;; In development we'll just get the first user.
                                                      (fn [] (d/q '{:find  [?uuid .]
                                                                    :where [[_ :user/uuid ?uuid]]}
                                                                  (d/db conn)))))
                     ;; either "dev" or "release"
                     ::m/cljs-build-id            (or (env :cljs-build-id) "dev")})
      m/wrap-defaults
      m/wrap-trace-request
      (cond-> @in-production? m/wrap-ssl)
      m/wrap-gzip))

;; Do a little re-def dance. Store the arguments to app* in a var, right before
;; it is redefined.
;; app*args and app will be defined by init the first time, then they'll be
;; redefined by ring.middleware.reload when it is redefining the namespace.
(declare app*args call-app*)

(def prev-app*args (when (bound? #'app*args) app*args))
(def ^:dynamic app*args (when prev-app*args prev-app*args))
(def ^:dynamic app (when app*args (call-app*)))

(defn call-app* [& _]
  (apply app* app*args))
;; <--- END Re-def hack.

(defn init
  [opts]
  (info "Initializing server...")
  (let [conn (or (::provided-conn opts)
                 (if (::stateless-server opts)
                   (datomic_dev/create-connection)
                   (datomic_dev/connect!)))]
    ;; See comments about this where app*args and app is defined.
    (alter-var-root (var app*args) (fn [_] [conn (::extra-middleware opts)]))
    (alter-var-root (var app) call-app*))
  (info "Done initializing server."))

(defn start-server
  ([] (start-server {}))
  ([opts] (start-server opts (var app)))
  ([opts handler]
   (init opts)
   (let [default-port 3000
         env-port (try
                    (Long/parseLong (env :port))
                    (catch Exception e
                      nil))
         port (or (:port opts) env-port default-port)]
     ;; by passing (var app) to run-jetty, it'll be forced to
     ;; evaluate app as code changes.
     (info "Using port: " port)
     (jetty/run-jetty handler (merge {:port port} (dissoc opts :port))))))

(defn -main [& _]
  (start-server))

(defn main-debug
  "For repl, debug and test usages. Takes an optional map. Returns the jetty-server."
  [& [opts]]
  {:pre [(or (nil? opts) (map? opts))]}
  (reset! in-production? false)
  (start-server (merge {:join?             false
                        ::extra-middleware #(-> %
                                                (prone/wrap-exceptions {:app-namespaces ["eponai"]})
                                                reload/wrap-reload)}
                       opts)))

(defn start-server-for-tests [& [{:keys [email-chan conn port] :as opts}]]
  {:pre [(or (nil? opts) (map? opts))]}
  (reset! in-production? false)
  (start-server
    (merge {:join?             false
            :port              (or port 0)
            :daemon?           true
            ::provided-conn    conn
            ::stateless-server true
            ::extra-middleware #(cond-> %
                                        (some? email-chan)
                                        (m/wrap-state {::email/send-verification-fn
                                                       (fn [verification params]
                                                         (debug "Putting verification: " verification
                                                                " on email-chan with params: " params)
                                                         (async/put! email-chan
                                                                     {:verification verification
                                                                      :params       params}))}))}
           opts)))
