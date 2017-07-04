(ns eponai.server.external.taxjar
  (:require
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [taoensso.timbre :refer [debug]]
    [eponai.common.format :as cf]))


(defn endpoint [path]
  (str "https://api.taxjar.com/v2/" path))

(defprotocol ITaxjar
  (-calculate [this id params]
    "Calculate tax with given params"))

(defn calculate [taxjar id {:keys [source-address destination-address shipping amount] :as params}]
  (let [source-country (get-in source-address [:shipping.address/country :country/code])
        destination-country (get-in destination-address [:shipping.address/country :country/code])]
    (debug "Calculate taxes: " params)
    (if (= source-country destination-country)
      (let [taxjar-params (cf/remove-nil-keys
                            {:to_country   destination-country ;"ES"
                             :to_state     (:shipping.address/region destination-address)
                             ;:to_zip       (:shipping.address/postal destination-address)
                             :from_country source-country
                             :from_state   (:shipping.address/region source-address)
                             ;:from_zip     (:shipping.address/postal source-address)
                             :amount       (or amount 0)
                             :shipping     (or shipping 10)
                             ;:nexus_addresses [{:id      store-id
                             ;                   :country "CA"
                             ;                   :state   "BC"
                             ;                   :zip     "V6B 2W6"}]
                             })]
        (-calculate taxjar id taxjar-params))
      {:taxes/id               id
       :taxes/rate             0
       :taxes/freight-taxable? false})))


(defrecord Taxjar [api-key]
  ITaxjar
  (-calculate [_ id params]
    (debug "Taxjar - calculate taxes: " params)
    (let [response (json/read-str (:body (http/post (endpoint "taxes") {:form-params params
                                                                        :oauth-token api-key}))
                                  :key-fn keyword)]
      (debug "Taxjar - recieved response: " response)
      (cf/remove-nil-keys
        {:taxes/id               id
         :taxes/rate             (get-in response [:tax :rate])
         :taxes/total-amount     (get-in response [:tax :order_total_amount])
         :taxes/freight-taxable? (get-in response [:tax :freight_taxable])}))))

(defn taxjar [api-key]
  (->Taxjar api-key))

(defn taxjar-stub []
  (reify ITaxjar
    (-calculate [_ store-id params]
      (let [{:keys [amount shipping]} params
            total-amount (+ amount shipping)
            response {:tax {:order_total_amount total-amount
                            :shipping           5.0
                            :taxable_amount     total-amount
                            :amount_to_collect  (* 0.12 total-amount)
                            :rate               0.12
                            :has_nexus          true
                            :freight_taxable    true
                            :tax_source         "origin"}}]
        {:taxes/id               store-id
         :taxes/rate             (get-in response [:tax :rate])
         :taxes/total-amount     (get-in response [:tax :order_total_amount])
         :taxes/freight-taxable? (get-in response [:tax :freight_taxable])}))))
