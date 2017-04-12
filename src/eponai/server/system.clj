(ns eponai.server.system
  (:require
    [com.stuartsierra.component :as c]
    [suspendable.core :as suspendable]
    [compojure.core :as compojure]
    [taoensso.timbre :refer [debug]]
    [eponai.server.middleware :as m]
    [eponai.common.parser :as parser]
    [eponai.server.routes :as server-routes]
    [environ.core :as environ]
    [eponai.server.external.aleph :as aleph]
    [eponai.server.external.aws-ec2 :as ec2]
    [eponai.server.external.aws-elb :as elb]
    [eponai.server.external.host :as server-address]
    [eponai.server.external.datomic :as datomic]
    [eponai.server.external.chat :as chat]
    [eponai.server.external.auth0 :as auth0]
    [eponai.server.websocket :as websocket]
    [eponai.server.external.wowza :as wowza]
    [eponai.server.external.mailchimp :as mailchimp]
    [eponai.server.external.stripe :as stripe]
    [eponai.server.external.aws-s3 :as s3]))

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
            handler (-> (compojure/routes server-routes/site-routes)
                        (cond-> (not in-production?) (m/wrap-node-modules))
                        m/wrap-post-middlewares
                        (m/wrap-authenticate conn (:system/auth0 system))
                        m/wrap-format
                        (m/wrap-state {::m/conn                conn
                                       ::m/in-production?      in-production?
                                       ::m/empty-datascript-db (m/init-datascript-db conn)
                                       ::m/parser              (parser/server-parser)
                                       ::m/cljs-build-id       (or cljs-build-id "dev")
                                       ::m/system              system})
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
                    :handler handler))))
  (stop [this]
    (dissoc this ::started? ::handler-atom :handler))
  suspendable/Suspendable
  (suspend [this]
    this)
  (resume [this old-this]
    (let [this (c/start this)]
      (let [old-handler (:handler old-this)
            old-atom (::handler-atom old-this)]
        (if (and old-handler old-atom)
          (do
            (reset! old-atom @(::handler-atom this))
            (assoc this :handler old-handler
                        ::handler-atom old-atom))
          (do
            (c/stop old-this)
            this))))))

(defn- system [in-prod? {:keys [env] :as config}]
  (let [system-map (c/system-map
                     :system/aleph (c/using (aleph/map->Aleph (select-keys config [:handler :port :netty-options]))
                                            {:handler :system/handler})
                     :system/auth0 (c/using (auth0/map->Auth0 {:client-id     (:auth0-client-id env)
                                                               :client-secret (:auth0-client-secret env)})
                                            {:server-address :system/server-address})
                     :system/aws-ec2 (ec2/aws-ec2)
                     :system/aws-elb (c/using (elb/map->AwsElasticBeanstalk {})
                                              {:aws-ec2 :system/aws-ec2})
                     :system/aws-s3 (s3/map->AwsS3 {:bucket     (:aws-s3-bucket-photos env)
                                                    :zone       (:aws-s3-bucket-photos-zone env)
                                                    :access-key (:aws-access-key-id env)
                                                    :secret     (:aws-secret-access-key env)})
                     :system/chat (c/using (chat/map->DatomicChat {})
                                           {:chat-datomic :system/chat-datomic
                                            :sulo-datomic :system/datomic})
                     :system/chat-datomic (datomic/map->Datomic
                                            {:db-url           (:chat-db-url env)
                                             :add-mocked-data? false})
                     :system/chat-websocket (c/using (websocket/map->StoreChatWebsocket {})
                                                     {:chat :system/chat})
                     :system/datomic (datomic/map->Datomic
                                       {:db-url           (:db-url env)
                                        :provided-conn    (::provided-conn config)
                                        :add-mocked-data? true})
                     :system/mailchimp (mailchimp/mail-chimp (:mail-chimp-api-key env))
                     :system/server-address (c/using (server-address/map->ServerAddress {:schema (:server-url-schema env)
                                                                                         :host   (:server-url-host env)})
                                                     {:aws-elb :system/aws-elb})
                     :system/stripe (stripe/stripe (env :stripe-secret-key))
                     :system/wowza (wowza/wowza {:secret         (:wowza-jwt-secret env)
                                                 :subscriber-url (:wowza-subscriber-url env)
                                                 :publisher-url  (:wowza-publisher-url env)}))
        ;; Put all of the other components in our request.
        system-map (assoc system-map
                     :system/handler (c/using (map->RequestHandler {:disable-ssl          (::disable-ssl config)
                                                                    :disable-anti-forgery (::disable-anti-forgery config)
                                                                    :in-production?       in-prod?
                                                                    :cljs-build-id        (:cljs-build-id env "dev")})
                                              (vec (keys (dissoc system-map :system/aleph)))))]
    ;; Returns the system wrapped in an atom in case we want to adjust it more.
    ;; The final state of the system atom will be the one assoc'ed in the request.
    system-map))

(defn old-system-keys []
  #{:system/auth0
    :system/chat
    :system/chat-websocket
    :system/wowza
    :system/mailchimp
    :system/stripe
    :system/aws-ec2
    :system/aws-elb
    :system/aws-s3})

(defn prod-system [config]
  {:post [(every? (set (keys %)) (old-system-keys))]}
  (let [config (assoc config :env environ/env)
        in-aws? (some? (get-in config [:env :aws-elb]))]
    (cond-> (system true config)
            (not in-aws?)
            (assoc :system/aws-elb (elb/aws-elastic-beanstalk-stub)
                   :system/aws-ec2 (ec2/aws-ec2-stub)))))

(defn dev-system [config]
  (let [{:keys [env] :as config} (assoc config :env environ/env
                                               ::disable-ssl true
                                               ::disable-anti-forgery true)]
    (assoc (system false config)
      ;; Comment out things to use the production ones.
      :system/auth0 (c/using (auth0/map->FakeAuth0 {})
                             {:datomic :system/datomic})
      :system/aws-elb (elb/aws-elastic-beanstalk-stub)
      :system/aws-ec2 (ec2/aws-ec2-stub)
      ;:system/aws-s3 (s3/aws-s3-stub)
      :system/wowza (wowza/wowza-stub {:secret (:wowza-jwt-secret env)})
      :system/mailchimp (mailchimp/mail-chimp-stub)
      ;; :system/stripe (stripe/stripe-stub)
      )))
