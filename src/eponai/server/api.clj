(ns eponai.server.api
  (:require
    [compojure.core :refer :all]
    [datomic.api :as d]
    [environ.core :refer [env]]
    [eponai.common.database :as db]
    [eponai.server.datomic.format :as datomic.format]
    [eponai.server.external.stripe :as stripe]
    [eponai.server.external.mailchimp :as mailchimp]
    [eponai.server.http :as http]
    [taoensso.timbre :refer [debug error info]]
    [clojure.data.json :as json])
  (:import (datomic Connection)
           (clojure.lang ExceptionInfo)))

; Actions

(defn api-error [status code ex-data]
  (ex-info (str "API error code " code)
           (merge {:status status
                   :code code}
                  ex-data)))

(declare stripe-trial)

(defn stripe-update-card
  [conn stripe-fn {:keys [stripe/customer
                          stripe/subscription]} {:keys [token]}]
  (let [{:keys [id email]} token
        subscription-id (:stripe.subscription/id subscription)]
    (if (some? customer)
      (let [updated-customer (stripe-fn :customer/update
                               {:customer-id customer
                                :params      {"source" id}})
            update-subscription (stripe-fn :subscription/update
                                           {:customer-id customer
                                            :subscription-id subscription-id
                                            :params {"trial_end" "now"}})]
        (debug "Did update stripe customer: " updated-customer)
        (debug "Updated subscription: " update-subscription)
        (db/transact-one conn [:db/add [:stripe.subscription/id subscription-id] :stripe.subscription/status (:stripe.subscription/status update-subscription)]))
      (throw (ex-info (str "Could not find customer for user with email: " email)
                      {:data {:token token}})))))

(defn stripe-trial
  "Subscribe user to trial, without requesting credit carg."
  [conn stripe-fn {:keys [user/email stripe/_user] :as user}]
  {:pre [(instance? Connection conn)
         (fn? stripe-fn)
         (string? email)]}
  (when (seq _user)
    (throw (api-error ::http/unprocessable-entity :illegal-argument
                      {:message       "Cannot start a trial for user that is already a Stripe customer."
                       :function      (str stripe-trial)
                       :function-args {'stripe-fn stripe-fn
                                       'user      user}})))
  (let [{user-id :db/id} (db/pull (d/db conn) [:db/id] [:user/email email])
        stripe (stripe-fn :customer/create
                          {:params {"plan"  "paywhatyouwant"
                                    "email" email
                                    "quantity" 0}})]
    (debug "Starting trial for user: " email " with stripe info: " stripe)
    (assert (some? user-id))
    (when stripe
      (db/transact-one conn (datomic.format/stripe-account user-id stripe)))))

;(defn newsletter-subscribe [conn email]
;  (let [{:keys [verification] :as account} (datomic.format/user-account-map email)]
;    (mailchimp/subscribe (env :mail-chimp-api-key)
;                         (env :mail-chimp-list-id)
;                         email
;                         (:verification/uuid verification))
;    (info "Newsletter subscribe successful, transacting user into datomic.")
;    (comment
;      ;; TODO: Actually transact this if we want to release jourmoney ^^
;      (transact-map conn account))))

(defn beta-vendor-subscribe [params]
  (mailchimp/subscribe (env :mail-chimp-api-key)
                       (env :mail-chimp-list-beta-id)
                       params))

