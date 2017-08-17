(ns eponai.server.external.firebase
  (:require
    [clj-http.client :as http]
    [taoensso.timbre :refer [debug warn]]
    [cheshire.core :as cheshire]
    [clojure.java.io :as io]
    [eponai.common :as c]
    [eponai.common.firebase :as common.firebase]
    [eponai.common.format.date :as date]
    [buddy.core.codecs.base64 :as b64]
    [com.stuartsierra.component :as component])
  (:import (com.google.firebase FirebaseOptions$Builder FirebaseApp)
           (com.google.firebase.auth FirebaseAuth FirebaseCredentials)
           (com.google.firebase.database FirebaseDatabase DatabaseReference ValueEventListener DataSnapshot DatabaseError)
           (com.google.firebase.tasks OnSuccessListener)))

(defprotocol IFirebaseNotifications
  (-send-chat-notification [this user-id data])
  (-send-notification [this user-id data])
  (-register-device-token [this user-id token])
  (-get-device-token [this user-id]))

(defprotocol IFirebaseAuth
  (-generate-client-auth-token [this user-id claims]))

(defprotocol IFirebaseChat
  (-presence [this])
  (-user-online [this user-id]))

(defn ref->snapshot
  "Takes a ref and returns the DataSnapshot for the ref.

  Throws exception if the call times out or is cancelled."
  [^DatabaseReference ref]
  (let [p (promise)]
    (-> ref
        (.addListenerForSingleValueEvent
          (reify ValueEventListener
            (^void onDataChange [this ^DataSnapshot snapshot]
              (deliver p {:snapshot snapshot}))
            (^void onCancelled [this ^DatabaseError error]
              (debug "FIREBASE - Error retrieving data: " error)
              (deliver p {:error error})))))
    (let [{:keys [snapshot error] :as v} (deref p 1000 ::timedout)]
      (cond (= v ::timedout)
            (throw (ex-info (str "Timed out getting value for database reference: " (.getPath ref))
                            {:ref      ref
                             :ref-path (.getPath ref)}))
            (some? error)
            (throw (ex-info (str "Exception when getting value for firebase reference: " (.getPath ref))
                            {:ref      ref
                             :ref-path (.getPath ref)}
                            (.toException error)))
            :else
            snapshot))))

(defn ref->value [^DatabaseReference ref]
  "Returns the value snapshot for a db ref"
  (.getValue (ref->snapshot ref)))

(defn- create-firebase-db [service-account database-url]
  ;; Initialize app once.
  (let [firebase-app (if (not-empty (FirebaseApp/getApps))
                       (FirebaseApp/getInstance)
                       (with-open [service-account (io/input-stream (b64/decode service-account))]
                         (let [opts (-> (FirebaseOptions$Builder.)
                                        (.setCredential (FirebaseCredentials/fromCertificate service-account))
                                        (.setDatabaseUrl database-url)
                                        (.build))]
                           (FirebaseApp/initializeApp opts))))]
    ;; Once the app has been initialized, get the db instance.
    (FirebaseDatabase/getInstance ^FirebaseApp firebase-app)))

(defn route->ref [database route route-params]
  (.getReference database (common.firebase/path route route-params)))

(defn chat-notification-ref [database user-id]
  (route->ref database :user/unread-chat-notifications {:user-id user-id}))

(defn notification-ref [database user-id]
  (route->ref database :user/unread-notifications {:user-id user-id}))

(defn store-owner-presence
  ([database]
   (doto (route->ref database :user-presence/store-owners {})
     (.keepSynced true)))
  ([database user-id]
   (route->ref database :user-presence/store-owner {:user-id user-id})))

(defrecord Firebase [server-key private-key private-key-id service-account database-url]
  component/Lifecycle
  (start [this]
    (if (:database this)
      this
      (let [db (create-firebase-db service-account database-url)]
        (assoc this :database db
                    ;; TODO: Implement tokens again.
                    :refs {:tokens (.getReference db "v1/tokens")}))))
  (stop [this]
    (dissoc this :database :refs))

  IFirebaseChat
  (-user-online [this user-id]
    (debug "Check online status. user-id: " user-id)
    (when user-id
      (ref->value (store-owner-presence (:database this) user-id))))
  (-presence [this]
    (ref->value (store-owner-presence (:database this))))

  IFirebaseAuth
  (-generate-client-auth-token [this user-id claims]
    (let [fb-instance (FirebaseAuth/getInstance)
          p (promise)]
      (-> (.createCustomToken fb-instance (str user-id) (clojure.walk/stringify-keys claims))
          (.addOnSuccessListener
            (reify OnSuccessListener
              (^void onSuccess [this customtoken]
                (debug "FIREBASE GOT TOKEN: " customtoken)
                (deliver p customtoken)))))
      (deref p 2000 :firebase/token-timeout)))

  IFirebaseNotifications
  (-send-notification [this user-id {:keys [title message type click-action] :as params}]
    (debug "Send notification to: " user-id " params: " params)
    (when (some? user-id)
      (let [user-notification-ref (notification-ref (:database this) user-id)]
        (-> (.push user-notification-ref)
            (.setValue (clojure.walk/stringify-keys {:title        title
                                                     :message      message
                                                     :type         type
                                                     :click-action click-action}))))))

  (-send-chat-notification [this user-id {:keys [title message subtitle] :as params}]
    ;; Some stores in dev doesn't have an owner.
    (when (some? user-id)
      (let [
            ;new-notification {:timestamp (date/current-millis)
            ;                  :type      "chat"
            ;                  :title     (c/substring title 0 100)
            ;                  :subtitle  (c/substring subtitle 0 100)
            ;                  :message   (c/substring message 0 100)}
            user-notifications-ref (chat-notification-ref (:database this) user-id)]
        (debug "Sending notification to user: " user-id " ref-path: " (.getPath user-notifications-ref))
        (-> (.push user-notifications-ref)
            (.setValue true))

        ;; TODO enable when we are ready to send web push notifications
        (comment
          (let [token (-get-device-token this user-id)]
            (debug "FIREBASE - send chat notification: " new-notification)
            (http/post "https://fcm.googleapis.com/fcm/send"
                       {:form-params {:to   token
                                      :data new-notification}
                        :headers     {"Authorization" (str "key=" server-key)}}))))))

  (-register-device-token [this user-id token]
    (when user-id
      (-> (:tokens (:refs this))
          (.child (str user-id))
          (.setValue token))))

  (-get-device-token [this user-id]
    (-> (:tokens (:refs this))
        (.child (str user-id))
        (ref->value))))

(defn firebase-stub []
  (reify
    IFirebaseChat
    (-user-online [this user-id])
    (-presence [this])
    IFirebaseAuth
    (-generate-client-auth-token [this user-id claims]
      "some-token")
    IFirebaseNotifications
    (-send-chat-notification [this user-id data])
    (-send-notification [this user-id data])
    (-register-device-token [this user-id token])
    (-get-device-token [this user-id]
      "some token")))

(defn firebase [{:keys [server-key private-key private-key-id service-account database-url in-prod?] :as conf}]
  (if (and (some? service-account) (some? database-url))
    (map->Firebase {:service-account service-account
                    :server-key      server-key
                    :private-key     private-key
                    :private-key-id  private-key-id
                    :database-url    database-url})
    (do (if in-prod?
          (let [missing-keys (into []
                                   (filter (comp nil? #(get conf %)))
                                   [:service-account :database-url])]
            (throw (ex-info (str "Unable to create a real firebase component. Missing keys: " missing-keys)
                            {:missing-keys missing-keys})))
          (warn "Got nil for service account. Using firebase-stub."))
        (firebase-stub))))