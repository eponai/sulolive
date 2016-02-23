(ns eponai.server.external.facebook
  (:require [cemerick.url :as url]
            [clj-http.client :as client]
            [clojure.data.json :as json]
            [ring.util.response :refer [redirect]]))

(defn login-dialog
  ([app-id]
    (login-dialog app-id "https://www.facebook.com/connect/login_success.html"))
  ([app-id redirect-url]
   (redirect
     (-> (url/url "https://www.facebook.com/dialog/oauth?")
         (assoc :query {:client_id    app-id
                        :redirect_uri redirect-url
                        :scope        "email"})
         str))))

(defn- code->access-token
  "Change the code recieved from Facebook to an access token for the session."
  [app-id secret code redirect-url]
  (json/read-str
    (:body (client/get
             (-> (url/url "https://graph.facebook.com/v2.3/oauth/access_token")
                 (assoc :query {:client_id     app-id
                                :redirect_uri  redirect-url
                                :client_secret secret
                                :code          code})
                 str)))
    :key-fn
    keyword))

(defn- inspect-token
  "Inspect the access token, will return a map with the matching app id and user id that can be used for validation."
  [app-id secret token]
  (json/read-str
    (:body (client/get
             (-> (url/url "https://graph.facebook.com/debug_token")
                 (assoc :query {:input_token  (:access_token token)
                                :access_token (str app-id "|" secret)})
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

(defn validated-token
  "Validate and inspect the code returned from Facebook, and return the data matching the code.
  Will match the app id for the token's app id, and return a user id that can be matched with our db."
  [app-id app-secret code redirect-url]
  ; We get a code back from Facebook after successful login. Since it's a GET parameter,
  ; someone could easily try and fake it. So we need to verify that it's valid.
  (let [access-token (code->access-token app-id app-secret code redirect-url)]
    ; If we get an error, just return that error map
    (if (:error access-token)
      access-token
      ; After receiving the access token, we need to inspect it to find if it's valid and matching a user.
      ; This is how we can verify that the token coming back is actually for our app and matchint the same
      ; person that requested it.
      (let [{:keys [data] :as inspected} (inspect-token app-id app-secret access-token)]
        ; If we get an error back, return the error map
        (if (:error inspected)
          inspected
          ; Check if the token is valid, and the app id is matching our app id, otherwise return error.
          (if (and (= (:app_id data)
                        app-id)
                     (:is_valid data))
            (assoc data :access_token (:access_token access-token)
                        :fb-info-fn user-info)
            {:error {:message "Invalid access token."}}))))))