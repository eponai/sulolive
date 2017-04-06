(ns eponai.web.sente
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
    [cljs.core.async :as a]
    [taoensso.timbre :refer [debug warn error]]
    [taoensso.sente :as sente]
    [taoensso.sente.packers.transit :as sente.transit]))

;; Public API

(defprotocol ISenteSender
  (send-event [this event] "Sends 1-off events.")
  (subscribe-event [this event] "Sends this event everytime the connection is open.")
  (unsubscribe-event [this event] "Removes this event from being sent everytime the connection is open."))

(defprotocol IStopSente
  (stop-sente! [this]))

;; Implement this:

(defprotocol ISenteEventHandler
  (event-received [this event-id event-data]))

;; Private:

(defprotocol ISenteListener
  (connection-open [this sente-state])
  (connection-closed [this sente-state]))

(defrecord SenteHandler [open? subscribed-events event-handlers send-fn]
  ISenteListener
  (connection-open [this sente-state]
    (run! #(send-event this %) subscribed-events)
    (assoc this :open? true))
  (connection-closed [this sente-state]
    (assoc this :open? false))
  ISenteEventHandler
  (event-received [this event-id event-data]
    (run! #(event-received % event-id event-data)
          event-handlers)
    this)
  ISenteSender
  (send-event [this event]
    (send-fn event)
    this)
  (subscribe-event [this event]
    (when open?
      (send-event this event))
    (update this :subscribed-events (fnil conj #{}) event))
  (unsubscribe-event [this event]
    (update this :subscribed-events (fnil disj #{}) event)))

(defn sente-handler [event-handlers send-fn]
  (map->SenteHandler {:event-handlers    event-handlers
                      :send-fn           send-fn
                      :open?             false
                      :subscribed-events #{}}))

(defn sente-loop [sente-handler sente-chan send-chan]
  (let [sente-handler (atom sente-handler)
        close-chan (a/chan)
        send-fn-ids {::send-event        send-event
                     ::subscribe-event   subscribe-event
                     ::unsubscribe-event unsubscribe-event}
        on-sente-event
        (fn [sente-event]
          (let [[event-id event-data] (:event sente-event)]
            (try
              (cond
                ;; Ignored events
                (#{:chsk/handshake :chsk/ws-ping} event-id)
                nil

                (= :chsk/recv event-id)
                (swap! sente-handler event-received (first event-data) (second event-data))

                (= :chsk/state event-id)
                (let [[old-state new-state] event-data]
                  (when (not= (:open? old-state) (:open? new-state))
                    (if (:open? new-state)
                      (swap! sente-handler connection-open new-state)
                      (swap! sente-handler connection-closed new-state))))

                :else
                (warn "Unrecognized event id: " event-id " skipping event with data: " event-data))
              (catch :default e
                (error "Exception in sente-loop: " e)))))]
    (go-loop []
      (let [[v c] (a/alts! [sente-chan close-chan send-chan])]
        (condp = c
          close-chan
          nil

          send-chan
          (let [[send-fn-id event] v
                send-fn (get send-fn-ids send-fn-id)]
            (try
              (swap! sente-handler send-fn event)
              (catch :default e
                (error "Error from send-chan event: " v " error: " e)))
            (recur))

          sente-chan
          (do (on-sente-event v)
              (recur)))
        (debug "Closed sente-loop!")))
    (fn close-fn []
      (a/close! close-chan))))

(defn start!
  "Given a sente endpoint and objects implementing ISenteEventHandler, return
   a stoppable object that can subscribe, unsubscribe and send events to the server.
   Received events are given to every event-handler."
  [endpoint event-handlers]
  (let [{:keys [send-fn ch-recv chsk]}
        (sente/make-channel-socket! endpoint {:packer (sente.transit/get-transit-packer)
                                              ;; TODO: Use :ws for websockets once we've
                                              ;;       enabled elastic beanstalk to run websockets.
                                              :type   :ajax})
        send-chan (a/chan (a/sliding-buffer 1000))
        handler (sente-handler event-handlers send-fn)
        close-fn (sente-loop handler ch-recv send-chan)]
    (reify
      IStopSente
      (stop-sente! [this]
        (sente/chsk-disconnect! chsk)
        (close-fn))
      ISenteSender
      (send-event [this event]
        (a/put! send-chan [::send-event event]))
      (subscribe-event [this event]
        (a/put! send-chan [::subscribe-event event]))
      (unsubscribe-event [this event]
        (a/put! send-chan [::unsubscribe-event event])))))

(defn delayed-start
  "Returns a sente which is started once it is used."
  [endpoint event-handlers]
  (let [delayed (delay (start! endpoint event-handlers))]
    (reify
      IStopSente
      (stop-sente! [this]
        (stop-sente! (force delayed)))
      ISenteSender
      (send-event [this event]
        (send-event (force delayed) event))
      (subscribe-event [this event]
        (subscribe-event (force delayed) event))
      (unsubscribe-event [this event]
        (unsubscribe-event (force delayed) event)))))