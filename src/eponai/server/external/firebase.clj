(ns eponai.server.external.firebase
  (:require
    [clj-http.client :as http]
    [taoensso.timbre :refer [debug]]
    [cheshire.core :as cheshire]
    [clojure.java.io :as io]
    [eponai.common :as c]
    [eponai.common.format.date :as date])
  (:import (com.google.firebase FirebaseOptions FirebaseOptions$Builder FirebaseApp)
           (com.google.firebase.auth FirebaseCredential FirebaseCredentials)
           (com.google.firebase.database FirebaseDatabase DatabaseReference ValueEventListener DataSnapshot DatabaseError DatabaseReference$CompletionListener)))

(def firebase-db "https://leafy-firmament-160421.firebaseio.com/")

(defonce database
         (with-open
           [service-account (io/input-stream (io/resource "private/leafy-firmament-160421.json"))]
           (let [opts (->
                        (FirebaseOptions$Builder.)
                        (.setCredential (FirebaseCredentials/fromCertificate service-account))
                        (.setDatabaseUrl firebase-db)
                        (.build))]
             (FirebaseApp/initializeApp opts)
             (-> (FirebaseDatabase/getInstance)
                 (.getReference "server/sulolive")))))

(defn db-ref->value [^DatabaseReference db cb]
  "Returns the value snapshot for a db ref"
  (do (-> db
          (.addValueEventListener
            (reify ValueEventListener
              (^void onDataChange [this ^DataSnapshot snapshot]
                (cb snapshot))
              (^void onCancelled [this ^DatabaseError error]
                (debug "FIREBASE - Error retrieving data: " error)))))))


(defprotocol IFirebaseNotifications
  (-send [this user-id data])
  (-register-token [this user-id token])
  (-get-token [this user-id cb]))


(defrecord Firebase [server-key private-key private-key-id]
  IFirebaseNotifications
  (-send [this user-id {:keys [title message subtitle] :as params}]
    (let [new-notification {:timestamp (date/current-millis)
                            :title     (c/substring title 0 100)
                            :subtitle  (c/substring subtitle 0 100)
                            :message   (c/substring message 0 100)}
          notifications-ref (.child database "notifications")
          user-notifications-ref (.child notifications-ref (str user-id))]
      (-> (.push user-notifications-ref)
          (.setValue (clojure.walk/stringify-keys new-notification)))
      (-get-token this user-id (fn [token]
                                 (debug "FIREBASE - send chat notification")
                                 (http/post "https://fcm.googleapis.com/fcm/send"
                                            {:form-params {:to   token
                                                           :data new-notification}
                                             :headers     {"Authorization" (str "key=" server-key)}})))))
  (-register-token [this user-id token]
    (when user-id

      (let [tokens-ref (.child database "tokens")]
        (.updateChildren tokens-ref {(str user-id) token}))))
  (-get-token [this user-id cb]
    (db-ref->value
      (.child database "tokens")
      (fn [tokens]
        (let [token (some-> (.getValue tokens)
                            (.get (str user-id)))]
          (cb token))))))

(defn firebase [{:keys [server-key private-key private-key-id]}]
  (map->Firebase {:server-key server-key :private-key private-key :private-key-id private-key-id}))