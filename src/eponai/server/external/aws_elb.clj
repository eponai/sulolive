(ns eponai.server.external.aws-elb
  (:require [amazonica.aws.elasticbeanstalk :as elb]
            [clojure.string :as str]
            [environ.core :as env])
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

(defn aws-elastic-beanstalk [environment-name]
  (let [is-in-staging? (fn []
                         (let [env (find-environment environment-name)]
                           (= (or (env/env :aws-elb-staging-url-prefix)
                                  "sulo-staging")
                              (-> (:cname env)
                                  (str/split #"\.")
                                  (first)))))
        ;; We can cache this because we'll only go from staging->production once
        ;; before restarting or upgrading it.
        ;; TODO: Figure out if this will bite us in the ass.
        is-in-staging? (cached-once-false is-in-staging?)]
    (reify
      IAWSElasticBeanstalk
      (is-staging? [_]
        (is-in-staging?))
      (env-url [_]
        (str "https://" (:cname (find-environment environment-name)))))))

(defn aws-elastic-beanstalk-stub []
  (reify
    IAWSElasticBeanstalk
    (is-staging? [this]
      false)
    (env-url [this]
      (throw (ex-info "Unsupported operation for this stub" {})))))