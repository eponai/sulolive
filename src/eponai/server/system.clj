(ns eponai.server.system
  (:require
    [com.stuartsierra.component :as c]
    [taoensso.timbre :refer [debug]]
    [eponai.server.external.cloudinary :as cloudinary]
    [environ.core :as environ]
    [eponai.client.client-env :as client-env]
    [eponai.server.external.aleph :as aleph]
    [eponai.server.external.aws-ec2 :as ec2]
    [eponai.server.external.aws-elb :as elb]
    [eponai.server.external.host :as server-address]
    [eponai.server.external.datomic :as datomic]
    [eponai.server.external.chat :as chat]
    [eponai.server.external.auth0 :as auth0]
    [eponai.server.websocket :as websocket]
    [eponai.server.external.wowza :as wowza]
    [eponai.server.external.product-search :as product-search]
    [eponai.server.external.mailchimp :as mailchimp]
    [eponai.server.external.elastic-cloud :as elastic-cloud]
    [eponai.server.external.stripe :as stripe]
    [eponai.server.external.taxjar :as taxjar]
    [eponai.server.external.aws-s3 :as s3]
    [eponai.server.external.email :as email]
    [eponai.server.external.google :as google]
    [eponai.server.external.stripe.webhooks :as stripe-webhooks]
    [eponai.server.external.firebase :as firebase]
    [eponai.server.external.vods :as vods]
    [eponai.server.external.request-handler :as request-handler]))

(def system-keys #{:system/aleph
                   :system/auth0
                   :system/aws-ec2
                   :system/aws-elb
                   :system/aws-s3
                   :system/chat
                   :system/chat-datomic
                   :system/chat-websocket
                   :system/client-env
                   :system/cloudinary
                   :system/datomic
                   :system/elastic-cloud
                   :system/firebase
                   :system/google
                   :system/handler
                   :system/mailchimp
                   :system/server-address
                   :system/stripe
                   :system/taxjar
                   :system/product-search
                   :system/wowza
                   :system/email
                   :system/auth0management
                   :system/stripe-webhooks
                   :system/vods})

(defn test-env? [env]
  (true? (::is-test-env? env)))

(defn resume-requests
  "Resumes requests when a system has been restarted."
  [system]
  (when-let [request-handler (:system/handler system)]
    (request-handler/resume-requests request-handler)))

(defn components-without-fakes [{:keys [env in-aws? in-prod?] :as config}]
  {:system/aleph           (c/using (aleph/map->Aleph (select-keys config [:handler :port :netty-options]))
                                    {:handler :system/handler})
   :system/cloudinary      (cloudinary/cloudinary
                             (:cloudinary-api-key env)
                             (:cloudinary-api-secret env)
                             in-prod?)


   :system/chat-websocket  (c/using (websocket/map->StoreChatWebsocket {})
                                    {:chat :system/chat})
   :system/client-env      (client-env/clj-client-env
                             {:client-env (select-keys env [;; Stripe
                                                            :stripe-publishable-key
                                                            ;; Auth0
                                                            :auth0-client-id :auth0-domain
                                                            ;; Wowza
                                                            :wowza-publisher-url :wowza-subscriber-url
                                                            ;; Firebase
                                                            ;; We're now using server side rendering instead of client side initialization
                                                            ;:firebase-api-key :firebase-auth-domain :firebase-database-url
                                                            ;:firebase-project-id :firebase-storage-bucket :firebase-messaging-sender-id
                                                            ])})
   :system/datomic         (datomic/map->Datomic
                             {:db-url           (:db-url env)
                              :provided-conn    (::provided-conn config)
                              :add-mocked-data? true})
   :system/google          (google/google (:google-api-key env))
   :system/product-search  (c/using (product-search/map->ProductSearch {})
                                    {:datomic :system/datomic})
   :system/server-address  (c/using (server-address/map->ServerAddress {:schema (:server-url-schema env)
                                                                        :host   (:server-url-host env)})
                                    {:aws-elb :system/aws-elb})
   :system/stripe-webhooks (stripe-webhooks/->StripeWebhooksExecutor)})

(defn real-components [{:keys [env in-prod?] :as config}]
  {:system/auth0           (c/using (auth0/map->Auth0 {:client-id     (:auth0-client-id env)
                                                       :client-secret (:auth0-client-secret env)})
                                    {:server-address :system/server-address})
   :system/auth0management (c/using (auth0/map->Auth0Management {:client-id     (:auth0management-client-id env)
                                                                 :client-secret (:auth0management-client-secret env)
                                                                 :domain        (:auth0management-domain env)})
                                    {:server-address :system/server-address})
   :system/aws-ec2         (ec2/aws-ec2)
   :system/aws-elb         (c/using (elb/map->AwsElasticBeanstalk {})
                                    {:aws-ec2 :system/aws-ec2})
   :system/aws-s3          (s3/map->AwsS3 {:bucket     (:aws-s3-bucket-photos env)
                                           :zone       (:aws-s3-bucket-photos-zone env)
                                           :access-key (:aws-access-key-id env)
                                           :secret     (:aws-secret-access-key env)})
   :system/chat            (c/using (chat/map->FirebaseStoreChat {})
                                    {:firebase     :system/firebase
                                     :sulo-datomic :system/datomic})
   :system/chat-datomic    {}
   :system/email           (email/email (:mandrill-api-key env))
   :system/elastic-cloud   (c/using (elastic-cloud/map->ElasticCloud
                                      {:cluster-hostname (:elastic-cloud-host env)
                                       ;; xpack-user format: "username:password"
                                       :xpack-user       (:elastic-cloud-xpack-user env)
                                       :index-name       (:elastic-cloud-index-name env)})
                                    {:server-address :system/server-address})
   :system/firebase        (firebase/firebase {:server-key      (:firebase-server-key env)
                                               :service-account (:firebase-service-account env)
                                               :database-url    (:firebase-database-url env)
                                               :in-prod?        in-prod?})
   :system/mailchimp       (mailchimp/mail-chimp (:mailchimp-api-key env))
   :system/stripe          (stripe/stripe (:stripe-secret-key env))
   :system/taxjar          (taxjar/taxjar (:taxjar-api-key env))
   :system/vods            (c/using (vods/map->VodStorage {})
                                    {:aws-s3 :system/aws-s3})
   :system/wowza           (wowza/wowza {:secret         (:wowza-jwt-secret env)
                                         :subscriber-url (:wowza-subscriber-url env)
                                         :publisher-url  (:wowza-publisher-url env)})})

