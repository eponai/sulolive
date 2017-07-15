(ns eponai.server.external.auth0
  (:require
    [clj-http.client :as http]
    [clj-time.core :as time]
    [clj-time.coerce :as time.coerce]
    [clojure.data.json :as json]
    [buddy.core.codecs.base64 :as b64]
    [buddy.sign.jwt :as jwt]
    [eponai.common.database :as db]
    [eponai.server.external.host :as server-address]
    [eponai.common.routes :as routes]
    [taoensso.timbre :refer [debug error info]]
    [buddy.sign.jws :as jws]
    [clojure.string :as string]
    [cemerick.url :as url]
    [eponai.common :as c]
    [slingshot.slingshot :refer [try+]])
  (:import (clojure.lang ExceptionInfo)))

(defn user-id [profile]
  (or (:user_id profile) (:sub profile)))

(defn provider [profile-or-userid]
  (let [user-id (if (map? profile-or-userid)
                  (user-id profile-or-userid)
                  profile-or-userid)]
    (first (string/split user-id #"\|"))))

(defn email-provider? [profile-or-userid]
  (= "email" (provider profile-or-userid)))

(defprotocol IAuth0
  (secret [this] "Returns the jwt secret for unsigning tokens")
  (token-info [this token] "Returns the jwt secret for unsigning tokens")
  (authenticate [this code state] "Returns an authentcation map")
  (refresh [this token] "Takes a token and returns a new one with a later :exp value. Return nil if it was not possible.")
  (should-refresh-token? [this parsed-token] "Return true if it's time to refresh a token"))

(defprotocol IAuth0Management
  (get-token [this])
  (update-user [this user-id params])
  (get-user [this profile])
  (create-email-user [this {:keys [email verified]}])
  (link-with-same-email [this profile])
  (link-user-accounts-by-id [this primary-id secondary-id])
  (unlink-user-accounts [this primary-profile secondary-id secondary-provider]))

(defprotocol IAuth0Endpoint
  (-post [this token path params])
  (-get [this token path params])
  (-delete [this token path]))

(defn- read-json [json]
  (json/read-json json true))

(defn token-expiring-within?
  "Returns true if the token expires in less than provided date.

  Takes an extra 'now' parameter for testing."
  ([token date]
   (token-expiring-within? token date (time/now)))
  ([{:keys [exp]} date now]
   (when exp
     (time/within? now
                   (time/plus now date)
                   (time.coerce/from-long (* 1000 exp))))))

(def auth0authentication-api-host "https://sulo.auth0.com/oauth/token")
(def auth0management-api-host "https://sulo.auth0.com/api/v2")

(defrecord Auth0Management [client-id client-secret domain server-address]
  IAuth0Endpoint
  (-get [this token path params]
    (try+
      (let [{:keys [access_token token_type] :or {token_type "Bearer"}} token
            url (string/join "/" (into [auth0management-api-host] (remove nil?) path))]
        (json/read-str (:body (http/get url
                                        {:query-params params
                                         :headers      {"Authorization" (str token_type " " access_token)}})) :key-fn keyword))
      (catch Object r
        (let [{:keys [body]} r
              {:keys [error error_description]} (json/read-str body :key-fn keyword)]
          (throw (ex-info error_description {:error error :description error_description}))))))

  (-post [this token path params]
    (try+
      (let [{:keys [access_token token_type] :or {token_type "Bearer"}} token
            url (string/join "/" (into [auth0management-api-host] (remove nil?) path))]
        (debug "Post to URL: " url)
        (json/read-str (:body (http/post url {:form-params params
                                              :headers     {"Authorization" (str token_type " " access_token)}})) :key-fn keyword))
      (catch Object r
        (let [{:keys [body]} r
              _ (debug "Got error body" body)
              {:keys [error error_description]} (json/read-str body :key-fn keyword)]
          (throw (ex-info error_description {:error error :description error_description :body body}))))))
  (-delete [this token path]
    (try+
      (let [{:keys [access_token token_type] :or {token_type "Bearer"}} token
            url (string/join "/" (into [auth0management-api-host] (remove nil?) path))]
        (json/read-str (:body (http/delete url {:headers {"Authorization" (str token_type " " access_token)}})) :key-fn keyword))
      (catch Object r
        (let [{:keys [body]} r
              _ (debug "RESPONSE: " r)
              {:keys [error error_description]} (when body (json/read-str body :key-fn keyword))]
          (when error_description
            (throw (ex-info error_description {:error error :description error_description})))))))

  IAuth0Management
  (get-token [this]
    (let [params {:grant_type    "client_credentials"
                  :client_id     client-id
                  :client_secret client-secret
                  :audience      (str auth0management-api-host "/")}
          response (http/post "https://sulo.auth0.com/oauth/token" {:form-params params})]
      (json/read-str (:body response) :key-fn keyword)))
  (get-user [this profile]
    (when (some? (user-id profile))
      (try
        (if (or (not (:email profile))
                (email-provider? profile))
          (-get this (get-token this) ["users" (user-id profile)] nil)
          (throw (ex-info "Not email provider account" {:code :continue})))
        (catch ExceptionInfo e
          (if-some [email (:email profile)]
            ;; Search user account in Auth0 with matching email
            (let [q (str "email.raw:\"" email "\"")
                  accounts (-get this (get-token this) ["users"] {:search_engine "v2" :q q})]

              ;; Get the account with matching email again, just in case the search
              ;; query failed (and Auth0 returns all accounts)
              (let [user (some #(when (= email (:email %)) %) accounts)]
                (-get this (get-token this) ["users" (user-id user)] nil)))
            (when-not (= :continue (:code (ex-data e)))
              (throw e)))))))

  (link-user-accounts-by-id [this primary-id secondary-id]
    (if-not (= primary-id secondary-id)
      (let [token (get-token this)
            secondary-provider (provider secondary-id)]
        (info "Auth0 - User accounts differ, will link accounts: " {:primary primary-id :secondary secondary-id})
        (-post this token ["users" primary-id "identities"] {:provider secondary-provider
                                                             :user_id  secondary-id}))
      (info "Auth0 - User accounts match, will not link" {:id primary-id})))

  (unlink-user-accounts [this primary-profile secondary-id secondary-provider]
    (let [token (get-token this)
          primary-user (get-user this primary-profile)
          secondary-user-id (str secondary-provider "|" secondary-id)]
      (info "Auth0 - Found primary user, will unlink: " primary-user " secondary: " secondary-user-id)
      (when-let [primary-id (:user_id primary-user)]
        (when-not (= primary-id secondary-user-id)
          (debug "Unlink accounts: " {:primary primary-id :secondary secondary-id :provider secondary-provider})
          (-delete this token ["users" primary-id "identities" secondary-provider secondary-id])
          (-delete this token ["users" secondary-user-id])))))

  (link-with-same-email [this profile]
    (when (not-empty (:email profile))
      (info "Auth0 - User has an email, searching for matching accounts to link to " (user-id profile))
      (let [token (get-token this)
            query-string (str "email.raw:\"" (:email profile) "\"")
            accounts (->> (-get this token ["users"] {:search_engine "v2" :q query-string})
                          (filter #(= (:email %) (:email profile)))
                          (into [profile]))]
        (if (not-empty accounts)
          ;; Get main account with the email provider if one exists, if not we want to create one to keep as the main account.
          ;; Link all the accounts with the main account
          (let [main-account (or (some #(when (= "email" (provider %)) %) accounts)
                                 (create-email-user this {:email (:email profile) :verified (:email_verified profile)}))]
            (debug "Auth0 - Found " (count accounts) " with matching email: " (mapv #(user-id %) accounts) ", will link to main account: " (user-id main-account))
            (doseq [secondary accounts]
              (info "Auth0 - Linking accounts: " {:primary (user-id main-account) :secondary (user-id secondary)})
              (try
                (link-user-accounts-by-id this (user-id main-account) (user-id secondary))
                (catch ExceptionInfo e
                  (error e)))))
          (info "Auth0 - Found no matching email account, doing nothing.")))))

  (create-email-user [this {:keys [email verified] :as a}]
    (debug "Auth0 - Creating new user email account: " a)
    (-post this (get-token this) ["users"] {:connection     "email"
                                            :email          email
                                            :email_verified verified})))

(defrecord Auth0 [client-id client-secret server-address]
  IAuth0
  (secret [this] client-secret)
  (token-info [this token]
    (jwt/unsign token (secret this)))
  (authenticate [this code state]
    (letfn [(code->token [code]
              (read-json
                (:body (http/post "https://sulo.auth0.com/oauth/token"
                                  {:form-params {:client_id     client-id
                                                 :redirect_uri  (str (server-address/webserver-url server-address)
                                                                     (routes/path :auth))
                                                 :client_secret client-secret
                                                 :code          code
                                                 :grant_type    "authorization_code"}}))))
            (token->profile [token]
              (read-json
                (:body (http/get "https://sulo.auth0.com/userinfo/?"
                                 {:query-params {"access_token" token}}))))]

      (if (some? code)
        (let [{:keys [id_token access_token token_type] :as to} (code->token code)
              profile (token->profile access_token)]
          ;; TODO: Do we ever need the profile of the token?
          ;; We're not using it right now, so let's avoid that http request.
          ;; profile (token->profile access_token)
          (debug "Got Token auth0: " to)
          {:token        id_token
           :access-token access_token
           :profile      profile
           :redirect-url state
           :token-type   token_type})
        {:redirect-url state})))
  (refresh [this token]
    (letfn [(token->refreshed-token [token]
              (let [response (http/post "https://sulo.auth0.com/delegation"
                                        {:form-params {:client_id     client-id
                                                       :client_secret client-secret
                                                       :grant_type    "urn:ietf:params:oauth:grant-type:jwt-bearer"
                                                       :scope         "openid email"
                                                       :id_token      token}})]
                (debug "Response after requesting a refreshed token: " response)
                (-> response :body (read-json) :id_token)))]
      (try
        (token->refreshed-token token)
        (catch Exception e
          (error "Error refreshing token: " e)
          nil))))
  (should-refresh-token? [this parsed-token]
    (token-expiring-within? parsed-token (time/weeks 1))))

(defrecord FakeAuth0 [datomic]
  IAuth0
  (secret [this]
    (.getBytes "sulo-dev-secret"))
  (token-info [this token]
    (jwt/unsign token (secret this)))
  (authenticate [this code state]
    (let [email code
          now (quot (System/currentTimeMillis) 1000)
          tomorrow (+ now (* 24 3600))
          auth-data {:email                                       email
                     :email_verified                              true
                     :nickname                                    "dev"
                     :iss                                         "localhost"
                     :iat                                         now
                     :exp                                         tomorrow
                     (keyword "https://sulo.live/can_open_store") true}
          jwt-secret (secret this)]
      (debug "Authing self-signed jwt token on localhost with auth-data: " auth-data)
      {:token        (jwt/sign auth-data jwt-secret)
       :profile      auth-data
       :access-token (url/url-encode (c/write-transit auth-data))
       :token-type   "Bearer"
       :redirect-url state}))
  (refresh [this token]
    (letfn [(token->refreshed-token [token]
              (let [{:keys [email]} (token-info this token)]
                (:token (authenticate this email ""))))]
      (try
        (token->refreshed-token token)
        (catch Exception e
          (debug "Unable to parse token: " token " error: " e)
          nil))))
  (should-refresh-token? [this parsed-token]
    (token-expiring-within? parsed-token (time/minutes 59)))

  IAuth0Management
  (get-token [this])
  (update-user [this user-id params])
  (get-user [this profile]
    (debug "GET FAKE USER: " profile)
    (assoc profile :identities [{:profileData {:email (:email profile)}
                                 :connection  "email"}]))
  (create-email-user [this email])
  (link-with-same-email [this profile])
  (link-user-accounts-by-id [this primary-id secondary-id])
  (unlink-user-accounts [this primary-profile secondary-id secondary-provider]))
