(ns eponai.server.external.stripe
  (:require
    [datomic.api :as d]
    [eponai.common.database.pull :as p]
    [eponai.common.database.transact :as transact]
    [eponai.common.format :as f]
    [eponai.server.email :as email]
    [eponai.server.http :as h]
    [taoensso.timbre :refer [debug error info]])
  (:import
    (com.stripe Stripe)
    (com.stripe.exception CardException)
    (com.stripe.model Customer)
    (com.stripe.model Card)
    (com.stripe.model Subscription)))
;; ########### Stripe objects ################

(defn customer [customer-id]
  (when customer-id
    (let [^Customer customer (Customer/retrieve customer-id)
          _ (debug "Customer object: " customer)
          default-source (.getDefaultSource customer)
          ^Card card (when default-source
                       (.retrieve (.getSources customer) (.getDefaultSource customer)))
          ]
      {:id    (.getId customer)
       :email (.getEmail customer)
       :card  (when card
                {:exp-month (.getExpMonth card)
                 :exp-year  (.getExpYear card)
                 :name      (.getName card)
                 :last4     (.getLast4 card)
                 :brand     (.getBrand card)
                 })
       }
      )))
;; ######################################

(defn obj->subscription-map [^Subscription stripe-obj]
  {:stripe.subscription/id      (.getId stripe-obj)
   :stripe.subscription/status  (keyword (.getStatus stripe-obj))
   :stripe.subscription/period-end (* 1000 (.getCurrentPeriodEnd stripe-obj))})

(defn json->subscription-map [{:keys [id current_period_end status]}]
  {:stripe.subscription/id      id
   :stripe.subscription/status  (keyword status)
   :stripe.subscription/period-end (* 1000 current_period_end)})

(declare stripe-action)

(defn stripe [api-key k params]
  (info "Stripe action: " {:action k :params params})
  (when api-key
    (try
      (debug "Api key set, try connect to Stripe.")
      (set! (. Stripe apiKey) api-key)
      (stripe-action k params)
      (catch CardException e
        (throw (ex-info (str (class e) " on Stripe action key: " k)
                        {:cause   ::h/unprocessable-entity
                         :type    (class e)
                         :message (.getMessage e)
                         :code    (keyword "stripe" (.getCode e))
                         :data    params})))
      (catch Exception e
        (throw (ex-info (str (class e) " on Stripe action key: " k)
                        {:cause   ::h/internal-error
                         :code    :undefined
                         :type    (class e)
                         :message (.getMessage e)
                         :data    params}))))))


;;; ########### Stripe actions ###############

(defmulti stripe-action (fn [k _] k))

(defmethod stripe-action :customer/create
  [_ {:keys [params]}]
  {:post [(map? %)]}
  (let [customer (Customer/create params)
        subscription (-> customer
                         (.getSubscriptions)
                         (.getData)
                         first)]
    (debug "Created customer: " customer)
    (debug "Customer map: " {:stripe/customer     (.getId customer)
                             :stripe/subscription (obj->subscription-map subscription)})
    {:stripe/customer     (.getId customer)
     :stripe/subscription (obj->subscription-map subscription)}))


(defmethod stripe-action :subscription/create
  [_ {:keys [customer-id params]}]
  {:post [(map? %)]}
  (let [customer (Customer/retrieve customer-id)
        created (.createSubscription customer params)]
    (debug "Created subscription: " created)
    (obj->subscription-map created)))

(defmethod stripe-action :subscription/update
  [_ {:keys [subscription-id customer-id params]}]
  {:post [(map? %)]}
  (let [customer (Customer/retrieve customer-id)
        subscription (.retrieve (.getSubscriptions customer) subscription-id)
        updated (.update subscription params)]
    (debug "Updated subscription: " updated)
    (obj->subscription-map updated)))

(defmethod stripe-action :subscription/cancel
  [_ {:keys [customer-id subscription-id]}]
  {:post [(map? %)]}
  (let [customer (Customer/retrieve customer-id)
        subscription (.retrieve (.getSubscriptions customer) subscription-id)
        params {"at_period_end" false}
        canceled (.cancel subscription params)]
    (debug "Canceled subscription: " canceled)
    (obj->subscription-map canceled)))

