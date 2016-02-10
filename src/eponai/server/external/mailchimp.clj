(ns eponai.server.external.mailchimp
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [taoensso.timbre :refer [info error trace]]))

(defn subscribe [api-key email is-vip]
  (info "Subscribing email to newsletter: " email)
  (let [list-id "38cdbb121a"
        url (str "https://us12.api.mailchimp.com/3.0/lists/" list-id "/members")
        data {:email_address email
              :status        "subscribed"
              :list_id       list-id}
        _ (prn "Data: " (json/write-str data))
        req (client/post url {:body       (json/write-str data)
                              :basic-auth ["user" api-key]})]
    (prn "Api key: " req)
    (let [response (json/read-str (:body req) :key-fn keyword)]
      (prn "Response: " response))))
