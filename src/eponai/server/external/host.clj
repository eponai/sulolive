(ns eponai.server.external.host
  (:require
    [com.stuartsierra.component :as component]
    [eponai.server.external.aws-elb :as elb]))

(defprotocol IServerAddress
  (webserver-url [this] "Provides the url to this server"))

(defrecord ServerAddress [aws-elb schema host]
  component/Lifecycle
  (start [this]
    (assoc this :url (str schema "://" host)))
  (stop [this]
    (dissoc this :url))

  IServerAddress
  (webserver-url [this]
    (if (elb/is-staging? aws-elb)
      (str (elb/env-url aws-elb))
      (:url this))))
