(ns eponai.server.external.email
  (:require
    [hiccup.page :refer [xhtml]]
    [eponai.server.external.email.templates :as templates]
    [postal.core :as postal]
    [taoensso.timbre :refer [debug info]]))

(defprotocol ISendEmail
  (-send-order-receipt [this params])
  (-send-order-notification [this params])
  (-send-store-access-request [this params]))

(defrecord SendEmail [smtp]
  ISendEmail
  (-send-order-receipt [this {:keys [charge order] :as params}]
    (debug "Sending email... " smtp)
    (let [{:keys [source created amount]} charge
          {:keys [last4 brand]} source
          {:order/keys [store shipping user]} order
          {store-name :store.profile/name} (:store/profile store)
          sent-email (postal/send-message smtp
                                          {:from    (str store-name " via SULO Live <hello@sulo.live>")
                                           :to      (:user/email user)
                                           :subject (str "Your SULO Live receipt from " store-name " #" (:db/id order))
                                           :body    [{:type    "text/html"
                                                      :content (templates/order-receipt params)}]})]
      (info "EMAIL - Send order receipt to customer" sent-email)))

  (-send-order-notification [this {:keys [charge order] :as params}]
    (debug "Sending email... " smtp)
    (let [{:keys [source created amount]} charge
          {:keys [last4 brand]} source
          {:order/keys [store shipping]} order
          {store-name :store.profile/name} (:store/profile store)
          store-owner (get-in store [:store/owners :store.owner/user])
          sent-email (postal/send-message smtp
                                          {:from    "SULO Live <hello@sulo.live>"
                                           :to      (:user/email store-owner)
                                           :subject (str "You received a SULO Live order from " (:shipping/name shipping) " #" (:db/id order))
                                           :body    [{:type    "text/html"
                                                      :content (templates/order-notification params)}]})]

      (info "EMAIL - Send order notification to store " sent-email)))

  (-send-store-access-request [_ {:field/keys [brand email website locality]
                                  user-id     :user-id :as params}]
    (let [sent-email (postal/send-message smtp
                                          {:from    (str (if (some? user-id) "SULO Live User" "SULO Live Visitor") " <hello@sulo.live>")
                                           :to      "hello@sulo.live"
                                           :subject (str "Request start store access - " brand)
                                           :body    [{:type    "text/html"
                                                      :content (templates/open-store-request params)}]})]
      (debug sent-email))))

(defn email [smtp]
  (->SendEmail smtp))

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

