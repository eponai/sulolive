(ns eponai.client.client-env
  (:require
    [eponai.common.database :as db]))

(defprotocol IClientEnvironment
  (env-map [this]))

(defn clj-client-env [client-env]
  (reify IClientEnvironment
    (env-map [this]
      client-env)))

(defn cljs-client-env [reconciler-atom]
  (reify IClientEnvironment
    (env-map [this]
      (db/singleton-value (db/to-db @reconciler-atom) :ui.singleton.client-env/env-map))))

(defn get-key [client-env k]
  (-> client-env
      (env-map)
      (get k)))