(ns eponai.server.external.email
  (:require
    [hiccup.page :refer [xhtml]]
    [eponai.server.external.email.templates :as templates]
    [postal.core :as postal]
    [taoensso.timbre :refer [debug]]))

(defprotocol ISendEmail
  (-send-order-receipt [this params]))

(defrecord SendEmail [smtp]
  ISendEmail
  (-send-order-receipt [this {:keys [charge order] :as params}]
    (debug "Sending email... " smtp)
    (let [{:keys [source created amount]} charge
          {:keys [last4 brand]} source
          {:order/keys [store shipping]} order
          {store-name :store.profile/name} (:store/profile store)
          sent-email (postal/send-message smtp
                                          {:from    "hello@sulo.live"
                                           :to      "diana@sulo.live"
                                           :subject (str "Your SULO Live receipt from " store-name " #" (:db/id order))
                                           :body    [{:type    "text/html"
                                                      :content (templates/receipt params)}]})]
      (debug sent-email))))

