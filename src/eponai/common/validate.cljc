(ns eponai.common.validate
  (:require [clojure.string :as s])
  #?(:clj
      (:import [clojure.lang ExceptionInfo])))

(defn- error [msg data]
  (let [message (str "Validation failed, " msg)]
    (throw (ex-info message {:cause   ::validation-error
                             :data    data
                             :message message
                             #?@(:clj [:status :eponai.server.http/unprocessable-entity])}))))


(defn validate
  "Validate with the given message and function on the input args.
  Throws an ex-info if validation failed."
  [msg f & args]
  (let [data {:fn (str f) :params (s/join " " args)}]
    (try
      (if (apply f args)
        true
        (error msg data))
      (catch #?(:clj ExceptionInfo :cljs cljs.core.ExceptionInfo) e
        (throw e))
      (catch #?(:clj Exception :cljs :default) e
        (error msg (assoc data :exception e))))))


(defn valid-user-transaction? [user-tx]
  (let [required-fields #{:transaction/uuid
                          :transaction/title
                          :transaction/date
                          :transaction/amount
                          :transaction/currency
                          :transaction/created-at
                          #?(:clj :transaction/budget)}]
    (validate (str "user tx: " user-tx " had nil keys for " (filterv #(nil? (get user-tx %)) required-fields))
              every? #(some? (get user-tx %)) required-fields)))
