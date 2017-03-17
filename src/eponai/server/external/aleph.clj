(ns eponai.server.external.aleph
  (:require [com.stuartsierra.component :as component]
            [aleph.http :as aleph]
            [aleph.netty]
            [taoensso.timbre :refer [debug error]])
  (:import (io.netty.channel ChannelPipeline)
           (io.netty.handler.codec.http HttpContentCompressor)
           (io.netty.handler.stream ChunkedWriteHandler)))

(defrecord Aleph [handler port netty-options]
  component/Lifecycle
  (start [this]
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
          server (aleph/start-server (:handler handler) server-options)]
      (assoc this :server server)))
  (stop [this]
    (if-let [server (:server this)]
      (do (debug "Stopping aleph..")
          (.close server)
          ;; (aleph.netty/wait-for-close server)
          (debug "Stopped aleph!"))
      (debug "There was no server to stop :o"))
    (dissoc this :server)))
