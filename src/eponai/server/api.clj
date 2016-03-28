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
            )
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

(declare stripe-trial)

(defn activate-account
  "Try and activate account for the user with UUId user-uuid, and user email passed in.

  Default would be the email from the user's FB account or the one they signed up with. However,
  they might change, and we need to check that it's verified. If it's verified, go ahead and activate the account.

  The user is updated with the email provided when activating the account.

  Throws exception if the email provided is not already verified, email is already in use,
  or the user UUID is not found."
  [conn user-uuid email & [opts]]
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
          (let [budget (common.format/budget user-db-id)
                dashboard (common.format/dashboard (:db/id budget))
                activated-db (:db-after (transact conn [[:db/add user-db-id :user/status :user.status/active]
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

(defn share-budget [conn budget-uuid user-email]
  (let [db (d/db conn)
        user (pull/lookup-entity db [:user/email user-email])
        email-chan (chan 1)]
    (if user
      ;; If user already exists, check that they are not already sharing this budget.
      (let [user-budgets (pull/pull db [{:budget/_users [:budget/uuid]}] (:db/id user))
            grouped (group-by :budget/uuid (:budget/_users user-budgets))]
        ;; If the user is already sharing this budget, throw an exception.
        (when (get grouped budget-uuid)
          (throw (ex-info "User already sharing budget." {:cause ::http/internal-error
                                                          :data  {:user/email  user-email
                                                                  :budget/uuid budget-uuid}})))
        ;; Else create a new verification for the user, to login through their email.
        ;; We let this verification be unlimited time, as the user invited may not see their email within 15 minutes from the invitation
        (let [verification (datomic.format/verification user {:verification/time-limit 0})]
          (transact conn [verification
                          [:db/add [:budget/uuid budget-uuid] :budget/users [:user/email user-email]]])
          (put! email-chan verification)
          {:email-chan email-chan
           :status     (:user/status user)}))

      ;; If no user exists, create a new account, and verification as normal.
      ;; And add that user to the users for the budget.
      ;; TODO: We might want to create an 'invitation' entity, so that it can be pending if the user hasn't accepted to share
      (let [{:keys [verification]
             new-user :user
             :as new-account} (datomic.format/user-account-map user-email {:verification/time-limit 0})]
        (prn "Transact new user: " new-account)
        (transact-map conn (assoc new-account :add [:db/add [:budget/uuid budget-uuid] :budget/users (:db/id  new-user)]))
        (put! email-chan verification)
        {:email-chan email-chan
         :status (:user/status new-user)}))))

(defn stripe-subscribe
  "Subscribe user to a plan in Stripe. Basically create a Stripe customer for this user and subscribe to a plan."
  [conn stripe-fn {:keys [stripe/customer
                          stripe/subscription]} {:keys [token plan] :as p}]
  {:pre [(instance? LocalConnection conn) (fn? stripe-fn) (map? p)]}
  (let [{:keys [id email]} token
        params {"source"    id
                "plan"      plan
                "trial_end" "now"}]
    (if (some? customer)
      (if-let [subscription-id (:stripe.subscription/id subscription)]
        ;; We have a subscription saved in datomic, so just update that.
        (let [updated (stripe-fn :subscription/update
                                 {:customer-id     customer
                                  :subscription-id subscription-id
                                  :params          params})]
          (transact conn [[:db/add [:stripe.subscription/id subscription-id] :stripe.subscription/status (:stripe.subscription/status updated)]
                          [:db/add [:stripe.subscription/id subscription-id] :stripe.subscription/ends-at (:stripe.subscription/ends-at updated)]]))

        ;; We don't have a subscription saved for the customer (maybe the user canceled at some point). Create a new one.
        (let [created (stripe-fn :subscription/create
                                 {:customer-id customer
                                  :params      params})
              db-subscription (assoc created :db/id (d/tempid :db.part/user))]
          (transact conn [db-subscription
                          [:db/add [:stripe/customer customer] :stripe/subscription (:db/id db-subscription)]])))

      ;; We don't have a Stripe customer, so we need to create it.
      (let [{user-id :db/id} (pull/pull (d/db conn) [:db/id] [:user/email email])
            stripe (stripe-fn :customer/create
                              {:params (assoc params "email" email)})
            _ (debug "Will format stripe: " stripe)
            account (datomic.format/stripe-account user-id stripe)]
        (debug "did format account stripe " account)
        (assert (some? user-id))
        (transact-one conn account)))))

(defn stripe-trial
  "Subscribe user to trial, without requesting credit carg."
  [conn stripe-fn {:keys [user/email
                          stripe/_user]}]
  {:pre [(instance? LocalConnection conn)
         (fn? stripe-fn)
         (string? email)]}
  (when (seq _user)
    (throw (ex-info "Trial for user that's already on Stripe. " {:data {:email email}})))
  (let [{user-id :db/id} (pull/pull (d/db conn) [:db/id] [:user/email email])
        stripe (stripe-fn :customer/create
                          {:params {"plan"  "monthly"
                                    "email" email}})]
    (assert (some? user-id))
    (transact-one conn (datomic.format/stripe-account user-id stripe))))

(defn stripe-cancel
  "Cancel the subscription in Stripe for user with uuid."
  [conn stripe-fn stripe-account]
  {:pre [(instance? LocalConnection conn)
         (fn? stripe-fn)
         (map? stripe-account)]}
  ;; If no customer-id exists, we cannot cancel anything.
  (when-not (:stripe/customer stripe-account)
    (throw (ex-info "No customer id provided. " {:cause ::http/unprocessable-entity
                                                 :data {:stripe-account stripe-account}})))
  ;; If we have a subscription id in the db, try and cancel that
  (if-let [subscription-id (get-in stripe-account [:stripe/subscription :stripe.subscription/id])]
    ; Find the stripe account for the user.
    (let [subscription (stripe-fn :subscription/cancel
                                     {:customer-id     (:stripe/customer stripe-account)
                                      :subscription-id subscription-id})]
      (transact conn [[:db.fn/retractEntity [:stripe.subscription/id (:stripe.subscription/id subscription)]]]))
    ;; We don't have a subscription ID so we cannot cancel.
    (throw (ex-info "Subscription id not found in database." {:cause ::http/unprocessable-entity
                                                              :data {:stripe-account stripe-account}}))))

(defn newsletter-subscribe [conn email]
  (let [{:keys [verification] :as account} (datomic.format/user-account-map email {:verification/time-limit 0})]
    (mailchimp/subscribe (env :mail-chimp-api-key)
                         (env :mail-chimp-list-id)
                         email
                         (:verification/uuid verification))
    (info "Newsletter subscribe successful, transacting user into datomic.")
    (transact-map conn account)))