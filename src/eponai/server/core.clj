(ns eponai.server.core
  (:gen-class)
  (:require [compojure.core :refer :all]
            [eponai.server.auth :as a]
            [datomic.api :only [q db] :as d]
            [cemerick.friend :as friend]
            [eponai.server.openexchangerates :as exch]
            [clojure.core.async :refer [<! go chan]]
            [clojure.edn :as edn]
            [ring.adapter.jetty :as jetty]
            [eponai.server.datomic_dev :refer [connect!]]
            [eponai.server.parser :as parser]
            [eponai.server.api :as api :refer [api-routes]]
            [eponai.server.site :refer [site-routes]]
            [eponai.server.middleware :as m]))

(defn app* [conn currency-chan email-chan]
  (-> (routes api-routes site-routes)
      (friend/authenticate {:credential-fn       (partial a/cred-fn #(api/user-creds (d/db conn) %))
                            :workflows           [(a/form)]
                            :default-landing-uri "/dev/budget.html"})
      m/wrap-error
      m/wrap-transit
      (m/wrap-state {::m/conn          conn
                     ::m/parser        (parser/parser {:read parser/read :mutate parser/mutate})
                     ::m/currency-chan currency-chan
                     ::m/email-chan    email-chan})
      m/wrap-defaults
      m/wrap-log
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

(def currency-chan (chan))
(def email-chan (chan))

(defn init
  ([]
   (println "Using remote resources.")
   (let [conn (connect!)]
     ;; See comments about this where app*args and app is defined.
     (alter-var-root (var app*args) (fn [_] [conn currency-chan email-chan]))
     (alter-var-root (var app) call-app*)
     (init conn
           (partial exch/currency-rates nil)
           (partial a/send-email-verification (a/smtp)))))
  ([conn cur-fn email-fn]
   (println "Initializing server...")

   (go (while true
         (try
           (api/post-currency-rates conn cur-fn (<! currency-chan))
           (catch Exception e
             (println (.getMessage e))))))
   (go (while true
         (try
           (api/send-email-verification email-fn (<! email-chan))
           (catch Exception e
             (println (.getMessage e))))))
   (println "Done.")))

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
