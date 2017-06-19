(ns eponai.server.log
  (:require
    [taoensso.timbre :as timbre]
    [clojure.core.async :as async]
    [eponai.common.format.date :as date]
    [com.stuartsierra.component :as component]))

(defprotocol ILogger
  (log [this msg]))

;; ------- Log manipulation functions

(defn with [logger f]
  (reify
    ILogger
    (log [_ msg]
      (log logger (f msg)))))

(defn- logging-thread [logger in]
  (async/thread
    (loop []
      (when-some [v (async/<!! in)]
        (try
          (log logger v)
          (catch Throwable e
            (try
              (timbre/error ::async-error
                            e
                            {:exception-class   (.getClass e)
                             :exception-message (.getMessage e)})
              (catch Throwable _))))
        (recur)))))

(defn async-logger
  ([logger]
   (async-logger logger 1))
  ([logger threads]
   (let [capacity 100
         buf (async/sliding-buffer capacity)
         msg-chan (async/chan buf)]
     (doall (take threads (repeatedly #(logging-thread logger msg-chan))))
     (reify
       ILogger
       (log [_ msg]
         (when (== capacity (count buf))
           (timbre/debug "logging capacity has been reached. Will drop message."))
         (async/put! msg-chan msg))
       component/Lifecycle
       (stop [this]
         (async/close! msg-chan))))))

;; ------ Logging functions

(defn- log! [level logger id data]
  {:pre [(and (keyword? id) (some? (namespace id)))
         (map? data)]}
  (log logger {:id id
               :level level
               :data data
               :millis (System/currentTimeMillis)}))

(defn info! [logger id data]
  (log! :info logger id data))

(defn warn! [logger id data]
  (log! :warn logger id data))

(defn error! [logger id data]
  (log! :error logger id data))

;; ------ Loggers

(defrecord TimbreLogger []
  ILogger
  (log [this msg]
    (timbre/log (:level msg) (:id msg) " " (:data msg))))
