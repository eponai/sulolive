(ns eponai.server.external.facebook
  (:require
    [cemerick.url :as url]
    [clj-http.client :as client]
    [clojure.data.json :as json]
    [ring.util.response :refer [redirect]]
    [taoensso.timbre :refer [info debug error]]))

;(defn login-dialog
;  ([app-id]
;    (login-dialog app-id "https://www.facebook.com/connect/login_success.html"))
;  ([app-id redirect-url]
;   (redirect
;     (-> (url/url "https://www.facebook.com/dialog/oauth?")
;         (assoc :query {:client_id    app-id
;                        :redirect_uri redirect-url
;                        :scope        "email,name"})
;         str))))

;(defn- code->access-token
;  "Change the code recieved from Facebook to an access token for the session."
;  [app-id secret code redirect-url]
;  (json/read-str
;    (:body (client/get
;             (-> (url/url "https://graph.facebook.com/v2.3/oauth/access_token")
;                 (assoc :query {:client_id     app-id
;                                :redirect_uri  redirect-url
;                                :client_secret secret
;                                :code          code})
;                 str)))
;    :key-fn
;    keyword))
(defn- short-lived->long-lived [app-id secret access-token]
  (json/read-str
    (:body (client/get
             (-> (url/url "https://graph.facebook.com/v2.8/oauth/access_token?")
                 (assoc :query {:grant_type        "fb_exchange_token"
                                :client_id         app-id
                                :client_secret     secret
                                :fb_exchange_token access-token})
                 str)))
    :key-fn
    keyword))

(defn- inspect-token
  "Inspect the access token, will return a map with the matching app id and user id that can be used for validation."
  [access-token input-token]
  (json/read-str
    (:body (client/get
             (-> (url/url "https://graph.facebook.com/debug_token?")
                 (assoc :query {:input_token  input-token
                                :access_token access-token})
                 str)))
    :key-fn
    keyword))

(defn user-info [user-id access-token]
  (json/read-str
    (:body (client/get (-> (url/url "https://graph.facebook.com/v2.5/" user-id)
                           (assoc :query {:access_token access-token
                                          :fields "id,name,email,picture"})
                           str)))
    :key-fn
    keyword))

(defn user-token-validate
  ([app-id app-secret params]
    (user-token-validate
      app-id app-secret params {:to-long-lived-token-fn short-lived->long-lived
                                :user-info-fn           user-info
                                :inspect-token-fn       inspect-token}))
  ([app-id app-secret {:keys [access-token user-id]} {:keys [to-long-lived-token-fn
                                                             user-info-fn
                                                             inspect-token-fn]}]
   {:pre [(some? app-id)
          (some? app-secret)
          (some? access-token)
          (some? user-id)]}
   (let [{:keys [data] :as inspected} (inspect-token-fn (str app-id "|" app-secret) access-token)

         token-is-valid? (and
                           ; Cond 1) The app ID for this token matches our app's appID with Facebook.
                           (= (:app_id data)
                              app-id)
                           ; Cond 2) The user ID for this token matches the user id that requested the auth.
                           (= (:user_id data)
                              user-id)
                           ; Cond 3) The access token is still valid with Facebook (i.e. not expired or otherwise invalidated)
                           (:is_valid data))]
     (cond
       ; Check if error is returned from Facebook when inspecting the token.
       (:error inspected)
       inspected

       ; Check if the token is valid, and the app id is matching our app id.
       token-is-valid?
       ; The access token received from Facebook in the client is short lived and only valid for a few hours.
       ; We exchange the short lived one for a long lived token here, valid for about 60 days.
       (let [long-lived-token (to-long-lived-token-fn app-id app-secret access-token)]
         {:user_id      user-id
          :access_token (:access_token long-lived-token)
          :fb-info-fn   user-info-fn})

       :else
       (or (:error inspected)
           {:error {:message "Invalid access token."
                    :data    data}})))))

;(defn validated-token
;  "Validate and inspect the code returned from Facebook, and return the data matching the code.
;  Will match the app id for the token's app id, and return a user id that can be matched with our db."
;  [app-id app-secret code redirect-url]
;  ; We get a code back from Facebook after successful login. Since it's a GET parameter,
;  ; someone could easily try and fake it. So we need to verify that it's valid.
;  (let [access-token (code->access-token app-id app-secret code redirect-url)]
;    ; If we get an error, just return that error map
;    (if (:error access-token)
;      access-token
;      ; After receiving the access token, we need to inspect it to find if it's valid and matching a user.
;      ; This is how we can verify that the token coming back is actually for our app and matchint the same
;      ; person that requested it.
;      (let [{:keys [data] :as inspected} (inspect-token (str app-id "|" app-secret) (:access-token access-token))]
;        ; If we get an error back, return the error map
;        (if (:error inspected)
;          inspected
;          ; Check if the token is valid, and the app id is matching our app id, otherwise return error.
;          (if (and (= (:app_id data)
;                        app-id)
;                     (:is_valid data))
;            (assoc data :access_token (:access_token access-token)
;                        :fb-info-fn user-info)
;            {:error {:message "Invalid access token."}}))))))