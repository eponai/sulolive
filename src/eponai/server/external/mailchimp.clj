(ns eponai.server.external.mailchimp
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [taoensso.timbre :refer [info error trace debug]])
  (:import [java.math BigInteger]
           [java.security MessageDigest]))

(defn hash-MD5
  "Use java interop to flexibly hash strings"
  [^String string]
  (let [hashed (doto (MessageDigest/getInstance "MD5")
                 (.reset)
                 (.update (.getBytes string)))]
    (.toString (BigInteger. 1 (.digest hashed)) 16)))

;; TODO: Use the protocol and stuff in core.clj
;; TODO: Check out usages of the original subscribe fn (not the one in the defrecord).

(defprotocol IMailChimp
  (subscribe [this params] "Subscribe to a list, where params is a map with :list-id :email and :merge-fields."))

(defrecord MailChimp [api-key]
  IMailChimp
  (subscribe [_ {:keys [list-id email merge-fields]}]
    (let [hashed-email (hash-MD5 (.toLowerCase email))
          url (str "https://us14.api.mailchimp.com/3.0/lists/" list-id "/members/" hashed-email)
          data {:email_address email
                :status        "pending"
                :merge_fields  merge-fields}
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
