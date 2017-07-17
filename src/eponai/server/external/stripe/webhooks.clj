(ns eponai.server.external.stripe.webhooks
  (:require
    [postal.core :as postal]
    [com.stuartsierra.component :as component]
    [suspendable.core :as suspendable]
    [taoensso.timbre :refer [debug]]
    [eponai.server.external.email :as email]
    [eponai.common.database :as db]
    [eponai.common.format :as f]
    [eponai.server.api.store :as store]
    [eponai.server.external.stripe.format :as stripe-format]
    [eponai.server.log :as log])
  (:import (java.util.concurrent ExecutorService TimeUnit Executors)))

(defprotocol IStripeWebhooksExecutor
  ;; Iternal, can be used for testing.
  (-get-executor! [this] "Gets the executor for this webhook-id. Might create a new executor, thus the bang(!)")
  (execute-async [this id f] "Executes a webhook by id, async. Ignores the result"))

(defrecord StripeWebhooksExecutor []
  IStripeWebhooksExecutor
  (-get-executor! [this]
    (:executor this))
  (execute-async [this id f]
    (let [executor (-get-executor! this)]
      (debug "Executing webhook-id: " id)
      (locking this
        (.submit ^ExecutorService executor ^Runnable f))))

  component/Lifecycle
  (start [this]
    (if (:executor this)
      this
      ;; A single threaded executor for executing all webhooks in order
      (do
        (assoc this :executor (Executors/newSingleThreadExecutor)))))
  (stop [this]
    (when-let [executor (:executor this)]
      (try (.shutdown executor) (catch Exception _))
      (when-not (.awaitTermination executor 1 TimeUnit/SECONDS)
        (debug "Was unable to terminate StripeWebhooksExecutor within the timelimit")))
    (dissoc this :executor))

  suspendable/Suspendable
  (suspend [this]
    this)
  (resume [this old-this]
    (if-let [e (:executor old-this)]
      (assoc this :executor e)
      (do (component/stop old-this)
          (component/start this)))))

;; ############### SULO ACCOUNT ####################

(defn send-order-receipt [{:keys [state system]} charge]
  (let [order-uuid (f/str->uuid (get-in charge [:metadata :order_uuid]))
        order (db/pull-one-with (db/db state)
                                [:db/id
                                 {:order/items [{:order.item/parent [{:store.item/_skus [:store.item/name
                                                                                         :store.item/price
                                                                                         {:store.item/photos [{:store.item.photo/photo [:photo/id]}]}]}
                                                                     :store.item.sku/variation]}
                                                :order.item/type
                                                :order.item/amount]}
                                 {:order/shipping [:shipping/name
                                                   {:shipping/address [{:shipping.address/country [:country/name]}
                                                                       :shipping.address/region
                                                                       :shipping.address/postal
                                                                       :shipping.address/locality
                                                                       :shipping.address/street
                                                                       :shipping.address/street2]}]}
                                 {:order/store [:db/id
                                                :store/username
                                                {:store/profile [:store.profile/name]}
                                                {:store/owners [{:store.owner/user [:user/email]}]}]}
                                 {:order/user [:user/email]}]
                                {:where   '[[?e :order/uuid ?uuid]]
                                 :symbols {'?uuid order-uuid}})]
    (email/-send-order-receipt (:system/email system) {:order order :charge charge})
    (email/-send-order-notification (:system/email system) {:order order :charge charge})))

(defmulti handle-account-webhook (fn [{:keys [logger webhook-event]} _]
                                   (debug "Webhook event: " webhook-event)
                                   (log/info! logger ::stripe-platform-webhook {:event webhook-event})
                                   (:type webhook-event)))

(defmethod handle-account-webhook :default
  [{:keys [webhook-event]} object]
  (debug "No handler implemented for account webhook event " (:type webhook-event) ", doing nothing."))


(defmethod handle-account-webhook "charge.captured"
  [{:keys [state system webhook-event] :as env} charge]
  (debug "Will handle captured charged:  " webhook-event)
  (execute-async (:system/stripe-webhooks system)
                 "charge.captured"
                 (fn []
                   (send-order-receipt env charge)))
  nil)

(defmethod handle-account-webhook "charge.succeeded"
  [{:keys [state system webhook-event] :as env} charge]
  (debug "Will handle captured succeeded:  " webhook-event)
  (execute-async (:system/stripe-webhooks system)
                 "charge.succeeded"
                 (fn []
                   (send-order-receipt env charge)))
  nil)

;; ############## CONNECTED ACCOUNT ################

(defmulti handle-connected-webhook (fn [{:keys [logger webhook-event]} event-object]
                                     (debug "Webhook event: " (:type webhook-event))
                                     (log/info! logger ::stripe-connected-webhook {:event webhook-event})
                                     (:type webhook-event)))

(defmethod handle-connected-webhook :default
  [{:keys [webhook-event]} event-object]
  (debug "No handler implemented for connected webhook event " (:type webhook-event) ", doing nothing."))

(defmethod handle-connected-webhook "account.updated"
  [{:keys [system] :as env} account]
  ;; Run webhooks async and don't care about the result
  (execute-async (:system/stripe-webhooks system)
                 "account.updated"
                 (fn []
                   (store/stripe-account-updated env (stripe-format/stripe->account account))))
  ;(utils/account-updated env (stripe-format/stripe->account account))
  nil)