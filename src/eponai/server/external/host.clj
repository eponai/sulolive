(ns eponai.server.external.host
  (:require [eponai.server.external.aws-elb :as elb]))

(defprotocol IServerAddress
  (webserver-url [this] "Provides the url to this server"))

(defrecord ServerAddress [aws-elb schema host]
  IServerAddress
  (webserver-url [this]
    (if (elb/is-staging? aws-elb)
      (str (elb/env-url aws-elb))
      (str schema "://" host))))