;; ##################### Webhooooks ####################
;; References:
;; https://stripe.com/docs/webhooks
;; https://stripe.com/docs/api/java#events
;; https://stripe.com/docs/api#event_types

(defn webhook-ex [event message & [ex-data]]
  (ex-info (str "Stripe webhook error: " (:type event))
           (merge {:cause   ::h/internal-error
                   :message message
                   :key     (:type event)
                   :event   event
                   :object  (get-in event [:data :object])}
                  ex-data)))

; Multi method for Stripe event types passed in via webhooks.
; Reference Events: https://stripe.com/docs/api#events
(defmulti webhook (fn [_ event & _]
                    (info "Stripe event received: " {:type (:type event) :id (:id event)})
                    ; Dispatches on the event type.
                    ; Reference Event Type: https://stripe.com/docs/api#event_types
                    (:type event)))

(defmethod webhook :default
  [_ event & _]
  (info "Stripe webhook event type not implemented: " (:type event))
  (debug "Stripe event: " event))

(defmethod webhook "charge.succeeded"
  ;; Receiving a Charge object in event.
  ;; Reference: https://stripe.com/docs/api#charge_object
  [conn event & _]
  (let [charge (get-in event [:data :object])]
    (prn "charge.succeeded: " charge)
    (prn "Customer: " (:customer charge))))

(defmethod webhook "charge.failed"
  ;; Receiving a Charge object in event.
  ;; Reference: https://stripe.com/docs/api#charge_object
  [conn event & [opts]]
  (let [{:keys [customer] :as charge} (get-in event [:data :object]) ;; customer id
        {:keys [::email/send-payment-reminder-fn]} opts]
    (if customer
      ;; Find the customer entry in the db.
      (let [db-customer (p/lookup-entity (d/db conn) [:stripe/customer customer])]
        ;; If the customer is not found in the DB, something is wrong and we're out of sync with Stripe.
        (when-not db-customer
          (throw (webhook-ex event
                             (str ":stripe/customer not found: " customer)
                             {:customer customer
                              :cause    ::h/unprocessable-entity
                              :code     :entity-not-found})))

        ;; Find the user corresponding to the specified stripe customer.
        (let [user (p/lookup-entity (d/db conn) (get-in db-customer [:stripe/user :db/id]))]
          ;; If the customer entity has no user, something is wrong in the db entry, throw exception.
          (when-not user
            (throw (webhook-ex event
                               (str "No :stripe/user associated with :stripe/customer: " customer)
                               {:customer customer
                                :code     :entity-not-found})))

          (info "Stripe charge.failed for user " (d/touch user))

          ;; Notify the user by ending an email to the user for the customer. That payment failed and they should check their payment settings.
          (when send-payment-reminder-fn
            (send-payment-reminder-fn (:user/email user)))))
      (when-let [email (get-in charge [:source :name])]
        (when send-payment-reminder-fn
          (send-payment-reminder-fn email))))))

(defmethod webhook "customer.deleted"
  ;; Receiving a Customer object in event.
  ;; Reference: https://stripe.com/docs/api#customer_object
  [conn event & _]
  (let [{:keys [customer]} (get-in event [:data :object])
        db-entry (p/lookup-entity (d/db conn) [:stripe/customer customer])]
    (when db-entry
      (info "Stripe customer.deleted, retracting entity from db: " (d/touch db-entry))
      (transact/transact-one conn [:db.fn/retractEntity (:db/id db-entry)]))))

(defmethod webhook "customer.subscription.created"
  ;; Receiving a Subscription object in event.
  ;; Reference: https://stripe.com/docs/api#subscription_object
  [conn event & _]
  (let [{:keys [customer] :as subscription} (get-in event [:data :object])
        db-customer (p/lookup-entity (d/db conn) [:stripe/customer customer])]
    (if db-customer
      (let [db-sub (f/add-tempid (json->subscription-map subscription))]
        (debug "Customer subscription created: " (:id subscription))
        (transact/transact conn [db-sub
                                 [:db/add (:db/id db-customer) :stripe/subscription (:db/id db-sub)]]))
      (throw (webhook-ex event
                         (str ":stripe/customer not found: " customer)
                         {:customer customer
                          :cause    ::h/unprocessable-entity
                          :code     :entity-not-found})))))

