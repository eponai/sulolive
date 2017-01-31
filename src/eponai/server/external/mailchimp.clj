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

;; TODO: Use the protocol and stuff in core.clj
;; TODO: Check out usages of the original subscribe fn (not the one in the defrecord).

(defprotocol IMailChimp
  (subscribe [this params]))

(defrecord MailChimp [api-key]
  IMailChimp
  (subscribe [_ {:keys [list-id name email site]}]
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
        response))))

(defn mail-chimp [api-key]
  (->MailChimp api-key))

(defn mail-chimp-stub []
  (reify IMailChimp
    (subscribe [_ params]
      (debug "DEV - Fake subscribing email to mail-chimp with params: " params))))
