(ns eponai.server.external.stripe.format
  (:require [eponai.common.format :as f]
            [eponai.common :as c])
  (:import
    (com.stripe.model OrderItem Order ShippingDetails Address Product SKU Account Account$Verification LegalEntity BankAccount LegalEntity$DateOfBirth CountrySpec VerificationFields VerificationFieldsDetails ExternalAccount Card AccountTransferSchedule LegalEntity$Verification)))

(defn stripe->verification-fields [^VerificationFields v]
  (let [min-fields (fn [^VerificationFieldsDetails field-details]
                     (.getMinimum field-details))]
    {:country-spec.verification-fields/individual {:country-spec.verification-fields.individual/minimum (min-fields (.getIndividual v))}
     :country-spec.verification-fields/company    {:country-spec.verification-fields.company/minimum (min-fields (.getCompany v))}}))

(defn stripe->country-spec [^CountrySpec c]
  {:country-spec/id                                (.getId c)
   :country-spec/default-currency                  (.getDefaultCurrency c)
   :country-spec/supported-payment-currencies      (.getSupportedPaymentCurrencies c)
   :country-spec/supported-bank-account-currencies (.getSupportedBankAccountCurrencies c)
   :country-spec/supported-payment-methods         (.getSupportedPaymentMethods c)
   :country-spec/verification-fields               (stripe->verification-fields (.getVerificationFields c))})

(defn stripe->verification [^Account$Verification v]
  (f/remove-nil-keys
    {:stripe.verification/fields-needed (.getFieldsNeeded v)
     :stripe.verification/due-by        (.getDueBy v)
     :stripe.verification/disabled-reason (.getDisabledReason v)}))


(defn stripe->legal-entity [^LegalEntity le]
  (let [dob* (fn [dob]
               (f/remove-nil-keys
                 {:stripe.legal-entity.dob/year  (.getYear dob)
                  :stripe.legal-entity.dob/month (.getMonth dob)
                  :stripe.legal-entity.dob/day   (.getDay dob)}))
        address* (fn [^Address a]
                   {:stripe.legal-entity.address/city   (.getCity a)
                    :stripe.legal-entity.address/postal (.getPostalCode a)
                    :stripe.legal-entity.address/line1  (.getLine1 a)
                    :stripe.legal-entity.address/state  (.getState a)})
        verification* (fn [^LegalEntity$Verification v]
                        {:stripe.legal-entity.verification/status (.getStatus v)
                         :stripe.legal-entity.verification/details (.getDetails v)})]
    (f/remove-nil-keys
      {:stripe.legal-entity/first-name    (.getFirstName le)
       :stripe.legal-entity/last-name     (.getLastName le)
       :stripe.legal-entity/dob           (dob* (.getDob le))
       :stripe.legal-entity/address       (address* (.getAddress le))
       :stripe.legal-entity/business-name (.getBusinessName le)
       :stripe.legal-entity/type          (keyword (.getType le))
       :stripe.legal-entity/verification  (verification* (.getVerification le))})))

(defn stripe->bank-account [^BankAccount ba]
  {:stripe.external-account/id                    (.getId ba)
   :stripe.external-account/bank-name             (.getBankName ba)
   :stripe.external-account/routing-number        (.getRoutingNumber ba)
   :stripe.external-account/default-for-currency? (.getDefaultForCurrency ba)
   :stripe.external-account/status                (.getStatus ba)
   :stripe.external-account/last4                 (.getLast4 ba)
   :stripe.external-account/currency              (.getCurrency ba)
   :stripe.external-account/country               (.getCountry ba)})

(defn stripe->card [^Card c]
  {:stripe.external-account/id    (.getId c)
   :stripe.external-account/last4 (.getLast4 c)
   :stripe.external-account/brand (.getBrand c)})

