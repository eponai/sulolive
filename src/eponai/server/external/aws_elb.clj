(ns eponai.server.external.aws-elb
  (:require [amazonica.aws.elasticbeanstalk :as elb]
            [clojure.string :as str]
            [environ.core :as env]
            [com.stuartsierra.component :as component]
            [eponai.server.external.aws-ec2 :as ec2])
  (:import [com.amazonaws.regions Regions]))

(defprotocol IAWSElasticBeanstalk
  (is-staging? [this] "Return true if we're in the staging environment in elastic beanstalk")
  (env-url [this] "Return the environment of this environment"))

(def get-current-region (memoize (fn [] (.getName (Regions/getCurrentRegion)))))

(defn cached-once-false
  "Takes a function f and returns a new function that once f returns false, it'll return false."
  [f]
  (let [a (atom true)]
    (fn [& args]
      (locking a
        (when @a
          (swap! a (fn [_] (apply f args))))))))

(defn find-environment [env-name]
  (let [envs (elb/describe-environments {:endpoint (get-current-region)})]
    (some #(when (= (:environment-name %) env-name) %)
          (:environments envs))))

(defrecord AwsElasticBeanstalk [aws-ec2]
  component/Lifecycle
  (start [this]
    (letfn [(is-in-staging? [environment-name]
              (when-let [env (find-environment environment-name)]
                (= (or (env/env :aws-elb-staging-url-prefix)
                       "sulo-staging")
                   (-> (:cname env)
                       (str/split #"\.")
                       (first)))))]
      ;; We can cache this because we'll only go from staging->production once
      ;; before restarting or upgrading it.
      ;; TODO: Figure out if this will bite us in the ass.
      (assoc this :is-in-staging? (cached-once-false is-in-staging?)
                  :environment-name (some-> aws-ec2
                                            (ec2/find-this-instance)
                                            (ec2/elastic-beanstalk-env-name)))))
  (stop [this]
    (dissoc this :is-in-staging? :environment-name))
  IAWSElasticBeanstalk
  (is-staging? [this]
    ((:is-in-staging? this) (:environment-name this)))
  (env-url [this]
    (str "https://" (:cname (find-environment (:environment-name this))))))

(defn aws-elastic-beanstalk-stub []
  (reify
    IAWSElasticBeanstalk
    (is-staging? [this]
      false)
    (env-url [this]
      (throw (ex-info "Unsupported operation for this stub" {})))))