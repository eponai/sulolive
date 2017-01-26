(ns eponai.server.external.mailchimp
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [taoensso.timbre :refer [info error trace debug]]))

(defn hash-MD5
  "Use java interop to flexibly hash strings"
  [string]
  (let [hashed
        (doto (java.security.MessageDigest/getInstance "MD5")
          (.reset)
          (.update (.getBytes string)))]
    (.toString (new java.math.BigInteger 1 (.digest hashed)) 16)))

(defn subscribe [api-key list-id {:keys [name email site] :as params}]
  (info "SUBSCRIBE VENDOR TO BETA: " params)
  (let [lower-email (.toLowerCase email)
        hashed-email (hash-MD5 lower-email)
        url (str "https://us14.api.mailchimp.com/3.0/lists/" list-id "/members/" hashed-email)
        data {:email_address email
              :status        "pending"
              :merge_fields  {"NAME" name
                              "SITE" site}}
        req (client/put url {:body       (json/write-str data)
                             :basic-auth ["user" api-key]})]
    (let [response (json/read-str (:body req) :key-fn keyword)]
      (info "Subscribed user " email)
      (debug "Mailchimp response: " response)
      response))
  )