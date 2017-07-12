(ns eponai.web.firebase
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require
    [taoensso.timbre :refer [debug error]]
    [om.next :as om]
    [eponai.common.format.date :as date]
    [eponai.common.shared :as shared]
    [eponai.common.database :as db]
    [eponai.common.firebase :as common.firebase]
    [cljs.core.async :as async]
    [eponai.common :as c]
    [clojure.set :as set]))

(def path common.firebase/path)

(defn listen-to
  "Puts events from the firebase-route and params and puts them on the out-chan.
  Returns a function that stops listening."
  [database out-chan {:keys [firebase-route firebase-route-params snapshot-keywords]}]
  (let [visitor-counts (.ref database (path firebase-route firebase-route-params))
        listeners
        (into []
              (map (fn [event-type]
                     (.on visitor-counts
                          event-type
                          (fn [^js/firebase.database.DataSnapshot snapshot]
                            (async/put!
                              out-chan
                              (cond-> {:firebase-route              firebase-route
                                       :firebase-route-params       firebase-route-params
                                       :event-type                  (keyword event-type)
                                       :snapshot                    snapshot
                                       (get snapshot-keywords :key) (.-key snapshot)}
                                      (some? (get snapshot-keywords :val))
                                      (assoc (get snapshot-keywords :val) (.val snapshot))))))))
              ["child_added"
               "child_changed"
               "child_removed"])]

    (fn []
      (run! (fn [listener]
              (.off visitor-counts listener))
            listeners))))

(defn- unlisten [listener]
  (listener))

(defn locality-listeners [{:keys [route-params]}]
  (when-some [locality (:locality route-params)]
    [{:firebase-route        :visitor-count
      :firebase-route-params {:locality locality}
      :snapshot-keywords     {:key :store-id
                              :val :count}}
     {:firebase-route        :user-presence/store-owners
      :firebase-route-params {:locality locality}
      :snapshot-keywords     {:key :user-id
                              :val :timestamp}}]))

(defn store-listeners [{:keys [route-params]}]
  (when-some [store-id (:store-id route-params)]
    [{:firebase-route        :store/owner-presences
      :firebase-route-params {:store-id store-id}
      :snapshot-keywords     {:key :user-id
                              :val :timestamp}}]))

(defn user-listeners [{:keys [route-params]}]
  (when-some [user-id (:user-id route-params)]
    [{:firebase-route        :user/chat-notifications
      :firebase-route-params {:user-id user-id}
      :snapshot-keywords     {:key :notification-id}}]))

(defn all-listeners
  "Returns all listeners we should listen for given a route-map.
  The functions return nil when they shouldn't be listened to."
  [route-map]
  (-> []
      (into (locality-listeners route-map))
      (into (store-listeners route-map))
      (into (user-listeners route-map))))

;; Return datascript transactions.
;; Use vectors for transactions as we can dedupe them.
(defmulti firebase-event->txs :firebase-route)
(defmethod firebase-event->txs :default
  [e]
  (debug "No firebase event-handler for event: " e))

(defmethod firebase-event->txs :visitor-count
  [{:keys [store-id count event-type]}]
  (when-let [store-id (c/parse-long-safe store-id)]
    (if (= event-type :child_removed)
      [[:db.fn/retractAttribute store-id :store/visitor-count]]
      [[:db/add store-id :store/visitor-count count]])))

(defmethod firebase-event->txs :user-presence/store-owners
  [{:keys [event-type user-id timestamp]}]
  (when-let [user-id (c/parse-long-safe user-id)]
    (if (= event-type :child_removed)
      [[:db.fn/retractAttribute user-id :user/online?]]
      [[:db/add user-id :user/online? timestamp]])))

(defmethod firebase-event->txs :store/owner-presences
  [{:keys [event-type user-id timestamp]}]
  (when-let [user-id (c/parse-long-safe user-id)]
    (if (= event-type :child_removed)
      [[:db.fn/retractAttribute user-id :user/online?]]
      [[:db/add user-id :user/online? timestamp]])))

(defmethod firebase-event->txs :user/chat-notifications
  [{:keys [event-type] :as params}]
  (debug "Got chat-notification with params:" params)
  (error "DOING NOTHING WITH THIS CHAT NOTIFICATION FYI."))