(defn fake-components [{:keys [env] :as config}]
  {:system/auth0           (c/using (auth0/map->FakeAuth0 {})
                                    {:datomic :system/datomic})
   :system/auth0management (c/using (auth0/map->FakeAuth0 {})
                                    {:datomic :system/datomic})
   :system/aws-ec2         (ec2/aws-ec2-stub)
   :system/aws-elb         (elb/aws-elastic-beanstalk-stub)
   :system/aws-s3          (s3/aws-s3-stub)
   :system/chat            (c/using (chat/map->DatomicChat {})
                                    {:chat-datomic :system/chat-datomic
                                     :sulo-datomic :system/datomic})
   :system/chat-datomic    (datomic/map->Datomic
                             {:db-url           nil
                              :add-mocked-data? false})
   :system/elastic-cloud   (elastic-cloud/elastic-cloud-stub)
   :system/firebase        (firebase/firebase-stub)
   :system/email           (email/email-stub)
   :system/mailchimp       (mailchimp/mail-chimp-stub)
   :system/stripe          (stripe/stripe-stub (if (test-env? env)
                                                 "no-stripe-keys-in-test-environment"
                                                 (:stripe-secret-key env)))
   :system/taxjar          (taxjar/taxjar-stub)
   :system/vods            (c/using (vods/map->FakeVodStorage {})
                                    {:datomic :system/datomic})
   :system/wowza           (wowza/wowza-stub {:secret (:wowza-jwt-secret env)})})

(defn with-request-handler [system {:keys [in-prod? env] :as config}]
  (assoc system
    :system/handler
    (c/using (request-handler/map->RequestHandler
               {:disable-ssl          (::disable-ssl config)
                :disable-anti-forgery (::disable-anti-forgery config)
                :in-production?       in-prod?
                :cljs-build-id        (:cljs-build-id env "dev")})
             (vec (keys (dissoc system :system/aleph))))))

(defn real-system [{:keys [in-aws?] :as config}]
  (c/map->SystemMap (-> (merge (components-without-fakes config)
                               (real-components config)
                               (when (not in-aws?)
                                 (select-keys (fake-components config) [:system/aws-elb
                                                                        :system/aws-ec2
                                                                        :system/elastic-cloud
                                                                        :system/vods])))
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

(defn aws-env? [env]
  (some? (:aws-elb env)))

(defn- with-stripe-publishable-key [env]
  ;; We should make sure we've set a "real" stripe-publishable key
  ;; when in production (in aws).
  ;; Otherwise, just use the test key if we haven't set it already.
  (cond
    (aws-env? env)
    (do (assert (contains? env :stripe-publishable-key)
                (str "Env did not contain :stripe-publishable-key"))
        env)
    (test-env? env)
    ;; Don't require that we're using real keys in the test environemnt.
    env
    :else
    env
    ;(update env :stripe-publishable-key
    ;        (fnil identity "pk_test_VhkTdX6J9LXMyp5nqIqUTemM"))
    ))

(def env-without-empty-vals (into {}
                                  (remove (comp #(when (string? %) (empty? %)) val))
                                  environ/env))

(defn prod-system [config]
  {:post [(= (set (keys %)) system-keys)]}
  (let [env env-without-empty-vals
        config (assoc config :env (-> env (with-stripe-publishable-key))
                             :in-prod? true
                             :in-aws? (aws-env? env))]
    (real-system config)))

(defn- dev-config
  ([config] (dev-config config env-without-empty-vals))
  ([config env]
   (assoc config :env (-> env (with-stripe-publishable-key))
                 :in-prod? false
                 :in-aws? false
                 ::disable-ssl true
                 ::disable-anti-forgery true)))

(defn dev-system [config]
  {:post [(= (set (keys %)) system-keys)]}
  (fake-system (dev-config config)
               ;; Put keys under here to use the real implementation
               ;:system/stripe
               :system/firebase
               ;:system/chat
               ;:system/auth0
               ;:system/auth0management
               ;:system/email
               ;:system/mailchimp
               ;:system/taxjar
               ;:system/elastic-cloud
               ;:system/aws-s3
               ;:system/vods
               ))

(defn demo-system [config]
  (fake-system (dev-config config)))

(defn test-system [config]
  {:post [(= (set (keys %)) system-keys)]}
  ;; Never uses a real implementation.
  (fake-system (dev-config config {::is-test-env? true})))