(defn stripe->account [^Account a]
  (let [ext-account* (fn [^ExternalAccount ea]
                       (let [object (.getObject ea)
                             account (cond (= "bank_account")
                                           (stripe->bank-account ea)
                                           (= "card")
                                           (stripe->card ea))]
                         (assoc account :stripe.external-account/object object)))
        payout-schedule* (fn [^AccountTransferSchedule ats]
                           {:stripe.payout-schedule/interval     (.getInterval ats)
                            :stripe.payout-schedule/month-anchor (.getMonthlyAnchor ats)
                            :stripe.payout-schedule/week-anchor  (.getWeeklyAnchor ats)
                            :stripe.payout-schedule/delay-days (.getDelayDays ats)})]
    (f/remove-nil-keys
      {:stripe/id                 (.getId a)
       :stripe/business-name      (.getBusinessName a)
       :stripe/business-url       (.getBusinessURL a)
       :stripe/charges-enabled?   (.getChargesEnabled a)
       :stripe/country            (.getCountry a)
       :stripe/default-currency   (.getDefaultCurrency a)
       :stripe/details-submitted? (.getDetailsSubmitted a)
       :stripe/external-accounts  (map ext-account* (.getData (.getExternalAccounts a)))
       :stripe/legal-entity       (stripe->legal-entity (.getLegalEntity a))
       :stripe/payouts-enabled?   (.getTransfersEnabled a)
       :stripe/payout-schedule    (payout-schedule* (.getTransferSchedule a))
       :stripe/verification       (stripe->verification (.getVerification a))})))

(defn stripe->price [p]
  (with-precision 10 (/ (bigdec p) 100)))


;{:field.legal-entity.address/line1   "legal_entity.address.line1"
; :field.legal-entity.address/postal  "legal_entity.address.postal_code"
; :field.legal-entity.address/city    "legal_entity.address.city"
; :field.legal-entity.address/state   "legal_entity.address.state"
;
; :field.legal-entity/business-name   "legal_entity.business_name"
; :field.legal-entity/business-tax-id "legal_entity.business_tax_id"
;
; :field.legal-entity.dob/day         "legal_entity.dob.day"
; :field.legal-entity.dob/month       "legal_entity.dob.month"
; :field.legal-entity.dob/year        "legal_entity.dob.year"
;
; :field.legal-entity/first-name      "legal_entity.first_name"
; :field.legal-entity/last-name       "legal_entity.last_name"
;
; :field/external-account             "external_account"}

(defn input->legal-entity [legal-entity]
  (let [{:field.legal-entity/keys [type address business-name business-tax-id first-name last-name dob]} legal-entity
        {:field.legal-entity.address/keys [line1 postal city state]} address
        {:field.legal-entity.dob/keys [day month year]} dob
        ]
    (f/remove-nil-keys
      {:first_name first-name
       :last_name  last-name
       :dob        (f/remove-nil-keys
                     {:day   day
                      :month month
                      :year  year})
       :address    (f/remove-nil-keys
                     {:city        city
                      :line1       line1
                      :postal_code postal
                      :state       state})
       :type       (when (some? type) (name type))})))

(defn input->payout-schedule [ps]
  (let [{:field.payout-schedule/keys [interval month-anchor week-anchor]} ps]
    (f/remove-nil-keys
      {:interval interval
       :monthly_anchor month-anchor
       :weekly_anchor week-anchor})))

(defn input->account-params [account-params]
  (let [{:field/keys [legal-entity external-account tos-acceptance default-currency payout-schedule]} account-params]
    (f/remove-nil-keys
      {:legal_entity     (input->legal-entity legal-entity)
       :external_account external-account
       :tos_acceptance   (f/remove-nil-keys
                           {:date (:field.tos-acceptance/date tos-acceptance)
                            :ip   (:field.tos-acceptance/ip tos-acceptance)})
       :default_currency default-currency
       :payout_schedule  (input->payout-schedule payout-schedule)})))

(defn input->price [p]
  (when p
    (int (with-precision 10 (* 100 (bigdec p))))))

(defn input->product [{:keys [id] p-name :name}]
  {"id"         id
   "name"       p-name
   "attributes" ["variation"]})

(defn input->sku [product-id {:keys [id price value quantity]}]
  {"id"         id
   "product"    product-id
   "price"      (input->price (or (c/parse-long price) 0))
   "currency"   "CAD"
   "attributes" {"variation" (or value "default")}
   "inventory"  (cond-> {"type" "infinite"}
                        (some? quantity)
                        (assoc "quantity" (c/parse-long quantity)
                               "type" "finite"))})

(defn stripe->sku [^SKU s]
  (let [inventory (.getInventory s)]
    ;; TODO: The price from stripe is smallest int depending on currency.
    ;;       i.e. "100 cents to charge $1.00, or 100 to charge ¥100, Japanese Yen
    ;;            being a 0-decimal currency"
    ;;       Do we use the stripe number somehow, or do we use the price we were
    ;;       passed? Gross.
    (f/remove-nil-keys
      {:store.item.sku/uuid      (f/str->uuid (.getId s))
       :store.item.sku/price     (stripe->price (.getPrice s))
       :store.item.sku/variation (get (.getAttributes s) "variation")})))

