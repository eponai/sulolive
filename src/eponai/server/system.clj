(ns eponai.server.system
  (:require
    [com.stuartsierra.component :as c]
    [suspendable.core :as suspendable]
    [manifold.deferred :as deferred]
    [compojure.core :as compojure]
    [taoensso.timbre :refer [debug]]
    [eponai.server.middleware :as m]
    [eponai.server.external.cloudinary :as cloudinary]
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
    [eponai.server.external.aws-s3 :as s3]
    [medley.core :as medley]))

(def system-keys #{:system/aleph
                   :system/auth0
                   :system/aws-ec2
                   :system/aws-elb
                   :system/aws-s3
                   :system/chat
                   :system/chat-datomic
                   :system/chat-websocket
                   :system/cloudinary
                   :system/datomic
                   :system/handler
                   :system/mailchimp
                   :system/server-address
                   :system/stripe
                   :system/wowza})

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
            handler (-> (compojure/routes server-routes/site-routes)
                        (cond-> (not in-production?) (m/wrap-node-modules))
                        m/wrap-post-middlewares
                        m/wrap-format
                        (m/wrap-authenticate conn (:system/auth0 system))
                        (m/wrap-state {::m/conn                conn
                                       ::m/in-production?      in-production?
                                       ::m/empty-datascript-db (m/init-datascript-db conn)
                                       ::m/parser              (parser/server-parser)
                                       ::m/cljs-build-id       (or cljs-build-id "dev")
                                       ::m/system              system})
                        (m/wrap-js-files cljs-build-id)
                        (m/wrap-defaults in-production? disable-anti-forgery)
                        m/wrap-trace-request
                        (cond-> (and in-production? (not disable-ssl))
                                m/wrap-ssl)
                        (m/wrap-error in-production?)
                        (cond-> (not in-production?) wrap-suspendable))
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
                    :handler handler))))
  (stop [this]
    (dissoc this ::started? ::handler-atom :handler))
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
                        ::handler-atom old-atom))
          (do
            (c/stop old-this)
            this))))))

(defn resume-requests
  "Resumes requests when a system has been restarted."
  [system]
  (when-let [request-handler (:system/handler system)]
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
        (debug "Resumed all suspended requests.")))))

(defn components-without-fakes [{:keys [env] :as config}]
  {:system/aleph          (c/using (aleph/map->Aleph (select-keys config [:handler :port :netty-options]))
                                   {:handler :system/handler})
   :system/cloudinary     (cloudinary/->Cloudinary
                            (:cloudinary-api-key env)
                            (:cloudinary-api-secret env))
   :system/chat           (c/using (chat/map->DatomicChat {})
                                   {:chat-datomic :system/chat-datomic
                                    :sulo-datomic :system/datomic})
   :system/chat-datomic   (datomic/map->Datomic
                            {:db-url           (:chat-db-url env)
                             :add-mocked-data? false})
   :system/chat-websocket (c/using (websocket/map->StoreChatWebsocket {})
                                   {:chat :system/chat})
   :system/datomic        (datomic/map->Datomic
                            {:db-url           (:db-url env)
                             :provided-conn    (::provided-conn config)
                             :add-mocked-data? true})
   :system/server-address (c/using (server-address/map->ServerAddress {:schema (:server-url-schema env)
                                                                       :host   (:server-url-host env)})
                                   {:aws-elb :system/aws-elb})})

(defn real-components [{:keys [env] :as config}]
  {:system/auth0     (c/using (auth0/map->Auth0 {:client-id     (:auth0-client-id env)
                                                 :client-secret (:auth0-client-secret env)})
                              {:server-address :system/server-address})
   :system/aws-ec2   (ec2/aws-ec2)
   :system/aws-elb   (c/using (elb/map->AwsElasticBeanstalk {})
                              {:aws-ec2 :system/aws-ec2})
   :system/aws-s3    (s3/map->AwsS3 {:bucket     (:aws-s3-bucket-photos env)
                                     :zone       (:aws-s3-bucket-photos-zone env)
                                     :access-key (:aws-access-key-id env)
                                     :secret     (:aws-secret-access-key env)})
   :system/mailchimp (mailchimp/mail-chimp (:mailchimp-api-key env))
   :system/stripe    (stripe/stripe (:stripe-secret-key env))
   :system/wowza     (wowza/wowza {:secret         (:wowza-jwt-secret env)
                                   :subscriber-url (:wowza-subscriber-url env)
                                   :publisher-url  (:wowza-publisher-url env)})})

(defn fake-components [{:keys [env] :as config}]
  {:system/auth0     (c/using (auth0/map->FakeAuth0 {})
                              {:datomic :system/datomic})
   :system/aws-ec2   (ec2/aws-ec2-stub)
   :system/aws-elb   (elb/aws-elastic-beanstalk-stub)
   :system/aws-s3    (s3/aws-s3-stub)
   :system/mailchimp (mailchimp/mail-chimp-stub)
   :system/stripe    (stripe/stripe-stub (:stripe-secret-key env))
   :system/wowza     (wowza/wowza-stub {:secret (:wowza-jwt-secret env)})})

(defn with-request-handler [system {:keys [in-prod? env] :as config}]
  (assoc system
    :system/handler
    (c/using (map->RequestHandler {:disable-ssl          (::disable-ssl config)
                                   :disable-anti-forgery (::disable-anti-forgery config)
                                   :in-production?       in-prod?
                                   :cljs-build-id        (:cljs-build-id env "dev")})
             (vec (keys (dissoc system :system/aleph))))))

(defn real-system [{:keys [in-aws?] :as config}]
  (c/map->SystemMap (-> (merge (components-without-fakes config)
                               (real-components config)
                               (when (not in-aws?)
                                 (select-keys (fake-components config) [:system/aws-elb :system/aws-ec2])))
                        (with-request-handler config))))

(defn fake-system [config & real-component-keys]
  (let [reals (real-components config)
        fakes (fake-components config)]
    (assert (= (set (keys reals)) (set (keys fakes)))
            (str "Real components and fake components did not contain the same"
                 " keys. Either create a fake (or real) and put it in the "
                 " function missing the component, or if the component does not"
                 " have a fake, put it in (components-without fakes ..)."
                 " diff of (clojure.data/diff (set (keys reals)) (set (keys fakes))) => "
                 (clojure.data/diff (set (keys reals))
                                    (set (keys fakes)))))
    (c/map->SystemMap (-> (merge (components-without-fakes config)
                                 fakes
                                 (select-keys reals real-component-keys))
                          (with-request-handler config)))))

(defn prod-system [config]
  {:post [(= (set (keys %)) system-keys)]}
  (let [config (assoc config :env environ/env
                             :in-prod? true
                             :in-aws? (some? (get-in config [:env :aws-elb])))]
    (real-system config)))

(defn dev-config [config]
  (assoc config :env environ/env
                :in-prod? false
                ::disable-ssl true
                ::disable-anti-forgery true))

(defn dev-system [config]
  {:post [(= (set (keys %)) system-keys)]}
  (fake-system (dev-config config)
               ;; Put keys under here to use the real implementation
               ;; :system/stripe
               ;; :system/mailchimp
               ))

(defn test-system [config]
  {:post [(= (set (keys %)) system-keys)]}
  ;; Never uses a real implementation.
  (fake-system (dev-config config)))
