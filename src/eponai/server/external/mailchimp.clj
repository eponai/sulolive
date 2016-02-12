(ns eponai.server.external.mailchimp
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [taoensso.timbre :refer [info error trace]]))

(defn subscribe [api-key list-id email uuid]
  (info "Subscribing email to newsletter: " email)
  (let [url (str "https://us12.api.mailchimp.com/3.0/lists/" list-id "/members")
        data {:email_address email
              :status        "subscribed"
              :list_id       list-id
              :merge_fields  {"UUID" (str uuid)}}
        req (client/post url {:body       (json/write-str data)
                              :basic-auth ["user" api-key]})]
    (let [response (json/read-str (:body req) :key-fn keyword)]
      (info "Subscribed user " email))))
