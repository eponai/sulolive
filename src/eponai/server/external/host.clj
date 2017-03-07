(ns eponai.server.external.host
  (:require [eponai.server.external.aws-elb :as elb]))

(defprotocol IServerAddress
  (webserver-url [this] "Provides the url to this server"))

(defn server-address [schema host]
  (reify IServerAddress
    (webserver-url [this]
      (str schema "://" host))))

(defn prod-server-address [aws-elb server-address]
  (reify IServerAddress
    (webserver-url [this]
      (if (elb/is-staging? aws-elb)
        (str (elb/env-url aws-elb))
        (webserver-url server-address)))))


