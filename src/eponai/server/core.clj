(ns eponai.server.core
  (:gen-class)
  (:require [clojure.core.async :refer [<! go chan]]
            [compojure.core :refer :all]
            [environ.core :refer [env]]
            [eponai.common.parser :as parser]
            [eponai.server.email :as e]
            [eponai.server.openexchangerates :as exch]
            [eponai.server.datomic_dev :refer [connect!]]
            [eponai.server.api :refer [api-routes]]
            [eponai.server.site :refer [site-routes]]
            [eponai.server.middleware :as m]
            [ring.adapter.jetty :as jetty]))

(defn app* [conn]
  (-> (routes api-routes site-routes)
      (m/wrap-authenticate conn)
      m/wrap-error
      m/wrap-transit
      (m/wrap-state {::m/conn              conn
                     ::m/parser            (parser/parser)
                     ::m/currency-rates-fn (exch/currency-rates-fn nil)
                     ::m/send-email-fn     (e/send-email-fn conn)
                     ;; either "dev" or "release"
                     ::m/cljs-build-id     (or (env :cljs-build-id) "dev")})
      m/wrap-defaults
      ;m/wrap-log
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
  (println "Initializing server...")
  (let [conn (connect!)]
    ;; See comments about this where app*args and app is defined.
    (alter-var-root (var app*args) (fn [_] [conn]))
    (alter-var-root (var app) call-app*))
  (println "Done."))

(defn -main [& args]
  (init)
  (let [default-port 3000
        port (try
               (Long/parseLong (first args))
               (catch Exception e
                 default-port))]
    ;; by passing (var app) to run-jetty, it'll be forced to
    ;; evaluate app as code changes.
    (jetty/run-jetty (var app) {:port port})))

(defn main-debug
  "For repl-debug use.
  Returns a future with the jetty-server.
  The jetty-server will block the current thread, so
  we just wrap it in something dereffable."
  []
  (future (-main)))
