(ns eponai.server.websocket
  (:require
    [clojure.core.async :as a :refer [go <!]]
    [taoensso.sente :as sente]
    [taoensso.sente.server-adapters.aleph :as sente.aleph]
    [taoensso.sente.packers.transit :as sente-transit]
    [taoensso.timbre :refer [debug error]]))

(defn chat-websocket []
  (let [sente-channel (sente/make-channel-socket! (sente.aleph/get-sch-adapter)
                                                  {:packer     (sente-transit/get-transit-packer)
                                                   :user-id-fn (fn [ring-req]
                                                                 (or (get-in ring-req [:identity :email])
                                                                     ;; client-id is assoc'ed by sente
                                                                     ;; before calling this fn.
                                                                     (:client-id ring-req)))})
        control-chan (a/chan 1)
        event-handler (fn [{:keys [event id ?data send-fn ?reply-fn uid ring-req client-id]}]
                        (debug "Client-id: " [:uid uid :client client-id] " sent chat event: " event))]
    (go
      (loop []
        (if-let [control-value (a/poll! control-chan)]
          (debug "Exiting chat websocket due to value on control-channel: " control-value)
          (do
            (try
              (event-handler (<! (:ch-recv sente-channel)))
              (catch Exception e
                (error "Exception in chat-websocket: " e)
                (debug "Will continue chat-websocket until there's an Error."))
              (catch Error e
                (error "Error in chat-websocket: " e)
                (a/put! control-chan e)))
            (recur)))))
    sente-channel))
