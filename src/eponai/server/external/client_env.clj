(ns eponai.server.external.client-env)

(defprotocol IClientEnvironment
  (env-map [this]))

(defrecord ClientEnvironment [client-env]
  IClientEnvironment
  (env-map [this]
    client-env))
