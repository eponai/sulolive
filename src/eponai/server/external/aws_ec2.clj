(ns eponai.server.external.aws-ec2
  (:require
    [amazonica.aws.ec2 :as ec2]
    [com.stuartsierra.component :as component])
  (:import [com.amazonaws.util EC2MetadataUtils]))

(defprotocol IAWSEc2
  (find-instance [this instance-id])
  (find-this-instance [this]))

(defn elastic-beanstalk-env-name [instance]
  (->> (:tags instance)
       (some (fn [{:keys [key value]}]
               (when (= key "elasticbeanstalk:environment-name")
                 value)))))

(defn- find-the-instance
  "Given a quite hairy ec2/describe-instances request, find and return the instance."
  [description id]
  (->> description
       (some (fn [[_ v]]
               (->> v
                    (some (fn [r]
                            (->> (:instances r)
                                 (some (fn [i]
                                         (when (= id (:instance-id i))
                                           i)))))))))))

(defn aws-ec2 []
  (reify
    IAWSEc2
    (find-instance [this instance-id]
      (find-the-instance (ec2/describe-instances :instance-ids [instance-id])
                         instance-id))
    (find-this-instance [this]
      (find-instance this (EC2MetadataUtils/getInstanceId)))))

(defn aws-ec2-stub []
  (reify IAWSEc2
    (find-instance [this instance-id]
      nil)
    (find-this-instance [this]
      nil)))