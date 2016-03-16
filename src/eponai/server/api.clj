(ns eponai.server.api
  (:require [clj-time.core :as time]
            [clj-time.coerce :as c]
            [clojure.core.async :refer [go >! <! chan put!]]
            [compojure.core :refer :all]
            [datomic.api :as d]
            [eponai.common.database.pull :as pull]
            [eponai.common.database.transact :refer [transact transact-map transact-one]]
            [eponai.common.format :as common.format]
            [eponai.server.datomic.format :as datomic.format]
            [eponai.server.external.stripe :as stripe]
            [eponai.server.external.mailchimp :as mailchimp]
            [eponai.server.http :as http]
            [taoensso.timbre :refer [debug error info]]
            [environ.core :refer [env]]
            [eponai.common.format :as format])
  (:import (datomic.peer LocalConnection)))

;(defn currency-infos
;  "Post information about currencies with a map of the form:
; {:SGD {:symbol \"SGD\", :symbol_native \"$\", :decimal_digits 2, :rounding 0.0, :code \"SGD\"}},"
;  [conn cur-infos]
;  (let [db-cur-codes (->> (p/currencies (d/db conn))
;                          (map #(keyword (:currency/code %)))
;                          set)
;        cur-infos (->> cur-infos
;                       (filter #(contains? db-cur-codes (key %))))]
;    (transact conn (df/currencies {:currency-infos (vals cur-infos)}))))

; Actions

(defn signin
  "Create a new user and transact into datomic.

  Returns channel with username and db-after user is added to use for email verification."
  [conn email]
  {:pre [(instance? LocalConnection conn)
         (string? email)]}
  (if email
    (let [user (pull/lookup-entity (d/db conn) [:user/email email])
          email-chan (chan 1)]
      (if user
        (let [{:keys [verification/uuid] :as verification} (datomic.format/verification user)]
          (transact-one conn verification)
          (info "New verification " uuid "for user:" email)
          (debug (str "Helper for mobile dev. verify uri: jourmoney://ios/1/login/verify/" uuid))
          (put! email-chan verification)
          {:email-chan email-chan
           :status (:user/status user)})
        (let [{:keys [verification] :as account} (datomic.format/user-account-map email)]
          (transact-map conn account)
          (debug "Creating new user with email:" email "verification:" (:verification/uuid verification))
          (put! email-chan verification)
          (debug "Done creating new user with email:" email)
          {:email-chan email-chan
           :status :user.status/new})))
    (throw (ex-info "Trying to signup with nil email."
                    {:cause ::signup-error}))))

(defn verify-email
  "Try and set the verification status if the verification with the specified uuid to :verification.status/verified.

  If more than 15 minutes has passed since the verification entity was created, sets the status to
  :verification.status/expired and throws exception.

  If the verification does not have status :verification.status/pending,
  it means that it's already verified or expired, throws exception.

  On success returns {:db/id some-id} for the user this verification belongs to."
  [conn uuid]
  {:pre [(instance? LocalConnection conn)
         (string? uuid)]}
  (let [ex (fn [msg] (ex-info msg
                              {:cause   ::verification-error
                               :status  ::http/unathorized
                               :data    {:uuid uuid}
                               :message "The verification link is invalid."}))]
    (if-let [verification (pull/lookup-entity (d/db conn) [:verification/uuid (common.format/str->uuid uuid)])]
      (let [verification-time (c/from-long (:verification/created-at verification))
            time-interval (time/in-minutes (time/interval verification-time (time/now)))
            time-limit (:verification/time-limit verification)]
        ; If the verification was not used within 15 minutes, it's expired.
        (if (or (<= time-limit 0) (>= time-limit time-interval))
          ;If verification status is not pending, we've already verified this or it's expired.
           (if (= (:verification/status verification)
                  :verification.status/pending)
             (do
               (debug "Successful verify for uuid: " (:verification/uuid verification))
               (transact-one conn [:db/add (:db/id verification) :verification/status :verification.status/verified])
               (:verification/entity verification))
             (throw (ex "Invalid verification UUID")))
           (do
            (transact-one conn [:db/add (:db/id verification) :verification/status :verification.status/expired])
            (throw (ex "Expired verification UUID")))))
      ; If verification does not exist, throw invalid error
      (throw (ex "Verification UUID does not exist")))))

(defn activate-account
  "Try and activate account for the user with UUId user-uuid, and user email passed in.

  Default would be the email from the user's FB account or the one they signed up with. However,
  they might change, and we need to check that it's verified. If it's verified, go ahead and activate the account.

  The user is updated with the email provided when activating the account.

  Throws exception if the email provided is not already verified, email is already in use,
  or the user UUID is not found."
  [conn user-uuid email]
  {:pre [(instance? LocalConnection conn)
         (string? user-uuid)
         (string? email)]}
  (if-let [user (pull/lookup-entity (d/db conn) [:user/uuid (common.format/str->uuid user-uuid)])]

    (do
      (when-let [existing-user (pull/lookup-entity (d/db conn) [:user/email email])]
        (if-not (= (:db/id user)
                   (:db/id existing-user))
          (throw (ex-info "Email already in use."
                          {:cause ::authentication-error
                           :data  {:email email}}))))

      (let [user-db-id (:db/id user)
            email-changed-db (:db-after (transact-one conn [:db/add user-db-id :user/email email]))
            verifications (pull/verifications email-changed-db user-db-id :verification.status/verified)]

        ; There's no verification verified for this email on the user,
        ; the user is probably activating their account with a new email.
        ; Create a new verification and throw exception
        (when-not (seq verifications)
          (debug "User not verified for email:" email "will create new verification for user: " user-db-id)
          (let [verification (datomic.format/verification user)]
            (transact-one conn verification)
            (throw (ex-info "Email not verified."
                            {:cause        ::authentication-error
                             :data         {:email email}
                             :message      "Email not verified"
                             :verification verification}))))

        ; If the user is trying to activate the same account again, just return the user entity.
        (if (= (:user/status user) :user.status/active)
          (do
            (debug "User already activated, returning user.")
            (pull/lookup-entity (d/db conn) [:user/email email]))
          ; First time activation, set status to active and return user entity.
          (let [budget (format/budget user-db-id)
                dashboard (format/dashboard (:db/id budget))
                activated-db (:db-after (transact conn [[:db/add user-db-id :user/status :user.status/active]
                                                        [:db/add user-db-id :user/activated-at (c/to-long (time/now))]
                                                        [:db/add user-db-id :user/currency [:currency/code "USD"]]
                                                        budget
                                                        dashboard]))]
            (debug "Activated account for user-uuid:" user-uuid)
            (pull/lookup-entity activated-db [:user/email email])))))


    ; The user uuid was not found in the database.
    (throw (ex-info "Invalid User id." {:cause   ::authentication-error
                                        :data    {:uuid  user-uuid
                                                  :email email}
                                        :message "No user exists for UUID"}))))

(defn stripe-charge
  "Subscribe user to a plan in Stripe. Basically create a Stripe customer for this user and subscribe to a plan."
  [conn token]
  (try
    (stripe/create-customer conn (env :stripe-secret-key-test) token)
    (catch Exception e
      (throw (ex-info (.getMessage e)
                      {:status ::http/unprocessable-entity
                       :data token})))))

(defn stripe-cancel
  "Cancel the subscription in Stripe for user with uuid."
  [conn user-uuid]
  (try
    ; Find the stripe account for the user.
    (let [stripe-account (pull/pull (d/db conn) [{:stripe/_user [:stripe/customer]}] [:user/uuid user-uuid])]
      (debug "Cancel subscription for user-id: " user-uuid)
      (stripe/cancel-subscription conn
                                  (env :stripe-secret-key-test)
                                  (first (:stripe/_user stripe-account))))
    (catch Exception e
      (throw (ex-info (.getMessage e)
                      {:status ::http/unprocessable-entity
                       :data user-uuid})))))

(defn newsletter-subscribe [conn email]
  (let [{:keys [verification] :as account} (datomic.format/user-account-map email {:verification/time-limit 0})]
    (mailchimp/subscribe (env :mail-chimp-api-key)
                         (env :mail-chimp-list-id)
                         email
                         (:verification/uuid verification))
    (info "Newsletter subscribe successful, transacting user into datomic.")
    (transact-map conn account)))