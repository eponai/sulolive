(ns eponai.server.external.elastic-cloud
  (:require
    [eponai.server.log :as log]
    [com.stuartsierra.component :as component]
    [suspendable.core :as suspendable]
    [taoensso.timbre :refer [error debug]]
    [cheshire.core :as cheshire])
  (:import (org.elasticsearch.common.settings Settings)
           (org.elasticsearch.client.transport TransportClient)
           (org.elasticsearch.common.transport InetSocketTransportAddress)
           (java.net InetAddress Inet6Address Inet4Address UnknownHostException)
           (org.elasticsearch.action.bulk BulkResponse)
           (org.elasticsearch.xpack.client PreBuiltXPackTransportClient)))

;; xpack example:
;; https://github.com/elastic/found-shield-example

(defn- add-transport-addresses [client cluster-hostname]
  ;; ipv6 doesn't work from within docker?
  ;; so it won't work for us?
  (let [ipv6? false
        ipv4? true
        port 9343]
    (try
      (doseq [address (InetAddress/getAllByName cluster-hostname)]
        (when (or (and ipv6? (instance? Inet6Address address))
                  (and ipv4? (instance? Inet4Address address)))
          (debug "Adding transport address: " address " port: " port)
          (.addTransportAddress client (InetSocketTransportAddress. ^InetAddress address port))))
      (catch UnknownHostException e
        (error "Unable to get host: " (.getMessage e))))))

;; This way of getting the cluster-id is taken from:
;; https://github.com/elastic/found-shield-example/blob/master/src/main/java/org/elasticsearch/cloud/transport/example/TransportExample.java#L54
(defn- ^String cluster-id-from-host [host]
  (first (.split host "\\." 2)))

(defrecord ElasticCloud [^String cluster-hostname
                         ;; shield-user being "username:password"
                         ^String xpack-user]
  component/Lifecycle
  (start [this]
    (if (:client this)
      this
      (let [settings (-> (Settings/builder)
                         (.put "client.transport.nodes_sampler_interval" "5s")
                         (.put "client.transport.sniff", false)
                         (.put "transport.tcp.compress", true)
                         (.put "cluster.name" (cluster-id-from-host cluster-hostname))
                         (.put "xpack.security.transport.ssl.enabled" true)
                         (.put "request.headers.X-Found-Cluster", "${cluster.name}")
                         (.put "xpack.security.user" xpack-user)
                         (.build))
            client (PreBuiltXPackTransportClient. ^Settings settings [])]
        (add-transport-addresses client cluster-hostname)
        (assoc this :client client))))
  (stop [this]
    (when-let [c (:client this)]
      (try
        (.close ^TransportClient c)
        (catch Exception ignore)))
    (dissoc this :client))
  suspendable/Suspendable
  (suspend [this]
    this)
  (resume [this old-this]
    (if-let [c (:client old-this)]
      (assoc this :client c)
      (do (component/stop old-this)
          (component/start this))))

  log/IBulkLogger
  (bulk-size [this]
    ;; TODO: What's a good bulk-size?
    25)
  (log-bulk [this messages]
    (debug "Sending messages to elastic: " messages)
    (let [^TransportClient client (:client this)
          bulk-builder (.prepareBulk client)
          _ (doseq [msg messages]
              (.add bulk-builder
                    (-> (.prepareIndex client "second_index" (str (:id msg)))
                        (.setSource (let [s (cheshire/generate-string (assoc (:data msg)
                                                                        "@timestamp" (:millis msg)
                                                                        :level (:level msg)
                                                                        ))]
                                      (debug "json: " s)
                                      s)))))
          bulk-response ^BulkResponse (.get bulk-builder)]
      (when (.hasFailures bulk-response)
        (error "elastic cloud bulk request failure: " (.buildFailureMessage bulk-response))))))
