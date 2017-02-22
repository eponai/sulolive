(ns eponai.server.external.stripe.format
  (:require [eponai.common.format :as f]
            [eponai.common :as c])
  (:import
    (com.stripe.model OrderItem Order ShippingDetails Address Product SKU)))

(defn stripe->price [p]
  (with-precision 10 (/ (bigdec p) 100)))

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

(defn stripe->sku [s]
  (let [inventory (.getInventory s)]
    ;; TODO: The price from stripe is smallest int depending on currency.
    ;;       i.e. "100 cents to charge $1.00, or 100 to charge ¥100, Japanese Yen
    ;;            being a 0-decimal currency"
    ;;       Do we use the stripe number somehow, or do we use the price we were
    ;;       passed? Gross.
    (cond-> {:store.item.sku/uuid  (f/str->uuid (.getId s))
             :store.item.sku/price (stripe->price (.getPrice s))
             :store.item.sku/value (get (.getAttributes s) "variation")}

            (some? (.getQuantity inventory))
            (assoc :store.item.sku/quantity (bigdec (.getQuantity inventory))))))

(defn stripe->product
  [p]
  {:store.item/uuid    (f/str->uuid (.getId p))
   :store.item/name    (.getName p)
   :store.item/skus    (map stripe->sku (.getData (.getSkus p)))
   :store.item/updated (.getUpdated p)
   :store.item/price   (stripe->price (.getPrice (first (.getData (.getSkus p)))))})


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
  [oi]
  (cond->
    {:order.item/amount      (.getAmount oi)
     :order.item/currency    (.getCurrency oi)
     :order.item/description (.getDescription oi)
     :order.item/type        (keyword (.getType oi))}
    ;; Assoc quantity if we have one (can be nil in case of type :shipping or :tax)
    (some? (.getQuantity oi))
    (assoc :order.item/quantity (.getQuantity oi))

    ;; Assoc parent if exists, can be nil when type is :tax
    (some? (.getParent oi))
    (assoc :order.item/parent (.getParent oi))))

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
  [o]
  (->> {:order/id                       (.getId o)
        :order/amount                   (.getAmount o)
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
        :order/status-transitions       (.getStatusTransitions o)
        :order/updated                  (.getUpdated o)}
       (reduce-kv (fn [m k v] (if (some? v) (assoc m k v) m)) {})))