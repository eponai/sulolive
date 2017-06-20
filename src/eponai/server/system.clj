(ns eponai.server.system
  (:require
    [com.stuartsierra.component :as c]
    [taoensso.timbre :refer [debug]]
    [eponai.server.external.cloudinary :as cloudinary]
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
    [eponai.server.external.product-search :as product-search]
    [eponai.server.external.mailchimp :as mailchimp]
    [eponai.server.external.elastic-cloud :as elastic-cloud]
    [eponai.server.external.stripe :as stripe]
    [eponai.server.external.taxjar :as taxjar]
    [eponai.server.external.aws-s3 :as s3]
    [eponai.server.external.email :as email]
    [eponai.server.external.client-env :as client-env]
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
                   :system/handler
                   :system/mailchimp
                   :system/server-address
                   :system/stripe
                   :system/taxjar
                   :system/product-search
                   :system/wowza
                   :system/email})

(defn resume-requests
  "Resumes requests when a system has been restarted."
  [system]
  (when-let [request-handler (:system/handler system)]
    (request-handler/resume-requests request-handler)))

(defn components-without-fakes [{:keys [env in-aws?] :as config}]
  {:system/aleph          (c/using (aleph/map->Aleph (select-keys config [:handler :port :netty-options]))
                                   {:handler :system/handler})
   :system/cloudinary     (cloudinary/cloudinary
                            (:cloudinary-api-key env)
                            (:cloudinary-api-secret env)
                            (:in-prod? config))
   :system/chat           (c/using (chat/map->DatomicChat {})
                                   {:chat-datomic :system/chat-datomic
                                    :sulo-datomic :system/datomic})
   :system/chat-datomic   (datomic/map->Datomic
                            {:db-url           (:chat-db-url env)
                             :add-mocked-data? false})
   :system/chat-websocket (c/using (websocket/map->StoreChatWebsocket {})
                                   {:chat :system/chat})
   :system/client-env     (client-env/map->ClientEnvironment
                            {:client-env (select-keys env [:stripe-publishable-key])})
   :system/datomic        (datomic/map->Datomic
                            {:db-url           (:db-url env)
                             :provided-conn    (::provided-conn config)
                             :add-mocked-data? true})
   :system/product-search (c/using (product-search/map->ProductSearch {})
                                   {:datomic :system/datomic})
   :system/server-address (c/using (server-address/map->ServerAddress {:schema (:server-url-schema env)
                                                                       :host   (:server-url-host env)})
                                   {:aws-elb :system/aws-elb})})

(defn real-components [{:keys [env] :as config}]
  {:system/auth0         (c/using (auth0/map->Auth0 {:client-id     (:auth0-client-id env)
                                                     :client-secret (:auth0-client-secret env)})
                                  {:server-address :system/server-address})
   :system/aws-ec2       (ec2/aws-ec2)
   :system/aws-elb       (c/using (elb/map->AwsElasticBeanstalk {})
                                  {:aws-ec2 :system/aws-ec2})
   :system/aws-s3        (s3/map->AwsS3 {:bucket     (:aws-s3-bucket-photos env)
                                         :zone       (:aws-s3-bucket-photos-zone env)
                                         :access-key (:aws-access-key-id env)
                                         :secret     (:aws-secret-access-key env)})
   :system/email         (email/email {:host (:smtp-host env)
                                       ;:port (Long/parseLong (:smtp-port env))
                                       :ssl  true
                                       :user (:smtp-user env)
                                       :pass (:smtp-password env)})
   :system/elastic-cloud (c/using (elastic-cloud/map->ElasticCloud
                                    {:cluster-hostname (:elastic-cloud-host env)
                                     ;; xpack-user format: "username:password"
                                     :xpack-user       (:elastic-cloud-xpack-user env)
                                     :index-name       (:elastic-cloud-index-name env)})
                                  {:server-address :system/server-address})
   :system/mailchimp     (mailchimp/mail-chimp (:mailchimp-api-key env))
   :system/stripe        (stripe/stripe (:stripe-secret-key env))
   :system/taxjar        (taxjar/taxjar (:taxjar-api-key env))
   :system/wowza         (wowza/wowza {:secret         (:wowza-jwt-secret env)
                                       :subscriber-url (:wowza-subscriber-url env)
                                       :publisher-url  (:wowza-publisher-url env)})})

(defn fake-components [{:keys [env] :as config}]
  {:system/auth0         (c/using (auth0/map->FakeAuth0 {})
                                  {:datomic :system/datomic})
   :system/aws-ec2       (ec2/aws-ec2-stub)
   :system/aws-elb       (elb/aws-elastic-beanstalk-stub)
   :system/aws-s3        (s3/aws-s3-stub)
   :system/elastic-cloud (elastic-cloud/elastic-cloud-stub)
   :system/email         (email/email-stub)
   :system/mailchimp     (mailchimp/mail-chimp-stub)
   :system/stripe        (stripe/stripe-stub (:stripe-secret-key env))
   :system/taxjar        (taxjar/taxjar-stub)
   :system/wowza         (wowza/wowza-stub {:secret (:wowza-jwt-secret env)})})

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
                                                                        :system/elastic-cloud])))
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
  (if (aws-env? env)
    (do (assert (contains? env :stripe-publishable-key)
                (str "Env did not contain :stripe-publishable-key"))
        env)
    (update env :stripe-publishable-key
            (fnil identity "pk_test_VhkTdX6J9LXMyp5nqIqUTemM"))))

(defn prod-system [config]
  {:post [(= (set (keys %)) system-keys)]}
  (let [config (assoc config :env (-> environ/env (with-stripe-publishable-key))
                             :in-prod? true
                             :in-aws? (aws-env? environ/env))]
    (real-system config)))

(defn- dev-config [config]
  (assoc config :env (-> environ/env (with-stripe-publishable-key))
                :in-prod? false
                :in-aws? false
                ::disable-ssl true
                ::disable-anti-forgery true))

(defn dev-system [config]
  {:post [(= (set (keys %)) system-keys)]}
  (fake-system (dev-config config)
               ;; Put keys under here to use the real implementation
               :system/stripe
               ;:system/auth0
               ;:system/email
               ;:system/mailchimp
               ;:system/taxjar

               ;:system/elastic-cloud
               ))

(defn test-system [config]
  {:post [(= (set (keys %)) system-keys)]}
  ;; Never uses a real implementation.
  (fake-system (dev-config config)))
