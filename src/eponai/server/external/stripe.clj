(ns eponai.server.external.stripe
  (:require [taoensso.timbre :refer [debug error info]])
  (:import (com.stripe.model Customer)
           (com.stripe Stripe)))

(defn create-customer
  [api-key token]
  (set! (. Stripe apiKey) api-key)  ;TODO: change this to live key
  (let [{:keys [id email]} token
        subscription-params {"source" id
                             "plan"   "basic-monthly"
                             "email"  email}
        customer (Customer/create subscription-params)]
    (debug "Created new customer: " customer)))