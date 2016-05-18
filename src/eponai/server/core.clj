(ns eponai.server.core
  (:gen-class)
  (:require [clojure.core.async :refer [<! go chan]]
            [compojure.core :refer :all]
            [environ.core :refer [env]]
            [datomic.api :as d]
            [eponai.common.parser :as parser]
            [eponai.common.validate]
            [eponai.server.email :as e]
            [eponai.server.external.openexchangerates :as exch]
            [eponai.server.datomic-dev :refer [connect!]]
            [eponai.server.parser.read]
            [eponai.server.parser.mutate]
            [eponai.server.routes :refer [site-routes api-routes]]
            [eponai.server.middleware :as m]
            [ring.adapter.jetty :as jetty]
            [taoensso.timbre :refer [debug error info]]
    ;; Debug/dev requires
            [ring.middleware.reload :as reload]
            [prone.middleware :as prone]
            [eponai.server.external.stripe :as stripe]
            [eponai.server.email :as email]))

(defonce in-production? (atom true))

(defn app* [conn]
  (-> (routes api-routes site-routes)
      m/wrap-post-middlewares
      (m/wrap-authenticate conn)
      (cond-> @in-production? m/wrap-error)
      m/wrap-format
      (m/wrap-state {::m/conn                     conn
                     ::m/parser                   (parser/parser)
                     ::m/currencies-fn            #(exch/currencies (when @in-production? (env :open-exchange-app-id)))
                     ::m/currency-rates-fn        (exch/currency-rates-fn (when @in-production? (env :open-exchange-app-id)))
                     ;::m/send-email-fn     (e/send-email-fn conn)
                     ::stripe/stripe-fn           (fn [k p]
                                                    (stripe/stripe (env :stripe-secret-key-test) k p))
                     ::email/send-verification-fn (partial email/send-verification-email @in-production?)
                     ::email/send-invitation-fn   (partial email/send-invitation-email @in-production?)
                     ::m/playground-user-uuid-fn  (if (env :playground-user-uuid)
                                                    (constantly (env :playground-user-uuid))
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
  []
  (info "Initializing server...")
  (let [conn (connect!)]
    ;; See comments about this where app*args and app is defined.
    (alter-var-root (var app*args) (fn [_] [conn]))
    (alter-var-root (var app) call-app*))
  (info "Done initializing server."))

(defn start-server
  ([] (start-server (var app) {}))
  ([handler opts]
   (init)
   (let [default-port 3000
         port (try
                (Long/parseLong (env :port))
                (catch Exception e
                  default-port))]
     ;; by passing (var app) to run-jetty, it'll be forced to
     ;; evaluate app as code changes.
     (info "Using port: " port)
     (jetty/run-jetty handler (merge {:port port}
                                     opts)))))

(defn -main [& _]
  (start-server))

(defn main-debug
  "For repl-debug use.
  Returns a future with the jetty-server.
  The jetty-server will block the current thread, so
  we just wrap it in something dereffable."
  []
  (reset! in-production? false)
  (start-server (-> (var app)
                    (m/wrap-state {::m/make-parser-error-fn (fn [req]
                                                              (fn [e]
                                                                (error "Will create prone exception.")
                                                                (error e)
                                                                (prone/exceptions-response req e ["eponai"])))})
                    (prone/wrap-exceptions {:app-namespaces ["eponai"]})
                    reload/wrap-reload)
                {:join? false}))
