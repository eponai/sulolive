(ns eponai.server.datomic.format
  (:require [clj-time.core :as t]
            [clj-time.coerce :as c]
            [datomic.api :only [db a] :as d]
            [eponai.common.format :as cf]
            [eponai.server.datomic.pull :as p]))

;;; -------------------- Format to entities ----------------

(defn currency-rates
  "Create conversion entities from the given data.

  Takes a map with data (from OpenExchangeRates) of the form:
  {:date \"2010-10-10\" :rates {:SEK 8.3, :USD 1, :NOK 8}}

  Returns a sequence of conversion entities."
  [data]
  (let [map-fn (fn [[code rate]]
                 {:db/id               (d/tempid :db.part/user)
                  :conversion/date     (cf/date (:date data))
                  :conversion/currency [:currency/code (name code)]
                  :conversion/rate     (bigdec rate)})]
    (map map-fn (:rates data))))

(defn currencies
  "Create currency entities from the given data.

  Provide opts including keys for what information is available. Will consider keys
  * :currencies - Takes a data map of the form:
  {:SEK \"Swedish Krona\", :USD \"US Dollar\", \":NOK \"Norwegian Krona\"}.

  * :currency-infos - Takes data sequence of the form
  ({:symbol \"SEK\", :symbol_native \"kr\", :decimal_digits 2, :rounding 0.0, :code \"SEK\"}
  {:symbol \"$\", :symbol_native \"$\", :decimal_digitls 2, :rounding 0.0, :code \"USD\"})

  Returns a sequence of currency entities."
  [opts]
  (let [{:keys [currencies
                currency-infos]} opts]
    (cond currencies
          (let [currency-fn (fn [[c n]] {:db/id         (d/tempid :db.part/user)
                                         :currency/code (name c)
                                         :currency/name n})]
            (map currency-fn currencies))

          currency-infos
          (let [currency-info-fn (fn [info]
                                   {:db/id                   (d/tempid :db.part/user)
                                    :currency/code           (:code info)
                                    :currency/symbol         (:symbol info)
                                    :currency/symbol-native  (:symbol_native info)
                                    :currency/decimal-digits (:decimal_digits info)})]
            (map currency-info-fn currency-infos)))))

;; ------------------------ Create new entities -----------------

(defn user
  "Create user db entity.

  Provide opts including keys that should be specifically set. Will consider keys:
  * :user/status - Activation status of this user, default is :user.status/new.

  Returns a map representing a user entity."
  [email & [opts]]
  (cond->
    {:db/id       (d/tempid :db.part/user)
     :user/uuid   (d/squuid)
     :user/status (or (:user/status opts) :user.status/new)}
    email
    (assoc :user/email email)))

(defn fb-user
  "Create fb-user entity.

  Provide opts including keys that should be specifically set. Will consider keys:
  * :fb-user/id - required, user_id on Facebook.
  * :fb-user/token - required, access_token from Facebook

  Returns a map representing a fb-user entity, or nil if required keys are missing."
  [user opts]
  (let [id (:fb-user/id opts)
        token (:fb-user/token opts)]
    (when (and id
               token)
      {:db/id         (d/tempid :db.part/user)
       :fb-user/id    id
       :fb-user/token token
       :fb-user/user  (:db/id user)})))

(defn verification
  "Create verification db entity belonging to the provided entity (user for email verification).

  Provide opts including keys that should be specifically set. Will consider keys:
  * :verification/status - status of verification, default is :verification.status/pending.
  * :verification/created-at - timestamp if when verification was created, default value is now.
  * :verification/attribute - key in this entity with a value that this verification should verify, default is :user/email.
  * :verification/time-limit - time limit in minutes before this verification should expire, default is 15.

  Returns a map representing a verification entity"
  [entity & [opts]]
  (let [attribute (or (:verification/attribute opts) :user/email)]
    {:db/id                   (d/tempid :db.part/user)
     :verification/status     (or (:verification/status opts) :verification.status/pending)
     :verification/created-at (or (:verification/created-at opts) (c/to-long (t/now)))
     :verification/uuid       (d/squuid)
     :verification/entity     (:db/id entity)
     :verification/attribute  attribute
     :verification/value      (get entity attribute)
     :verification/time-limit (or (:verification/time-limit opts) 15)}))

(defn user-account-map
  "Create entities for a user account.

  Refer to followg functions for considered keys:
  * (user email)
  * (budget user)
  * (fb-user user opts) - returns nil if no fb-user opts provided
  * (verification user opts) - if email is not nil

  Returns a map representing a user account map including keys
  #{:user :budget :verification(if email not nil) :fb-user(if not nil)}"
  [email & [opts]]
  (let [user (user email opts)
        fb-user (fb-user user opts)]
    (cond->
      {:user   user
       :budget (cf/budget (:db/id user) opts)}

      email
      (assoc :verification (verification user opts))

      fb-user
      (assoc :fb-user fb-user))))