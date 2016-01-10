(ns eponai.server.api
  (:require [clojure.core.async :refer [go >! <! chan put!]]
            [compojure.core :refer :all]
            [datomic.api :as d]
            [eponai.common.database.pull :as pull]
            [eponai.server.datomic.transact :as t]
            [eponai.server.datomic.pull :as p]
            [eponai.server.http :as h]
            [clj-time.core :as time]
            [clj-time.coerce :as c]
            [eponai.common.format :as f]))

; Actions

(defn signin
  "Create a new user and transact into datomic.

  Returns channel with username and db-after user is added to use for email verification."
  [conn email]
  (println "Signin: " (p/user (d/db conn) email))
  (if email
    (let [user (p/user (d/db conn) email)
          email-chan (chan 1)]
      (if user
        (let [verification (t/email-verification conn user :verification.status/pending)]
          (println "New verification " (:verification/uuid verification) "for user... " email)
          (put! email-chan verification)
          email-chan)
        (let [verification (t/new-user conn email)]
          (println "Craeting new user with email... " email)
          (put! email-chan verification)
          (println "Done!")
          email-chan))))
    (throw (ex-info "Trying to signup with nil email."
                    {:cause ::signup-error})))

(defn verify-email
  "Try and set the verification status if the verification with the specified uuid to :verification.status/verified.

  If more than 15 minutes has passed since the verification entity was created, sets the status to
  :verification.status/expired and throws exception.

  If the verification does not have status :verification.status/pending,
  it means that it's already verified or expired, throws exception."
  [conn uuid]
  (let [ex (fn [msg] (ex-info msg
                              {:cause   ::verification-error
                               :status  ::h/unathorized
                               :data    {:uuid uuid}
                               :message "The verification link is invalid."}))]
    (if-let [{:keys [verification/status] :as verification} (pull/verification (d/db conn) '[*] uuid)]
      (let [verification-time (c/from-long (:verification/created-at verification))
            time-interval (time/in-minutes (time/interval verification-time (time/now)))]
        (println "Verification: " verification)
        ; If the verification was not used within 15 minutes, it's expired.
        (if (>= 15 time-interval)
           ;If verification status is not pending, we've already verified this or it's expired.
           ;(if (= (:db/id status)
           ;       (d/entid db :verification.status/pending)))
           (do
             (println "Successful verify")
             (t/add conn (:db/id verification) :verification/status :verification.status/verified)
             (:verification/entity verification))
           ;(throw (ex "Invalid verification UUID"))
           (do
            (t/add conn (:db/id verification) :verification/status :verification.status/expired)
            (throw (ex "Expired verification UUID")))))
      ; If verification does not exist, throw invalid error
      (throw (ex "Verification UUID does not exist")))))

(defn create-account [conn user-uuid email]
  {:pre [(string? user-uuid)
         (string? email)]}
  (if-let [{user-db-id :db/id} (pull/pull (d/db conn) '[:db/id] [:user/uuid (f/str->uuid user-uuid)])]
    (let [new-db (:db-after (t/add conn user-db-id :user/email email))
          verifications (pull/verifications new-db user-db-id :verification.status/verified)]

      ; There's no verification verified for this email on the user,
      ; the user is probably creating an account with a new email.
      ; Create a new verification and throw exception
      (when-not (seq verifications)
        (println "User not verified for email: " email "... creating new verification.")
        (let [verification (t/email-verification conn user-db-id :verification.status/pending)]
          (throw (ex-info "Email not verified."
                          {:cause        ::authentication-error
                           :data         {:email email}
                           :message      "Email not verified"
                           :verification verification}))))

      ; Activate this account and return the user entity
      (let [activated-db (:db-after (t/add conn user-db-id :user/status :user.status/activated))]
        (println "Activated account! Returning user: " (pull/pull activated-db '[:user/email {:user/status [:db/ident]}] [:user/email email]))
        (pull/user activated-db email )))
    ; The user uuid was not found in the database.
    (throw (ex-info "Invalid User id." {:cause   ::authentication-error
                                        :data    {:uuid  user-uuid
                                                  :email email}
                                        :message "No user exists for UUID"}))))

(defn post-currencies
  "Post currencies into the database of the following form:
  {:SEK \"Swedish Krona\"
   :USD \"US Dollar\"}. "
  [conn curs]
  (t/currencies conn curs))

(defn post-currency-info
  "Post information about currencies with a map of the form:
   {:SGD {:symbol \"SGD\", :symbol_native \"$\", :decimal_digits 2, :rounding 0.0, :code \"SGD\"}},"
  [conn cur-infos]
  (t/currency-infos conn cur-infos))
