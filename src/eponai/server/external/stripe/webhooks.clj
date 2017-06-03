(ns eponai.server.external.stripe.webhooks
  (:require
    [postal.core :as postal]
    [taoensso.timbre :refer [debug]]
    [eponai.server.external.email :as email]
    [eponai.common.database :as db]
    [eponai.common.format :as f]))

(defmulti handle-webhook (fn [_ event] (:type event)))

(defmethod handle-webhook :default
  [_ event]
  (debug "No handler implemented for webhook event " (:type event) ", doing nothing."))

(defmethod handle-webhook "charge.captured"
  [{:keys [state system]} event]
  (debug "Will handle captured charged:  " event)
  (let [charge (get-in event [:data :object])
        order-uuid (f/str->uuid (get-in charge [:metadata :order_uuid]))
        order (db/pull-one-with (db/db state)
                                [:db/id
                                 {:order/items [{:order.item/parent {:store.item/_skus [:store.item/name
                                                                                        :store.item/price]}}
                                                :order.item/amount]}
                                 {:order/store [:db/id
                                                {:store/profile [:store.profile/name]}]}]
                                {:where   '[[?e :order/uuid ?uuid]]
                                 :symbols {'?uuid order-uuid}})
        order (or order {:db/id       123456789
                         :order/items [{:order.item/parent {:store.item/_skus {:store.item/name  "Some product"
                                                                               :store.item/price 100}}
                                        :order.item/amount 100}
                                       {:order.item/parent {:store.item/_skus {:store.item/name  "Secondd"
                                                                               :store.item/price 30}}
                                        :order.item/amount 30}]
                         :order/shipping {:shipping/name "Diana Gren"
                                          :shipping/address {:shipping.address/country "SE"
                                                             :shipping.address/city "Stockholm"
                                                             :shipping.address/postal "11530"
                                                             :shipping.address/street "Artillerigatan 78"}}
                         :order/store {:db/id 1234
                                       :store/profile {:store.profile/name "Test store"}}})]
    (email/-send-order-receipt (:system/email system) {:order order :charge charge}))
  ;(postal/send-message {:from    "me@draines.com"
  ;                      :to      ["mom@example.com" "dad@example.com"]
  ;                      :cc      "bob@example.com"
  ;                      :subject "Hi!"
  ;                      :body    "Test."
  ;                      :X-Tra   "Something else"})
  ()
  )