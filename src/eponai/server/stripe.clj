(ns eponai.server.stripe
  (:require [taoensso.timbre :refer [debug error info]])
  (:import (com.stripe.model Customer)
           (com.stripe Stripe)))

(defn create-customer
  [api-key {:keys [stripeToken
                   stripeEmail]}]
  (set! (. Stripe apiKey) api-key)  ;TODO: change this to live key
  (let [subscription-params {"source" stripeToken
                             "plan"   "jourmoney-basic-plan"
                             "email"  stripeEmail}
        customer (Customer/create subscription-params)]
    (debug "Created new customer: " customer)))