(ns eponai.server.websocket
  (:require
    [clojure.core.async :as a :refer [go <!]]
    [eponai.server.external.chat :as chat]
    [com.stuartsierra.component :as component]
    [taoensso.sente :as sente]
    [taoensso.sente.server-adapters.aleph :as sente.aleph]
    [taoensso.sente.packers.transit :as sente-transit]
    [taoensso.timbre :refer [debug error warn]]
    [taoensso.timbre :as timbre]))

(defrecord StoreChatWebsocket [chat]
  component/Lifecycle
  (start [this]
    (let [store-id-stream (chat/chat-update-stream chat)
          {:keys [ch-recv connected-uids send-fn] :as sente-channel}
          (sente/make-channel-socket! (sente.aleph/get-sch-adapter)
                                      {:packer     (sente-transit/get-transit-packer)
                                       :user-id-fn (fn [ring-req]
                                                     (or (get-in ring-req [:identity :email])
                                                         ;; client-id is assoc'ed by sente
                                                         ;; before calling this fn.
                                                         (:client-id ring-req)))})
          control-chan (a/chan 1)
          store-id->uids (atom {})
          event-handler (fn [{:keys [event id ?data ?reply-fn uid ring-req client-id]}]
                          (let [[event-id event-data] event]
                            (condp = event-id
                              :store-chat/start-listening!
                              (if (some? (:store-id event-data))
                                (swap! store-id->uids update (:store-id event-data) (fnil conj #{}) uid)
                                (debug "No :store-id key found in event: " event))
                              :store-chat/stop-listening!
                              (if (some? (:store-id event-data))
                                (swap! store-id->uids update (:store-id event-data (fnil disj #{}) uid))
                                (debug "No :store-id key found in event: " event))
                              (debug "Unhandled event: " [:uid uid :event event]))))]
      (go
        (loop []
          (debug "Awaiting new value for websocket")
          (let [[v c] (a/alts! [ch-recv store-id-stream control-chan])]
            (debug "Got value from channel: " c)
            (condp = c
              control-chan (debug "Exiting chat websocket due to value on control-channel: " v)
              ch-recv (do
                        (try
                          (event-handler v)
                          (catch Exception e
                            (error "Exception in chat-websocket: " e)
                            (debug "Will continue chat-websocket until there's an Error."))
                          (catch Error e
                            (error "Error in chat-websocket: " e)
                            (a/put! control-chan e)))
                        (recur))
              store-id-stream (let [store-id (:store-id v)]
                                (try
                                  (if (nil? store-id)
                                    (warn "No store-id found in value returned from store-id stream. Value was: " v)
                                    (doseq [uid (get @store-id->uids store-id)]
                                      (if (contains? (:any @connected-uids) uid)
                                        (do
                                          (debug "Sending store id update to uid: " uid [:store-id store-id])
                                          (timbre/with-level
                                            :trace
                                            (send-fn uid
                                                     [:store-chat/update {:store-id store-id}]
                                                     {:flush? true}))
                                          (debug "Sent store id update to uid: " uid))
                                        (do
                                          (debug "uid was no longer connected to the server(?). Removing: " uid
                                                 " from " store-id)
                                          ;; TODO: REMOVE THIS debug, it's for testing only right now.
                                          (debug "connected-uids: " @connected-uids)
                                          (swap! store-id->uids update store-id disj uid)))))
                                  ;; TODO: DRY this (it's a copy from the handler above
                                  (catch Exception e
                                    (error "Exception in chat-websocket: " e)
                                    (debug "Will continue chat-websocket until there's an Error."))
                                  (catch Error e
                                    (error "Error in chat-websocket: " e)
                                    (a/put! control-chan e)))
                                (recur))))))
      (assoc sente-channel
        :stop-fn #(a/close! control-chan))))
  (stop [this]
    ((:stop-fn this))))
