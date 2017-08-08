(ns eponai.server.external.email
  (:require
    [slingshot.slingshot :refer [try+]]
    [hiccup.page :refer [xhtml]]
    [eponai.server.external.email.templates :as templates]
    [postal.core :as postal]
    [taoensso.timbre :refer [debug info]]
    [clj-http.client :as http]
    [eponai.common.format.date :as date]
    [eponai.common.ui.utils :as ui-utils]
    [eponai.common.photos :as photos]
    [clojure.string :as string]
    [clojure.data.json :as json]
    [eponai.client.routes :as client.routes]))

(defprotocol ISendEmail
  (-send-order-receipt [this params])
  (-send-order-notification [this params])
  (-send-store-access-request [this params]))

(def mandrill-host "https://mandrillapp.com/api/1.0")
(def sulo-host "https://sulo.live")

(defn mandrill-params [params]
  (debug "Convert opts to mandrill params: " params)
  (mapv (fn [[k v]]
          {:name    (name k)
           :content v})
        params))

(defrecord SendEmail [api-key]
  ISendEmail
  (-send-order-receipt [this {:keys [charge order] :as params}]
    (try+
      (let [{:keys [source created amount]} charge
            {:keys [last4 brand]} source
            {:order/keys [store shipping user]} order
            shipping-address (:shipping/address shipping)
            {store-name :store.profile/name} (:store/profile store)
            opts {:order-number             (:db/id order)
                  :store-name               (str store-name)
                  :card-brand               (str brand)
                  :card-last4               (str last4)
                  :order-total              (ui-utils/two-decimal-price (/ amount 100))
                  :created-date             (date/date->string (* created 1000) "MMM dd YYYY")
                  :order-url                (str sulo-host (client.routes/url :user/order {:order-id (:db/id order)}))
                  :store-url                (str sulo-host (client.routes/store-url store :store))
                  :products                 (mapv (fn [oi]
                                                    (debug "Order item: " oi)
                                                    {:product-name      (:order.item/title oi)
                                                     :product-variation (:order.item/description oi)
                                                     :product-photo     (photos/transform (get-in oi [:order.item/photo :photo/id]) :transformation/thumbnail)
                                                     :product-price     (ui-utils/two-decimal-price (:order.item/amount oi))})
                                                  (filter #(= (:order.item/type %) :order.item.type/sku) (:order/items order)))
                  :shipping-name            (:shipping/name shipping)
                  :shipping-address-street  (:shipping.address/street shipping-address)
                  :shipping-address-info    (string/join ", " (remove nil? [(:shipping.address/locality shipping-address)
                                                                            (:shipping.address/postal shipping-address)
                                                                            (:shipping.address/region shipping-address)]))
                  :shipping-address-country (str (-> shipping-address :shipping.address/country :country/name))}
            resp (http/post (str mandrill-host "/messages/send-template.json")
                            {:body (json/write-str {:key              api-key
                                                    :template_name    "sulolive-receipt"
                                                    :template_content []
                                                    :message          {:to         [{:email (:user/email user)}]
                                                                       :subject    (str "Your SULO Live receipt from " store-name " #" (:db/id order))
                                                                       :from_name  (str store-name " via SULO Live")
                                                                       :merge_vars [{:rcpt (:user/email user)
                                                                                     :vars (mandrill-params opts)}]
                                                                       :tags       ["purchase-notifications" "customer-receipt"]}})})]
        (debug "EMAIL - Send order receipt to customer" (into {} resp)))
      (catch Object _
        (debug "Caught error: " (:throwable &throw-context)))))

  (-send-order-notification [this {:keys [charge order] :as params}]
    (try+
      (let [{:keys [source created amount]} charge
            {:keys [last4 brand]} source
            {:order/keys [store shipping]} order
            shipping-address (:shipping/address shipping)
            {store-name :store.profile/name} (:store/profile store)
            store-owner (get-in store [:store/owners :store.owner/user])
            opts {:order-number             (:db/id order)
                  :card-brand               (str brand)
                  :card-last4               (str last4)
                  :order-total              (ui-utils/two-decimal-price (/ amount 100))
                  :created-date             (date/date->string (* created 1000) "MMM dd YYYY")
                  :order-url                (str sulo-host (client.routes/store-url store :store-dashboard/order {:order-id (:db/id order)}))
                  :store-url                (str sulo-host (client.routes/store-url store :store))
                  :products                 (mapv (fn [oi]
                                                    (debug "Order item: " oi)
                                                    {:product-name      (:order.item/title oi)
                                                     :product-variation (:order.item/description oi)
                                                     :product-photo     (photos/transform (get-in oi [:order.item/photo :photo/id]) :transformation/thumbnail)
                                                     :product-price     (ui-utils/two-decimal-price (:order.item/amount oi))})
                                                  (filter #(= (:order.item/type %) :order.item.type/sku) (:order/items order)))
                  :shipping-name            (:shipping/name shipping)
                  :shipping-address-street  (:shipping.address/street shipping-address)
                  :shipping-address-info    (string/join ", " (remove nil? [(:shipping.address/locality shipping-address)
                                                                            (:shipping.address/postal shipping-address)
                                                                            (:shipping.address/region shipping-address)]))
                  :shipping-address-country (str (-> shipping-address :shipping.address/country :country/name))}

            resp (http/post (str mandrill-host "/messages/send-template.json")
                            {:body (json/write-str {:key              api-key
                                                    :template_name    "store-order-notification"
                                                    :template_content []
                                                    :message          {:to         [{:email (:user/email store-owner)}]
                                                                       :subject    (str "You received a SULO Live order from " (:shipping/name shipping) " #" (:db/id order))
                                                                       :from_name  "SULO Live"
                                                                       :merge_vars [{:rcpt (:user/email store-owner)
                                                                                     :vars (mandrill-params opts)}]
                                                                       :tags       ["purchase-notifications" "store-order-notification"]}})})]

        (info "EMAIL - Send order notification to store " (into {} resp)))
      (catch Object _
        (debug "Caught error sending store notification: " (:throwable &throw-context)))))

  (-send-store-access-request [_ {:field/keys [brand email website locality message]
                                  user-id     :user-id :as params}]
    (try+
      (let [
            opts (cond-> {:brand    brand
                          :email    email
                          :website  website
                          :location locality
                          :message  message}
                         (some? user-id)
                         (assoc :user-id user-id))
            resp (http/post (str mandrill-host "/messages/send-template.json")
                            {:body (json/write-str {:key              api-key
                                                    :template_name    "request-start-store"
                                                    :template_content []
                                                    :message          {:to         [{:email "hello@sulo.live"}]
                                                                       :subject    (str "Request start store access - " brand)
                                                                       :from_name  (if (some? user-id) "SULO Live User" "SULO Live Visitor")
                                                                       :merge_vars [{:rcpt "hello@sulo.live"
                                                                                     :vars (mandrill-params opts)}]
                                                                       :tags       ["internal-notifications" "request-store"]}})})
            ;sent-email (postal/send-message smtp
            ;                                {:from    (str (if (some? user-id) "SULO Live User" "SULO Live Visitor") " <hello@sulo.live>")
            ;                                 :to      "hello@sulo.live"
            ;                                 :subject (str "Request start store access - " brand)
            ;                                 :body    [{:type    "text/html"
            ;                                            :content (templates/open-store-request params)}]})
            ]
        ;(debug sent-email)
        (info "EMAIL - Send order notification to store " (into {} resp))
        )
      (catch Object _
        (debug "Caught error sending store notification: " (:throwable &throw-context))))))

(defn email [api-key]
  (->SendEmail api-key))

(defn email-stub []
  (reify
    ISendEmail
    (-send-order-receipt [_ {:keys [charge order]}]
      (let [{:keys [source created amount]} charge
            {:keys [last4 brand]} source
            {:order/keys [store shipping user]} order
            {store-name :store.profile/name} (:store/profile store)]
        (info "Fake email - send order receipt: " {:to        (:user/email user)
                                                   :subject   (str "Your SULO Live receipt from " store-name " #" (:db/id order))
                                                   :order?    (some? order)
                                                   :charge?   (some? charge)
                                                   :shipping? (some? shipping)
                                                   :store?    (some? store)})))
    (-send-order-notification [this {:keys [charge order] :as params}]
      (let [{:order/keys [store shipping user]} order
            {store-name :store.profile/name} (:store/profile store)]
        (info "Fake email - send order notification: " {:to        (-> store :store/owners :store.owner/user :user/email)
                                                        :subject   (str "You received a SULO Live order from " (:shipping/name shipping) " #" (:db/id order))
                                                        :order?    (some? order)
                                                        :charge?   (some? charge)
                                                        :shipping? (some? shipping)
                                                        :store?    (some? store)})))

    (-send-store-access-request [_ {:field/keys [brand user-id] :as params}]
      (info "Fake email - send user request: " {:from    (str (if (some? user-id) "SULO Live User" "SULO Live Visitor") " <hello@sulo.live>")
                                                :to      "hello@sulo.live"
                                                :subject (str "Request start store access - " brand)
                                                :params  params}))))

