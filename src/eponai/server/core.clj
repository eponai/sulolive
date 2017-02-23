(ns eponai.server.core
  (:gen-class)
  (:require
    [clojure.core.async :as async :refer [<! go chan]]
    [compojure.core :refer :all]
    [environ.core :refer [env]]
    [eponai.common.parser :as parser]
    [eponai.common.validate]
    [eponai.server.email :as email]
    [eponai.server.datomic-dev :as datomic_dev]
    [eponai.server.parser.read]
    [eponai.server.external.aws-s3 :as s3]
    [eponai.server.parser.mutate]
    [eponai.server.routes :refer [site-routes]]
    [eponai.server.middleware :as m]
    [eponai.server.external.stripe :as stripe]
    [eponai.server.external.mailchimp :as mailchimp]
    [eponai.server.external.wowza :as wowza]
    [eponai.server.external.chat :as chat]
    [ring.adapter.jetty :as jetty]
    [taoensso.timbre :refer [debug error info]]
    ;; Dev/debug require
    [ring.middleware.reload :as reload]))

(defonce in-production? (atom true))

(defn app* [conn {::keys [extra-middleware disable-anti-forgery disable-ssl] :as options}]
  (-> (routes site-routes)
      (cond-> (not @in-production?) (m/wrap-node-modules))
      m/wrap-post-middlewares
      (m/wrap-authenticate conn @in-production?)
      (cond-> (some? extra-middleware) extra-middleware)
      m/wrap-format
      (m/wrap-state {::m/conn                     conn
                     ::m/in-production?           @in-production?
                     ::m/empty-datascript-db      (m/init-datascript-db conn)
                     ::m/parser                   (parser/server-parser)
                     ;::m/send-email-fn     (e/send-email-fn conn)
                     ::email/send-verification-fn (partial email/send-verification-email @in-production?)
                     ::email/send-invitation-fn   (partial email/send-invitation-email @in-production?)
                     ::m/system                   {:system/chat      (chat/->DatomicChat conn)
                                                   :system/wowza     (let [p {:secret         (env :wowza-jwt-secret)
                                                                              :subscriber-url (env :wowza-subscriber-url)
                                                                              :publisher-url  (env :wowza-publisher-url)}]
                                                                       (if (or @in-production?)
                                                                         (wowza/wowza p)
                                                                         (wowza/wowza-stub (select-keys p [:secret]))))
                                                   :system/mailchimp (if @in-production?
                                                                       (mailchimp/mail-chimp (env :mail-chimp-api-key))
                                                                       (mailchimp/mail-chimp-stub))
                                                   :system/stripe    (if (or @in-production?)
                                                                       (stripe/stripe (env :stripe-secret-key))
                                                                       (stripe/stripe-stub))
                                                   :system/aws-s3    (if (or @in-production?)
                                                                       (s3/aws-s3 {:bucket     (env :aws-s3-bucket-photos)
                                                                                   :zone       (env :aws-s3-bucket-photos-zone)
                                                                                   :access-key (env :aws-access-key-id)
                                                                                   :secret     (env :aws-secret-access-key)})
                                                                       (s3/aws-s3-stub))}
                     ::m/cljs-build-id            (or (env :cljs-build-id) "dev")})
      (m/wrap-defaults @in-production? disable-anti-forgery)
      (m/wrap-error @in-production?)
      m/wrap-trace-request
      (cond-> (some? extra-middleware) extra-middleware)
      (cond-> (and @in-production? (not disable-ssl))
              m/wrap-ssl)
      (cond-> (not @in-production?) reload/wrap-reload)
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
    (alter-var-root (var app*args) (fn [_] [conn (select-keys opts [::extra-middleware
                                                                    ::disable-anti-forgery
                                                                    ::disable-ssl])]))
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
  (start-server (merge {:join? false} opts)))

(defn main-release-no-ssl
  []
  (debug "Running repl in production mode without ssl")
  (start-server {:join? false ::disable-ssl true}))

(defn start-server-for-tests [& [{:keys [email-chan conn port wrap-state] :as opts}]]
  {:pre [(or (nil? opts) (map? opts))]}
  (reset! in-production? false)
  (start-server
    (merge {:join?             false
            :port              (or port 0)
            :daemon?           true
            ::provided-conn    conn
            ::stateless-server true
            ::extra-middleware #(cond-> %
                                        (some? wrap-state)
                                        (m/wrap-state wrap-state)
                                        (some? email-chan)
                                        (m/wrap-state {::email/send-verification-fn
                                                       (fn [verification params]
                                                         (debug "Putting verification: " verification
                                                                " on email-chan with params: " params)
                                                         (async/put! email-chan
                                                                     {:verification verification
                                                                      :params       params}))}))}
           opts)))