(defn stripe->product [^Product p]
  (f/remove-nil-keys
    {:store.item/uuid    (f/str->uuid (.getId p))
     :store.item/name    (.getName p)
     :store.item/skus    (map stripe->sku (.getData (.getSkus p)))
     :store.item/updated (.getUpdated p)
     :store.item/price   (stripe->price (.getPrice (first (.getData (.getSkus p)))))}))


(defn stripe->order-item
  "Convert an OrderItem objec into a map:

  Fields;
    * amount      - A positive integer in the smallest currency unit (that is, 100 cents for $1.00, or 1 for ¥1,
                    Japanese Yen being a 0-decimal currency) representing the total amount for the line item.
    * currency    - 3-letter ISO code representing the currency of the line item.
    * description - Description of the line item, meant to be displayable to the user (e.g., \"Express shipping\").
    * parent      - The ID of the associated object for this line item. Expandable if not null (e.g., expandable to a SKU).
    * quantity    - A positive integer representing the number of instances of parent that are included in this order item.
                    Applicable/present only if type is sku.
    * type        - The type of line item. One of :sku, :tax, :shipping, or :discount.

  see https://stripe.com/docs/api#order_item_object for more information about OrderItem."
  [^OrderItem oi]
  (f/remove-nil-keys
    {:order.item/amount      (.getAmount oi)
     :order.item/currency    (.getCurrency oi)
     :order.item/description (.getDescription oi)
     :order.item/type        (keyword (.getType oi))

     ;; Quantity may be nil in case of type :shipping or :tax)
     :order.item/quantity    (.getQuantity oi)

     ;; Parent can be nil when SKU type is tax
     :order.item/parent      (.getParent oi)}))

(defn stripe->address [a]
  {:city (.getCity a)})

(defn stripe->shipping [s]
  {:order.shipping/address (stripe->address (.getAddress s))
   :order.shipping/name    (.getName s)
   :order.shipping/phone   (.getPhone s)})

(defn stripe->order
  "Convert Java Order object to clojure map.

  Fields:
  * id - String unique id.
  * amount - A positive integer in the smallest currency unit (that is, 100 cents for $1.00, or 1 for ¥1, Japanese Yen being a 0-decimal currency) representing the total amount for the order.
  * application - ID of the Connect Application that created the order.
  * application-fee - integer
  * charge - The ID of the payment used to pay for the order. Present if the order status is paid, fulfilled, or refunded.
  * created - timestamp
  * currency - 3-letter ISO code representing the currency in which the order was made.
  * customer - The customer used for the order. Expandable
  * emai - The email address of the customer placing the order.
  * items - List of items constituting the order.
  * livemode - boolean
  * metadata - A set of key/value pairs that you can attach to an order object. It can be useful for storing additional information about the order in a structured format.
  * selected-shipping-method - The shipping method that is currently selected for this order, if any. If present, it is equal to one of the ids of shipping methods in the shipping_methods array. At order creation time, if there are multiple shipping methods, Stripe will automatically selected the first method.
  * shipping - The shipping address for the order. Present if the order is for goods to be shipped.
  * status - Current order status. One of created, paid, canceled, fulfilled, or returned. More detail in the Relay API Overview.
  * status-transitions - The timestamps at which the order status was updated.
  * updated - timestamp

  See https://stripe.com/docs/api#order_object for more info about the Order object."
  [^Order o]
  (f/remove-nil-keys
    {:order/id                       (.getId o)
     :order/amount                   (stripe->price (.getAmount o))
     ;:order/amount-returned          (.getAmountRefunded o)
     :order/application              (.getApplication o)
     :order/application-fee          (.getApplicationFee o)

     :order/charge                   (.getCharge o)
     :order/created                  (.getCreated o)
     :order/currency                 (.getCurrency o)
     :order/customer                 (.getCustomer o)

     :order/email                    (.getEmail o)
     :order/external-coupon-code     (.getExternalCouponCode o)
     :order/items                    (map stripe->order-item (.getItems o))
     :order/livemode                 (.getLivemode o)
     :order/metadata                 (.getMetadata o)
     ;:order/returns (.getReturns o)
     :order/selected-shipping-method (.getSelectedShippingMethod o)
     :order/shipping                 (stripe->shipping (.getShipping o))

     :order/status                   (keyword "order.status" (.getStatus o))
     ;:order/status-transitions       (.getStatusTransitions o)
     :order/updated                  (.getUpdated o)}))