(ns eponai.server.external.stripe
  (:require [eponai.common.database.transact :as t]
            [eponai.common.database.pull :as p]
            [taoensso.timbre :refer [debug error info]]
            [datomic.api :as d]
            [clojure.data.json :as json])
  (:import (com.stripe.model Customer)
           (com.stripe Stripe)))

(defn subscription [stripe-customer]
  (-> stripe-customer
      (.getSubscriptions)
      (.getData)
      first))

(defn create-customer
  [conn api-key token]
  (set! (. Stripe apiKey) api-key)
  (let [{:keys [id email]} token
        subscription-params {"source" id
                             "plan"   "basic-monthly"
                             "email"  email}
        customer (Customer/create subscription-params)
        subscription (subscription customer)
        user (p/pull (d/db conn) [:db/id] [:user/email email])]

    (assert (:db/id user))
    (prn (t/transact conn [{:db/id               (d/tempid :db.part/user)
                            :stripe/customer     (.getId customer)
                            :stripe/user         (:db/id user)
                            :stripe/subscription (.getId subscription)}]))
    (debug "Created new customer: " customer)))

(defn cancel-subscription [conn api-key stripe-account]
  (set! (. Stripe apiKey) api-key)
  (let [customer-id (:stripe/customer stripe-account)
        customer (Customer/retrieve customer-id)
        subscription (subscription customer)
        params {"at_period_end" true}]
    (debug "Cancelled subscription: " (.cancel subscription params))))