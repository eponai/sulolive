(ns eponai.web.firebase
  (:require
    [taoensso.timbre :refer [debug]]
    [om.next :as om]
    [eponai.common.format.date :as date]))

(defn request-permission [messaging & [f-success f-error]]
  (-> (.requestPermission messaging)
      (.then (fn []
               (when f-success (f-success))
               (debug "Firebase - Token access granted ")))
      (.catch (fn [err]
                (when f-error (f-error err))
                (debug "Firebase - Token access denied " err)))))

(defn get-token [messaging f]
  (-> (.getToken messaging)
      (.then (fn [token]
               (if token
                 (do
                   (f token)
                   (debug "Firebase - Received token: " token))
                 (debug "Firebase - No token avaiable, generate one"))))))

(defn on-message [messaging reconciler]
  (.onMessage messaging (fn [payload]
                          (debug "FIREBASE - Received message with payload: " payload)
                          ;(let [timestamp (date/current-millis)
                          ;      notification {:notification/id      timestamp
                          ;                    :notification/payload {:payload/title (aget payload "data" "title]")
                          ;                                           :payload/body  (aget payload "data" "message]")
                          ;                                           :payload/subtitle (aget payload "data" "subtitle]")}}]
                          ;  (om/transact! reconciler [(list 'notification/receive notification)
                          ;                            {:query/notifications [:notification/id :notification/payload]}])
                          ;  (js/setTimeout (fn [] (om/transact! reconciler [(list 'notification/remove {:id timestamp})
                          ;                                                  {:query/notifications [:notification/id :notification/payload]}])) 5000)
                          ;  )
                          )))

(defn initialize [reconciler]
  (.initializeApp js/firebase #js{:apiKey            "AIzaSyBS99Iuv0WAacB4ain4zQMahksDrt6jmJY"
                                  :authDomain        "leafy-firmament-160421.firebaseapp.com"
                                  :databaseURL       "https://leafy-firmament-160421.firebaseio.com"
                                  :projectId         "leafy-firmament-160421"
                                  :storageBucket     "leafy-firmament-160421.appspot.com"
                                  :messagingSenderId "252203166563"})
  (if (.-serviceWorker js/navigator)
    (.addEventListener js/window
                       "load"
                       (fn []
                         (debug "Firebase register service worker")
                         (-> (.register (.-serviceWorker js/navigator) "/lib/firebase/firebase-messaging-sw.js")
                             (.then (fn [registration]
                                      (let [messaging (.messaging js/firebase)
                                            save-token #(om/transact! reconciler [(list 'firebase/register-token {:token %})])]
                                        (.useServiceWorker messaging registration)
                                        (request-permission messaging (fn [] (get-token messaging save-token)))
                                        (.onTokenRefresh messaging (fn [] (get-token messaging save-token)))
                                        (on-message messaging reconciler)))))))))