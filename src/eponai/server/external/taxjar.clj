(ns eponai.server.external.taxjar
  (:require
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [taoensso.timbre :refer [debug]]))


(defn endpoint [path]
  (str "https://api.taxjar.com/v2/" path))

(defprotocol ITaxjar
  (-calculate [this params]
    "Calculate tax with given params"))

(defn calculate [taxjar params]
  (-calculate taxjar params))


(defrecord Taxjar [api-key]
  ITaxjar
  (-calculate [_ params]
    (debug "Calculate taxes: " params)
    (let [response (json/read-str (:body (http/post (endpoint "taxes") {:form-params params
                                                                        :oauth-token api-key}))
                                  :key-fn keyword)]
      (debug "Got Taxjar response: " response)
      )))

(defn taxjar [api-key]
  (->Taxjar api-key))

(defn taxjar-stub []
  (reify ITaxjar
    (-calculate [_ params]
      (let [{:keys [store-id amount shipping]} params
            total-amount (+ amount shipping)
            response {:tax {:order_total_amount total-amount
                            :shipping           5.0
                            :taxable_amount     total-amount
                            :amount_to_collect  36.6
                            :rate               0.12
                            :has_nexus          true
                            :freight_taxable    true
                            :tax_source         "origin"}}]
        {:taxes/id           store-id
         :taxes/rate         (get-in response [:tax :rate])
         :taxes/total-amount (get-in response [:tax :order_total_amount])}))))