(defmethod webhook "customer.subscription.updated"
  ;; Receiving a Subscription object in event.
  ;; Reference: https://stripe.com/docs/api#subscription_object
  [conn event & _]
  (let [{:keys [customer] :as subscription} (get-in event [:data :object])
        db-customer (p/lookup-entity (d/db conn) [:stripe/customer customer])]
    (if db-customer
      (let [db-sub (f/add-tempid (json->subscription-map subscription))]
        (debug "Customer subscription updated: " (:id subscription))
        (transact/transact conn [db-sub
                                 [:db/add (:db/id db-customer) :stripe/subscription (:db/id db-sub)]]))
      (throw (webhook-ex event
                         (str ":stripe/customer not found: " customer)
                         {:customer customer
                          :cause    ::h/unprocessable-entity
                          :code     :entity-not-found})))))

(defmethod webhook "customer.subscription.deleted"
  ;; Receiving a Subscription object in event.
  ;; Reference: https://stripe.com/docs/api#subscription_object
  [conn event & _]
  (let [subscription (get-in event [:data :object])
        db-entry (p/lookup-entity (d/db conn) [:stripe.subscription/id (:id subscription)])]
    (debug "Customer subscription deleted: " (:id subscription))
    (when db-entry
      (info "Stripe customer.subscription.deleted, retracting entity from db: " (d/touch db-entry))
      (transact/transact-one conn [:db.fn/retractEntity (:db/id db-entry)]))))

(defmethod webhook "invoice.payment_succeeded"
  ;; Receiving an Invoice object in event
  ;; reference: https://stripe.com/docs/api#invoice_object
  [conn event & _]
  (let [{:keys [customer period_end] :as invoice} (get-in event [:data :object])
        {:keys [stripe/subscription]} (p/pull (d/db conn) [:stripe/subscription] [:stripe/customer customer])
        subscription-ends-at (* 1000 period_end)]
    (if subscription
      (do
        (debug "Invoice payment succeeded: " (:id invoice))
        (transact/transact-one conn [:db/add (:db/id subscription) :stripe.subscription/period-end subscription-ends-at]))
      (throw (webhook-ex event
                         (str "No :stripe/subscription associated with :stripe/customer: " customer)
                         {:customer customer
                          :code     :entity-not-found})))))

(defmethod webhook "invoice.payment_failed"
  ;; Receiving an Invoice object in event
  ;; reference: https://stripe.com/docs/api#invoice_object
  [conn event & [opts]]
  (let [{:keys [customer] :as invoice} (get-in event [:data :object])
        {:keys [::email/send-payment-reminder-fn]} opts]
    (if customer
      ;; Find the customer entry in the db.
      (let [db-customer (p/lookup-entity (d/db conn) [:stripe/customer customer])]
        ;; If the customer is not found in the DB, something is wrong and we're out of sync with Stripe.
        (when-not db-customer
          (throw (webhook-ex event
                             (str ":stripe/customer not found: " customer)
                             {:code     :entity-not-found
                              :customer customer
                              :cause    ::h/unprocessable-entity})))

        ;; Find the user corresponding to the specified stripe customer.
        (let [user (p/lookup-entity (d/db conn) (get-in db-customer [:stripe/user :db/id]))]
          ;; If the customer entity has no user, something is wrong in the db entry, throw exception.
          (when-not user
            (throw (webhook-ex event
                               (str "No :stripe/user associated with :stripe/customer: " customer)
                               {:customer customer
                                :code     :entity-not-found})))

          (info "Stripe invoice.payment_failed for user " (d/touch user))
          (when send-payment-reminder-fn
            ;; Notify the user by ending an email to the user for the customer. That payment failed and they should check their payment settings.
            (send-payment-reminder-fn (:user/email user)))))
      (when-let [email (get-in invoice [:source :name])]
        (when send-payment-reminder-fn
          (send-payment-reminder-fn email))))))

;(defmethod webhook "invoice.payment_succeeded")