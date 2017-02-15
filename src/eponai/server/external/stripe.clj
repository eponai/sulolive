(ns eponai.server.external.stripe
  (:require
    [datomic.api :as d]
    [eponai.common.database :as db]
    [eponai.common.format :as f]
    [eponai.server.email :as email]
    [eponai.server.http :as h]
    [taoensso.timbre :refer [debug error info]]
    [clojure.data.json :as json]
    [eponai.common :as c])
  (:import
    (com.stripe Stripe)
    (com.stripe.exception CardException)
    (com.stripe.model Customer Card Charge Subscription Account Product ExternalAccountCollection ExternalAccount BankAccount SKU Inventory Order)
    (com.stripe.net RequestOptions)))

(defn set-api-key [api-key]
  (if (some? api-key)
    (set! (. Stripe apiKey) api-key)
    (throw (ex-info "No Api key provided" {:message "No API key provided"
                                           :cause   ::h/unprocessable-entity}))))

(defn pull-stripe [db store-id]
  (when store-id
    (db/pull-one-with db '[*] {:where   '[[?s :store/stripe ?e]]
                               :symbols {'?s store-id}})))
;; ########## Stripe protocol ################

(defprotocol IStripeConnect
  (create-account [this opts]
    "Create a managed account on Stripe for a a seller.
    Opts is a map with following keys:

    :country - A two character string code for the country of the seller, e.g. 'US'.")

  (get-account [this account-id]
    "Get a managed account for a seller from Stripe.
    Opts is a map with following keys:

    :country - A two character string code for the country of the seller, e.g. 'US'.")

  (create-customer [this account-id opts]
    "Get a managed account for a seller from Stripe.
    Opts is a map with following keys:

    :country - A two character string code for the country of the seller, e.g. 'US'.")

  (charge [this opts]
    "Get a managed account for a seller from Stripe.
    Opts is a map with following keys:

    :currency - A three character string code for the currency of the charge, e.g 'sek'.
    :amount - The amount of the charge in cents. I.e. a charge for $10.00 has an amount of 1000.
    :source - The token of the card to charge, received from Stripe.js.
    :destination - The Stripe account ID of the seller who should receive payment."))

(defprotocol IStripeAccount
  (create-product [this account-secret product]
    "Get a managed account for a seller from Stripe.
    Opts is a map with following keys:

    :country - A two character string code for the country of the seller, e.g. 'US'.")

  (create-sku [this account-secret product-id sku]
    "Get a managed account for a seller from Stripe.
    Opts is a map with following keys:

    :country - A two character string code for the country of the seller, e.g. 'US'.")

  (update-product [_ account-secret product-id params]
    "Get a managed account for a seller from Stripe.
    Opts is a map with following keys:

    :country - A two character string code for the country of the seller, e.g. 'US'.")

  (delete-product [this account-secret product-id]
    "Get a managed account for a seller from Stripe.
    Opts is a map with following keys:

    :country - A two character string code for the country of the seller, e.g. 'US'.")

  (delete-sku [this account-secret sku-id]
    "Get a managed account for a seller from Stripe.
    Opts is a map with following keys:

    :country - A two character string code for the country of the seller, e.g. 'US'.")

  (get-products [this account-secret]
    "Get a managed account for a seller from Stripe.
    Opts is a map with following keys:

    :country - A two character string code for the country of the seller, e.g. 'US'.")

  (get-orders [this account-secret]))

(defn request-options [account-id]
  (.setStripeAccount (RequestOptions/builder) account-id))

(defrecord StripeRecord [api-key]
  IStripeConnect
  (charge [_ {:keys [amount currency source destination]}]
    (let [params {"amount"      amount
                  "currency"    currency
                  "source"      source
                  "destination" destination}
          charge (Charge/create params)]
      (debug "Created charge: " charge)))

  (get-account [_ account-id]
    (set-api-key api-key)
    (let [account (Account/retrieve ^String account-id)
          external-accounts (.getExternalAccounts account)]
      {:id      (.getId account)
       :country (.getCountry account)}))

  (create-customer [_ account-id {:keys [email]}]
    (let [customer (Customer/create {"email" email} ^RequestOptions (request-options account-id))]
      customer))

  (create-account [_ {:keys [country]}]
    (set-api-key api-key)
    (let [account (Account/create {"country" country
                                   "managed" true})
          keys (.getKeys account)]
      (debug "Created account: " account)
      {:id     (.getId account)
       :secret (.getSecret keys)
       :publ   (.getPublishable keys)}))
  IStripeAccount
  (get-products [_ account-secret]
    (set-api-key account-secret)
    (let [products (Product/list nil)]
      (map (fn [p]
             {:id   (.getId p)
              :name (.getName p)})
           (.getData products))))

  (create-product [_ account-secret product]
    (set-api-key account-secret)
    (let [params {"id"         (:id product)
                  "name"       (:name product)
                  "attributes" ["variation"]}
          new-product (Product/create params)]
      {:id   (.getId new-product)
       :name (.getName new-product)}))

  (create-sku [_ account-secret product-id sku]
    (set-api-key account-secret)
    (let [{:keys [price type quantity value id]} sku
          params {"id" id
                  "product"    product-id
                  "price"      (or (c/parse-long price) 0)
                  "currency"   "CAD"
                  "attributes" {"variation" value}
                  "inventory"  (cond-> {"type" (name type)}
                                       (some? quantity)
                                       (assoc "quantity" (c/parse-long quantity)))}
          SKU (SKU/create params)
          inventory (.getInventory SKU)]
      {:id       (java.util.UUID/fromString (.getId SKU))
       :type     (keyword "store.item.sku.type" (.getType inventory))
       :quantity (bigdec (.getQuantity inventory))
       ;; TODO: The price from stripe is smallest int depending on currency.
       ;;       i.e. "100 cents to charge $1.00, or 100 to charge ¥100, Japanese Yen
       ;;            being a 0-decimal currency"
       ;;       Do we use the stripe number somehow, or do we use the price we were
       ;;       passed? Gross.
       :price    (bigdec price)
       :value    (get (.getAttributes SKU) "variation")}))

  (update-product [_ account-secret product-id params]
    (set-api-key account-secret)
    (let [params {"name" (:name params)}
          old-product (Product/retrieve product-id)
          new-product (.update old-product params)]
      {:id (.getId new-product)}))

  (delete-product [_ account-secret product-id]
    (set-api-key account-secret)
    (let [product (Product/retrieve product-id)
          deleted (.delete product)]
      {:id      (.getId deleted)
       :deleted (.getDeleted deleted)}))

  (delete-sku [_ account-secret sku-id]
    (set-api-key account-secret)
    (let [sku (SKU/retrieve sku-id)
          deleted (.delete sku)]
      {:id      (.getId deleted)
       :deleted (.getDeleted deleted)}))

  (get-orders [_ account-secret]
    (set-api-key account-secret)
    (let [orders (Order/list nil)]
      (debug "STRIPE order list: " orders)
      (map (fn [o]
             {:id   (.getId o)
              :name (.getItems o)})
           (.getData orders)))))

(defn stripe [api-key]
  (->StripeRecord api-key))

(defn stripe-stub []
  (reify IStripeConnect
    (charge [_ params]
      (debug "DEV - Fake Stripe: charge with params: " params))
    (create-account [_ params]
      (debug "DEV - Fake Stripe: create-account with params: " params))
    (get-account [_ params]
      (debug "DEV - Fake Stripe: get-acconunt with params: " params))
    (create-customer [_ _ params]
      (debug "DEV - Fake Stripe: create-customer with params: " params))
    IStripeAccount))


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
                 :id        (.getId card)
                 })})))
;; ######################################

(defn obj->subscription-map [^Subscription stripe-obj]
  {:stripe.subscription/id         (.getId stripe-obj)
   :stripe.subscription/status     (keyword (.getStatus stripe-obj))
   :stripe.subscription/period-end (* 1000 (.getCurrentPeriodEnd stripe-obj))})

(defn json->subscription-map [{:keys [id current_period_end status]}]
  {:stripe.subscription/id         id
   :stripe.subscription/status     (keyword status)
   :stripe.subscription/period-end (* 1000 current_period_end)})

(declare stripe-action)

;(defn stripe [api-key k params]
;  (when api-key
;    (try
;      (debug "Api key set, try connect to Stripe.")
;      (set! (. Stripe apiKey) api-key)
;      (stripe-action k params)
;      (catch CardException e
;        (throw (ex-info (str (class e) " on Stripe action key: " k)
;                        {:cause   ::h/unprocessable-entity
;                         :type    (class e)
;                         :message (.getMessage e)
;                         :code    (keyword "stripe" (.getCode e))
;                         :data    params})))
;      (catch Exception e
;        (throw (ex-info (str (class e) " on Stripe action key: " k)
;                        {:cause   ::h/internal-error
;                         :code    :undefined
;                         :type    (class e)
;                         :message (.getMessage e)
;                         :data    params}))))))


;;; ########### Stripe actions ###############

(defmulti stripe-action (fn [k _] k))
;
;(defmethod stripe-action :customer/get
;  [_ {:keys [customer-id subscription-id]}]
;  (when customer-id
;    (let [^Customer customer (Customer/retrieve customer-id)
;          _ (debug "Customer object: " customer)
;          default-source (.getDefaultSource customer)
;          ^Card card (when default-source
;                       (.retrieve (.getSources customer) (.getDefaultSource customer)))
;          ^Subscription subscription (when subscription-id
;                                       (.retrieve (.getSubscriptions customer) subscription-id))]
;      {:id           (.getId customer)
;       :email        (.getEmail customer)
;       :card         (when card
;                       {:exp-month (.getExpMonth card)
;                        :exp-year  (.getExpYear card)
;                        :name      (.getName card)
;                        :last4     (.getLast4 card)
;                        :brand     (.getBrand card)
;                        :id        (.getId card)
;                        })
;       :subscription (when subscription
;                       {:quantity     (.getQuantity subscription)
;                        :period-end   (* 1000 (.getCurrentPeriodEnd subscription))
;                        :period-start (* 1000 (.getCurrentPeriodStart subscription))})})))
;
;(defmethod stripe-action :customer/create
;  [_ {:keys [params]}]
;  {:post [(map? %)]}
;  (let [customer (Customer/create params)
;        subscription (-> customer
;                         (.getSubscriptions)
;                         (.getData)
;                         first)]
;    (debug "Created customer: " customer)
;    (debug "Customer map: " {:stripe/customer     (.getId customer)
;                             :stripe/subscription (obj->subscription-map subscription)})
;    {:stripe/customer     (.getId customer)
;     :stripe/subscription (obj->subscription-map subscription)}))
;
;(defmethod stripe-action :customer/update
;  [_ {:keys [customer-id params]}]
;  (let [customer (Customer/retrieve customer-id)
;        updated-customer (.update customer params)]
;    (debug "Updated customer: " updated-customer)
;    {:stripe/customer customer-id}))
;
;(defmethod stripe-action :card/delete
;  [_ {:keys [customer-id card]}]
;  (let [customer (Customer/retrieve customer-id)
;        card (.retrieve (.getSources customer) (:id card))
;        deleted (.delete card)]
;    (debug "Deleted card from customer: " customer " " deleted)
;    {:stripe/customer customer-id}))
;
;
;(defmethod stripe-action :subscription/create
;  [_ {:keys [customer-id params]}]
;  {:post [(map? %)]}
;  (let [customer (Customer/retrieve customer-id)
;        created (.createSubscription customer params)]
;    (debug "Created subscription: " created)
;    (obj->subscription-map created)))
;
;(defmethod stripe-action :subscription/update
;  [_ {:keys [subscription-id customer-id params]}]
;  {:post [(map? %)]}
;  (let [customer (Customer/retrieve customer-id)
;        subscription (.retrieve (.getSubscriptions customer) subscription-id)
;        updated (.update subscription params)]
;    (debug "Updated subscription: " updated)
;    (obj->subscription-map updated)))
;
;(defmethod stripe-action :subscription/cancel
;  [_ {:keys [customer-id subscription-id]}]
;  {:post [(map? %)]}
;  (let [customer (Customer/retrieve customer-id)
;        subscription (.retrieve (.getSubscriptions customer) subscription-id)
;        params {"at_period_end" false}
;        canceled (.cancel subscription params)]
;    (debug "Canceled subscription: " canceled)
;    (obj->subscription-map canceled)))

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
                    (debug "Request: " event)
                    (info "Stripe event received: " {:type (:type event) :id (:id event)})
                    ; Dispatches on the event type.
                    ; Reference Event Type: https://stripe.com/docs/api#event_types
                    (:type event)))

(defmethod webhook :default
  [_ event & _]
  (debug "Request: " event)
  (info "Stripe webhook event type not implemented: " (:type event))
  (debug "Stripe event: " event))

(defn stripe->product [{:keys [name description]}]
  {:db/id           (d/tempid :db.part/user)
   :store.item/name name})
(defmethod webhook "product.created"
  [conn event & _]
  (let [product (get-in event [:data :object])
        {:keys [description name]} product]
    ())
  {:request nil, :type "product.created", :created 1326853478, :pending_webhooks 1, :id "evt_00000000000000", :api_version "2017-01-27", :livemode false, :object "event", :data {:object {:description "Comfortable gray cotton t-shirts", :caption nil, :updated 1486348116, :images [], :name "T-shirt", :deactivate_on [], :package_dimensions nil, :created 1486348116, :shippable true, :active true, :id "prod_00000000000000", :url nil, :livemode false, :skus {:object "list", :data [], :has_more false, :total_count 0, :url "/v1/skus?product=prod_A4AahEB2jWGNR3&active=true"}, :attributes ["size" "gender"], :metadata {}, :object "product"}}}
  (debug "Request: " event)
  (info "Stripe webhook event type not implemented: " (:type event))
  (debug "Stripe event: " event))

;(defmethod webhook "charge.succeeded"
;  ;; Receiving a Charge object in event.
;  ;; Reference: https://stripe.com/docs/api#charge_object
;  [conn event & _]
;  (let [charge (get-in event [:data :object])]
;    (prn "charge.succeeded: " charge)
;    (prn "Customer: " (:customer charge))))

;(defmethod webhook "charge.failed"
;  ;; Receiving a Charge object in event.
;  ;; Reference: https://stripe.com/docs/api#charge_object
;  [conn event & [opts]]
;  (let [{:keys [customer] :as charge} (get-in event [:data :object]) ;; customer id
;        {:keys [::email/send-payment-reminder-fn]} opts]
;    (if customer
;      ;; Find the customer entry in the db.
;      (let [db-customer (db/lookup-entity (d/db conn) [:stripe/customer customer])]
;        ;; If the customer is not found in the DB, something is wrong and we're out of sync with Stripe.
;        (when-not db-customer
;          (throw (webhook-ex event
;                             (str ":stripe/customer not found: " customer)
;                             {:customer customer
;                              :cause    ::h/unprocessable-entity
;                              :code     :entity-not-found})))
;
;        ;; Find the user corresponding to the specified stripe customer.
;        (let [user (db/lookup-entity (d/db conn) (get-in db-customer [:stripe/user :db/id]))]
;          ;; If the customer entity has no user, something is wrong in the db entry, throw exception.
;          (when-not user
;            (throw (webhook-ex event
;                               (str "No :stripe/user associated with :stripe/customer: " customer)
;                               {:customer customer
;                                :code     :entity-not-found})))
;
;          (info "Stripe charge.failed for user " (d/touch user))
;
;          ;; Notify the user by ending an email to the user for the customer. That payment failed and they should check their payment settings.
;          (when send-payment-reminder-fn
;            (send-payment-reminder-fn (:user/email user)))))
;      (when-let [email (get-in charge [:source :name])]
;        (when send-payment-reminder-fn
;          (send-payment-reminder-fn email))))))

;(defmethod webhook "customer.deleted"
;  ;; Receiving a Customer object in event.
;  ;; Reference: https://stripe.com/docs/api#customer_object
;  [conn event & _]
;  (let [{:keys [customer]} (get-in event [:data :object])
;        db-entry (db/lookup-entity (d/db conn) [:stripe/customer customer])]
;    (when db-entry
;      (info "Stripe customer.deleted, retracting entity from db: " (d/touch db-entry))
;      (db/transact-one conn [:db.fn/retractEntity (:db/id db-entry)]))))

;(defmethod webhook "customer.subscription.created"
;  ;; Receiving a Subscription object in event.
;  ;; Reference: https://stripe.com/docs/api#subscription_object
;  [conn event & _]
;  (let [{:keys [customer] :as subscription} (get-in event [:data :object])
;        db-customer (db/lookup-entity (d/db conn) [:stripe/customer customer])]
;    (if db-customer
;      (let [db-sub (f/add-tempid (json->subscription-map subscription))]
;        (debug "Customer subscription created: " (:id subscription))
;        (db/transact conn [db-sub
;                           [:db/add (:db/id db-customer) :stripe/subscription (:db/id db-sub)]]))
;      (throw (webhook-ex event
;                         (str ":stripe/customer not found: " customer)
;                         {:customer customer
;                          :cause    ::h/unprocessable-entity
;                          :code     :entity-not-found})))))

;(defmethod webhook "customer.subscription.updated"
;  ;; Receiving a Subscription object in event.
;  ;; Reference: https://stripe.com/docs/api#subscription_object
;  [conn event & _]
;  (let [{:keys [customer] :as subscription} (get-in event [:data :object])
;        db-customer (db/lookup-entity (d/db conn) [:stripe/customer customer])]
;    (if db-customer
;      (let [db-sub (f/add-tempid (json->subscription-map subscription))]
;        (debug "Customer subscription updated: " (:id subscription))
;        (db/transact conn [db-sub
;                           [:db/add (:db/id db-customer) :stripe/subscription (:db/id db-sub)]]))
;      (throw (webhook-ex event
;                         (str ":stripe/customer not found: " customer)
;                         {:customer customer
;                          :cause    ::h/unprocessable-entity
;                          :code     :entity-not-found})))))

;(defmethod webhook "customer.subscription.deleted"
;  ;; Receiving a Subscription object in event.
;  ;; Reference: https://stripe.com/docs/api#subscription_object
;  [conn event & _]
;  (let [subscription (get-in event [:data :object])
;        db-entry (db/lookup-entity (d/db conn) [:stripe.subscription/id (:id subscription)])]
;    (debug "Customer subscription deleted: " (:id subscription))
;    (when db-entry
;      (info "Stripe customer.subscription.deleted, retracting entity from db: " (d/touch db-entry))
;      (db/transact-one conn [:db.fn/retractEntity (:db/id db-entry)]))))

;(defmethod webhook "invoice.payment_succeeded"
;  ;; Receiving an Invoice object in event
;  ;; reference: https://stripe.com/docs/api#invoice_object
;  [conn event & _]
;  (let [{:keys [customer period_end] :as invoice} (get-in event [:data :object])
;        {:keys [stripe/subscription]} (db/pull (d/db conn) [:stripe/subscription] [:stripe/customer customer])
;        subscription-ends-at (* 1000 period_end)]
;    (if subscription
;      (do
;        (debug "Invoice payment succeeded: " (:id invoice))
;        (db/transact-one conn [:db/add (:db/id subscription) :stripe.subscription/period-end subscription-ends-at]))
;      (throw (webhook-ex event
;                         (str "No :stripe/subscription associated with :stripe/customer: " customer)
;                         {:customer customer
;                          :code     :entity-not-found})))))

;(defmethod webhook "invoice.payment_failed"
;  ;; Receiving an Invoice object in event
;  ;; reference: https://stripe.com/docs/api#invoice_object
;  [conn event & [opts]]
;  (let [{:keys [customer] :as invoice} (get-in event [:data :object])
;        {:keys [::email/send-payment-reminder-fn]} opts]
;    (if customer
;      ;; Find the customer entry in the db.
;      (let [db-customer (db/lookup-entity (d/db conn) [:stripe/customer customer])]
;        ;; If the customer is not found in the DB, something is wrong and we're out of sync with Stripe.
;        (when-not db-customer
;          (throw (webhook-ex event
;                             (str ":stripe/customer not found: " customer)
;                             {:code     :entity-not-found
;                              :customer customer
;                              :cause    ::h/unprocessable-entity})))
;
;        ;; Find the user corresponding to the specified stripe customer.
;        (let [user (db/lookup-entity (d/db conn) (get-in db-customer [:stripe/user :db/id]))]
;          ;; If the customer entity has no user, something is wrong in the db entry, throw exception.
;          (when-not user
;            (throw (webhook-ex event
;                               (str "No :stripe/user associated with :stripe/customer: " customer)
;                               {:customer customer
;                                :code     :entity-not-found})))
;
;          (info "Stripe invoice.payment_failed for user " (d/touch user))
;          (when send-payment-reminder-fn
;            ;; Notify the user by ending an email to the user for the customer. That payment failed and they should check their payment settings.
;            (send-payment-reminder-fn (:user/email user)))))
;      (when-let [email (get-in invoice [:source :name])]
;        (when send-payment-reminder-fn
;          (send-payment-reminder-fn email))))))

;(defmethod webhook "invoice.payment_succeeded")