(ns eponai.common.database.transact
  (:require [eponai.common.format :as f]
    #?(:clj [datomic.api :as d]
       :cljs [datascript.core :as d])))

(defn transact
  "Transact a collecion of entites into datomic.
  Throws ExceptionInfo if transaction failed."
  [conn txs]
  (try
    (let [ret @(d/transact conn txs)]
      ret)
    (catch #?(:clj Exception :cljs :default) e
      (let [#?@(:clj  [msg (.getMessage e)]
                :cljs [msg (.-message e)])]
        (throw (ex-info msg
                        {:cause     ::transaction-error
                         :data      {:conn conn
                                     :txs  txs}
                         :message   msg
                         :exception e
                         #?@(:clj [:status :eponai.server.http/service-unavailable])}))))))
