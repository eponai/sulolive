(ns eponai.server.external.aleph
  (:require [com.stuartsierra.component :as component]
            [suspendable.core :as suspendable]
            [aleph.http :as aleph]
            [aleph.netty]
            [taoensso.timbre :refer [debug error]])
  (:import (io.netty.channel ChannelPipeline)
           (io.netty.handler.codec.http HttpContentCompressor)
           (io.netty.handler.stream ChunkedWriteHandler)
           (java.net BindException)))

(defn close-quietly [server]
  (try
    (some-> server (.close))
    (catch Throwable e)))

(defrecord Aleph [handler port netty-options]
  component/Lifecycle
  (start [this]
    (if (:server this)
      this
      (let [gzip-pipeline (fn [^ChannelPipeline pipeline]
                            (doto pipeline
                              (.addBefore "request-handler" "deflater" (HttpContentCompressor.))
                              (.addBefore "request-handler" "streamer" (ChunkedWriteHandler.))))
            server-options (-> {:port port}
                               (merge netty-options)
                               (update :pipeline-transform (fn [current-transform]
                                                             (fn [^ChannelPipeline pipeline]
                                                               (cond-> pipeline
                                                                       (:enable-gzip netty-options true)
                                                                       (gzip-pipeline)
                                                                       (some? current-transform)
                                                                       (current-transform))))))
            server (try
                     (aleph/start-server (:handler handler) server-options)
                     (catch BindException e
                       (error "Unable to start aleph: " e)))]
        (assoc this :server server))))
  (stop [this]
    (when-let [server (:server this)]
      (debug "Stopping aleph..")
      (close-quietly server)
      ;; (aleph.netty/wait-for-close server)
      (debug "Stopped aleph!"))
    (dissoc this :server))
  suspendable/Suspendable
  (suspend [this]
    (close-quietly (:server this))
    this)
  (resume [this old-this]
    (if (:server this)
      this
      ;; Wait for the old one to really close, then try restarting this one.
      (do
        (some-> (:server old-this) (aleph.netty/wait-for-close))
        (let [restarted (component/start this)]
          (if (:server restarted)
            (do (debug "Successfully restarted Aleph.")
                restarted)
            (do (debug "Unable to restart Aleph!")
                (throw (ex-info "Unable to resume Aleph" {:component this})))))))))
