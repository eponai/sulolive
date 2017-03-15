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
    [eponai.server.external.aws-ec2 :as ec2]
    [eponai.server.external.aws-s3 :as s3]
    [eponai.server.external.aws-elb :as elb]
    [eponai.server.parser.mutate]
    [eponai.server.routes :refer [site-routes]]
    [eponai.server.middleware :as m]
    [eponai.server.external.auth0 :as auth0]
    [eponai.server.external.stripe :as stripe]
    [eponai.server.external.mailchimp :as mailchimp]
    [eponai.server.external.wowza :as wowza]
    [eponai.server.external.host :as server-address]
    [eponai.server.external.chat :as chat]
    [aleph.http :as aleph]
    [taoensso.timbre :refer [debug error info]]
    ;; Dev/debug require
    [ring.middleware.reload :as reload]
    [clojure.spec :as s])
  (:import (io.netty.handler.codec.http HttpContentCompressor)
           (io.netty.handler.stream ChunkedWriteHandler)
           (io.netty.channel ChannelPipeline)))

(defonce in-production? (atom true))

(defn- make-system [conn options]
  (let [in-aws? (and @in-production? (some? (env :aws-elb)))
        aws-ec2 (if in-aws?
                  (ec2/aws-ec2)
                  (ec2/aws-ec2-stub))
        aws-elb (if in-aws?
                  (elb/aws-elastic-beanstalk (-> aws-ec2
                                                 (ec2/find-this-instance)
                                                 (ec2/elastic-beanstalk-env-name)))
                  (elb/aws-elastic-beanstalk-stub))
        server-address (let [serv-addr (server-address/server-address (env :server-url-schema)
                                                                      (env :server-url-host))]
                         (if @in-production?
                           (server-address/prod-server-address aws-elb serv-addr)
                           serv-addr))]
    {:system/auth0     (if @in-production?
                         (auth0/auth0 (env :auth0-client-id)
                                      (env :auth0-client-secret)
                                      server-address)
                         (auth0/auth0-stub conn))
     :system/chat      (chat/->DatomicChat conn)
     :system/wowza     (let [p {:secret         (env :wowza-jwt-secret)
                                :subscriber-url (env :wowza-subscriber-url)
                                :publisher-url  (env :wowza-publisher-url)}]
                         (if (or @in-production?)
                           (wowza/wowza p)
                           (wowza/wowza-stub (select-keys p [:secret]))))
     :system/mailchimp (if @in-production?
                         (mailchimp/mail-chimp (env :mail-chimp-api-key))
                         (mailchimp/mail-chimp-stub))
     :system/stripe    (if (or @in-production? true)
                         (stripe/stripe (env :stripe-secret-key))
                         (stripe/stripe-stub))
     :system/aws-ec2   aws-ec2
     :system/aws-elb   aws-elb
     :system/aws-s3    (if (or @in-production? true)
                         (s3/aws-s3 {:bucket     (env :aws-s3-bucket-photos)
                                     :zone       (env :aws-s3-bucket-photos-zone)
                                     :access-key (env :aws-access-key-id)
                                     :secret     (env :aws-secret-access-key)})
                         (s3/aws-s3-stub))}))

(defn app* [conn {::keys [extra-middleware disable-anti-forgery disable-ssl] :as options}]
  (let [system (make-system conn options)]
    (-> (routes site-routes)
        (cond-> (not @in-production?) (m/wrap-node-modules))
        m/wrap-post-middlewares
        (m/wrap-authenticate conn (:system/auth0 system))
        (cond-> (some? extra-middleware) extra-middleware)
        m/wrap-format
        (m/wrap-state {::m/conn                     conn
                       ::m/in-production?           @in-production?
                       ::m/empty-datascript-db      (m/init-datascript-db conn)
                       ::m/parser                   (parser/server-parser)
                       ;::m/send-email-fn     (e/send-email-fn conn)
                       ::email/send-verification-fn (partial email/send-verification-email @in-production?)
                       ::email/send-invitation-fn   (partial email/send-invitation-email @in-production?)
                       ::m/system                   system
                       ::m/cljs-build-id            (or (env :cljs-build-id) "dev")})
        (m/wrap-defaults @in-production? disable-anti-forgery)
        m/wrap-trace-request
        (cond-> (some? extra-middleware) extra-middleware)
        (cond-> (and @in-production? (not disable-ssl))
                m/wrap-ssl)
        (m/wrap-error @in-production?)
        (cond-> (not @in-production?) reload/wrap-reload))))

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
         port (or (:port opts) env-port default-port)
         netty-pipeline (fn [^ChannelPipeline pipeline]
                          (doto pipeline
                            (.addBefore "request-handler" "deflater" (HttpContentCompressor.))
                            (.addBefore "request-handler" "streamer" (ChunkedWriteHandler.))))]
     ;; by passing (var app) to run-jetty, it'll be forced to
     ;; evaluate app as code changes.
     (info "Using port: " port)
     (aleph/start-server handler (merge {:pipeline-transform netty-pipeline
                                         :port  port}
                                        (dissoc opts :port))))))

(defn -main [& _]
  (start-server))

(defn main-debug
  "For repl, debug and test usages. Takes an optional map. Returns the aleph-server."
  [& [opts]]
  {:pre [(or (nil? opts) (map? opts))]}
  (reset! in-production? false)
  (s/check-asserts true)
  (start-server (merge {:join? false} opts)))

(defn main-release-no-ssl
  []
  (debug "Running repl in production mode without ssl")
  (start-server {:join? false ::disable-ssl true}))

(defn start-server-for-tests [& [{:keys [conn port wrap-state] :as opts}]]
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
                                        (m/wrap-state wrap-state))}
           opts)))
