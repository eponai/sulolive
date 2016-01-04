(ns eponai.server.api
  (:require [cemerick.friend :as friend]
            [clojure.core.async :refer [go >! <! chan put!]]
            [compojure.core :refer :all]
            [datomic.api :as d]
            [eponai.server.auth.credentials :as a]
            [eponai.server.datomic.transact :as t]
            [eponai.server.datomic.pull :as p]
            [eponai.server.http :as h]
            [eponai.server.middleware :as m]
            [ring.util.response :as r]))

; Actions

(defn signup
  "Create a new user and transact into datomic.

  Returns channel with username and db-after user is added to use for email verification."
  [conn {:keys [username] :as user-params}]
  (if-not (p/user (d/db conn) username)
    (let [email-chan (chan)]
      (t/new-user conn (a/add-bcrypt user-params :password))
      (put! email-chan username)
      email-chan)
    (throw (ex-info "User already exists."
                    {:cause   ::signup-error
                     :status  ::h/unathorized
                     :data    {:username username}
                     :message "User already exists."}))))

(defn verify
  "Verify the email with the specified uuid, will update the verification entity in the database
  to :verification.status/verified"
  [conn uuid]
  (let [db (d/db conn)
        verification (p/verification db uuid)]
    (if (= (:db/id (verification :verification/status))
           (d/entid db :verification.status/pending))
      (do
        (println "Successful verify")
        (t/add conn (:db/id verification) :verification/status :verification.status/verified))

      (throw (ex-info "Trying to activate invalid verification."
                      {:cause   ::verification-error
                       :status  ::h/unathorized
                       :data    {:uuid uuid}
                       :message "The verification link is no longer valid."})))))

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

(defn post-currency-rates
  "Post currency rates for a specific date. Give a get-rates-fn that is used to retrieve the currency rates.

  get-rates-fn should return a map of the form:
  {:date \"2015-10-10\"
   :rates {:SEK 8.333
           :USD 1.0}})."
  [conn get-rates-fn date]
  (when-not (p/date-conversions (d/db conn) date)
    (t/currency-rates conn (get-rates-fn date))))

(defn handle-parser-request
  [{:keys [::m/conn ::m/parser body] :as request}]
  (parser
    {:state conn
     :auth (friend/current-authentication request)}
    body))

(defn transaction-created
  "Handles response from the 'transaction/create mutation. Does nothing otherwise.

  Will look for :currency-chan and if found post the currency rates for the transactions asynchronousnly."
  [conn get-rates-fn response]
  (let [value (get-in response ['transaction/create :return])
        chan (:currency-chan value)]
    (if chan
      (do (go
            (post-currency-rates conn get-rates-fn (<! chan)))
          (assoc-in response ['transaction/create :return] (dissoc value :currency-chan)))
      response)))

;----------Routes

(defroutes
  user-routes
  (POST "/" {:keys [::m/conn ::m/currency-rates-fn] :as request}
    (r/response
      (->> (handle-parser-request request)
           (transaction-created conn currency-rates-fn)))))

(defroutes
  api-routes
  (context "/api" []
    (POST "/signup" {:keys [::m/send-email-fn
                            params
                            ::m/conn]}
      (go (send-email-fn (<! (signup conn params))))
      (r/redirect "/login.html"))

    ; Requires user login
    (context "/user" _
      (friend/wrap-authorize user-routes #{::a/user}))

    (POST "/verify" request
      (r/response
        (handle-parser-request request)))

    (friend/logout (ANY "/logout" [] (r/redirect "/")))))