(defn dedupe-txs
  "Takes a coll of transaction vectors and dedupes them, i.e. removes transactions
  that would cancel each other out."
  [txs]
  (->> (reduce (fn [{:keys [seen txs] :as m} [db-fn e a :as tx]]
                 (let [x [db-fn e a]]
                   (cond-> m
                           ;; assoc tx only if we haven't seen it before.
                           (not (contains? seen x))
                           (-> (assoc! :seen (conj seen x))
                               (assoc! :txs (conj txs tx))))))
               (transient {:seen #{} :txs []})
               ;; Walk transactions backwards
               (reverse txs))
       :txs
       ;; Reverse again.
       (reverse)))

(defprotocol IFirebase2
  (route-changed [this route-map])
  (stop [this]))

(defn firebase2 [reconciler]
  (let [event-chan (async/chan (async/sliding-buffer 100))
        close-chan (async/chan)
        state (atom {})
        database (.database js/firebase)
        ;; wait 1 frame
        timeout-between-transacts 16]
    (go-loop []
      (let [[v c] (async/alts! event-chan close-chan)]
        (condp = c
          close-chan
          (debug "Exiting firebase!")
          event-chan
          (let [txs (into []
                          (comp (take-while some?)
                                (mapcat firebase-event->txs))
                          (cons v (repeatedly #(async/poll! event-chan))))
                _ (debug "Found firebase events: " (count txs))
                ;; Removes transactions that would cancel out each other.
                txs (dedupe-txs txs)]
            (debug "Deduped txs down to: " (count txs))
            (debug "will transact firebase txs: " txs)
            ;; TODO: Add reads here for re-render?
            (try
              ;; Using transact to enter the data to our database
              ;; because it won't affect the backend.cljc loop.
              ;; It'll have to be re-transacted everytime we get
              ;; something from the backend though.
              ;; We may want to reconsider this.
              (om/transact! reconciler `[(~'firebase/transact-event ~{:txs txs})])
              (catch :default e
                (error "Error transacting transactions: " txs " error: " e)
                (error "stack: " (.-stack e))))
            (async/<! (async/timeout timeout-between-transacts))
            (recur)))))
    (reify IFirebase2
      (stop [this]
        (async/close! close-chan))

      (route-changed [_ route-map]
        (let [listener-id-fn (juxt :firebase-route :firebase-route-params)
              old-listeners (:listeners @state)
              new-listeners (into {}
                                  (map (juxt #(listener-id-fn %) identity))
                                  (all-listeners route-map))

              old-keys (set (keys old-listeners))
              new-keys (set (keys new-listeners))

              keys-to-stop (set/difference old-keys new-keys)
              keys-to-start (set/difference new-keys old-keys)
              ]
          (debug "Will listen to new keys: " keys-to-start)
          (debug "Will stop listening to: " keys-to-stop)
          ;; Stop listeners we shouldn't listen to anymore.
          (run! unlisten (vals (select-keys old-listeners keys-to-stop)))
          ;; Start new listeners
          (swap! state assoc :listeners
                 (into (apply dissoc keys-to-stop)
                       (map (fn [[id m]]
                              [id (listen-to database event-chan m)]))
                       (select-keys new-listeners keys-to-start))))))))

;; store-owner-presences (.ref database (path :user-presence/store-owners {:locality locality}))




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
  (let [{:keys [firebase-api-key firebase-auth-domain firebase-database-url
                firebase-project-id firebase-storage-bucket firebase-messaging-sender-id]}
        (db/singleton-value (db/to-db reconciler) :ui.singleton.client-env/env-map)]
    (.initializeApp js/firebase #js{:apiKey            firebase-api-key
                                    :authDomain        firebase-auth-domain
                                    :databaseURL       firebase-database-url
                                    :projectId         firebase-project-id
                                    :storageBucket     firebase-storage-bucket
                                    :messagingSenderId firebase-messaging-sender-id}))
  ;; TODO enable when we want to activate Web push notifications.
  ;; We might want to think about when to ask the user's permission first.
  (comment
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
                                          (on-message messaging reconciler))))))))))

(defn snapshot->map [snapshot]
  {:key   (.-key snapshot)
   :ref   (.-ref snapshot)
   :value (js->clj (.val snapshot))})

(defprotocol IFirebase
  (-ref [this path])

  (-timestamp [this])
  (-add-connected-listener [this ref {:keys [on-connect on-disconnect]}])

  (-remove-on-disconnect [this ref])

  (-limit-to-last [this n ref])

  (-set [this ref v])
  (-push [this ref])
  (-remove [this ref])
  (-update [this ref v])

  (-off [this ref])
  (-on-value-changed [this f ref])
  (-on-child-added [this f ref])
  (-on-child-removed [this f ref])
  (-once [this f ref]))

(defonce fb-initialized? (atom false))

(defmethod shared/shared-component [:shared/firebase :env/prod]
  [reconciler _ _]
  ;(initialize reconciler)
  ;(when-not @fb-initialized?
  ;
  ;  (reset! fb-initialized? true))
  (reify IFirebase
    (-remove-on-disconnect [this ref]
      (-> (.onDisconnect ref)
          (.remove)))
    (-timestamp [this]
      js/firebase.database.ServerValue.TIMESTAMP)
    (-add-connected-listener [this ref {:keys [on-connect on-disconnect]}]
      (let [am-online (-> (.database js/firebase)
                          (.ref ".info/connected"))]
        (.on am-online "value" (fn [snapshot]
                                 (when (.val snapshot)
                                   (-> (.onDisconnect ref)
                                       on-disconnect)
                                   (on-connect ref))))))

    ;; Refs
    (-ref [this path]
      (-> (.database js/firebase)
          (.ref (str "v1/" path))))

    (-limit-to-last [this n ref]
      (.limitToLast ref n))

    (-remove [this ref]
      (debug "FIREBASE removed: " ref)
      (.remove ref))

    (-push [this ref]
      (.push ref))
    (-set [this ref v]
      (debug "Setting value: " v)
      (.set ref v))
    (-update [this ref v]
      (.update ref v))

    (-off [this ref]
      (when ref
        (.off ref)))

    ;; Listeners
    (-on-value-changed [this f ref]
      (.on ref "value" (fn [snapshot] (f (snapshot->map snapshot)))))
    (-on-child-added [this f ref]
      (.on ref "child_added" (fn [snapshot] (f (snapshot->map snapshot)))))
    (-on-child-removed [this f ref]
      (.on ref "child_removed" (fn [snapshot] (f (snapshot->map snapshot)))))
    (-once [this f ref]
      (.once ref "value" (fn [snapshot] (f (snapshot->map snapshot)))))))

(defmethod shared/shared-component [:shared/firebase :env/dev]
  [reconciler _ _]
  (reify IFirebase
    (-ref [this path])
    (-timestamp [this])
    (-add-connected-listener [this ref {:keys [on-connect on-disconnect]}])

    (-remove-on-disconnect [this ref])

    (-limit-to-last [this n ref])

    (-set [this ref v])
    (-push [this ref])
    (-remove [this ref])
    (-update [this ref v])

    (-off [this ref])
    (-on-value-changed [this f ref])
    (-on-child-added [this f ref])
    (-on-child-removed [this f ref])
    (-once [this f ref])))