(ns eponai.web.firebase
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require
    [taoensso.timbre :refer [debug error]]
    [om.next :as om]
    [goog.object :as gobj]
    [eponai.common.format.date :as date]
    [eponai.common.shared :as shared]
    [eponai.common.database :as db]
    [eponai.common.firebase :as common.firebase]
    [cljs.core.async :as async]
    [eponai.common :as c]
    [clojure.set :as set]
    [eponai.client.auth :as client.auth]))

(def path common.firebase/path)

(defn- unlisten [listener]
  (listener))

(defn listen-to
  "Puts events from the firebase-route and params and puts them on the out-chan.
  Returns a function that stops listening."
  [database out-chan {:keys [firebase-route firebase-route-params snapshot-keywords events]}]
  (let [firebase-ref (.ref database (path firebase-route firebase-route-params))
        listeners
        (into []
              (comp
                (map name)
                (map (fn [event-type]
                       (let [listener
                             (fn [^js/firebase.database.DataSnapshot snapshot]
                               (async/put!
                                 out-chan
                                 (cond-> {:firebase-route              firebase-route
                                          :firebase-route-params       firebase-route-params
                                          :event-type                  (keyword event-type)
                                          :snapshot                    snapshot
                                          (get snapshot-keywords :key) (.-key snapshot)}
                                         (some? (get snapshot-keywords :val))
                                         (assoc (get snapshot-keywords :val) (.val snapshot)))))]
                         ;; Listen to the event type
                         (.on firebase-ref event-type listener)
                         ;; Return a function that unlistens
                         (fn []
                           (.off firebase-ref event-type listener))))))
              (or events
                  [:child_added
                   :child_changed
                   :child_removed]))]

    (fn []
      ;; Calls unlisten on all listeners
      (run! unlisten listeners))))

(defn locality-listeners [{::keys [special-params]}]
  (when-some [locality (:locality special-params)]
    [{:firebase-route        :visitor-counts
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

(defn user-listeners [{::keys [special-params]}]
  (when-some [user-id (:user-id special-params)]
    [{:firebase-route        :user/unread-chat-notifications
      :firebase-route-params {:user-id user-id}
      :snapshot-keywords     {:key :notification-id
                              :val :notification-val}
      :events                [:child_added
                              :value]}]))

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

(defmethod firebase-event->txs :visitor-counts
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

(defmethod firebase-event->txs :user/unread-chat-notifications
  [{:keys [event-type notification-id notification-val firebase-route-params] :as params}]
  ;; Only care about :child_added, removed will be done by an action.
  (when-let [user-id (c/parse-long-safe (:user-id firebase-route-params))]
    (cond
      (= event-type :child_added)
      [{:db/id                   user-id
        :user/chat-notifications [{:user.chat-notification/id notification-id}]}]
      (and (= event-type :value)
           (nil? notification-val))
      [[:db.fn/retractAttribute user-id :user/chat-notifications]])))

(defn dedupe-txs
  "Takes a coll of transaction vectors and dedupes them, i.e. removes transactions
  that would cancel each other out."
  [txs]
  (->> (reduce (fn [{:keys [seen txs] :as m} tx]
                 ;; Don't care about maps.
                 (if (map? tx)
                   (assoc! m :txs (conj txs tx))
                   ;; x -> [db-fn e a]
                   (let [x (vec (take 3 tx))]
                     (cond-> m
                             ;; assoc tx only if we haven't seen it before.
                             (not (contains? seen x))
                             (-> (assoc! :seen (conj seen x))
                                 (assoc! :txs (conj txs tx)))))))
               (transient {:seen #{} :txs []})
               ;; Walk transactions backwards
               (reverse txs))
       :txs
       ;; Reverse again.
       (reverse)))

(def firebase-timestamp js/firebase.database.ServerValue.TIMESTAMP)

(defprotocol IFirebase2Actions
  (read-chat-notifications [this user-id])
  ;; These actions can be called from (on-route-change [])..
  (register-store-owner-presence [this user-id store-id locality])
  (register-store-visit [this store-id locality])
  (unregister-store-visit [this store-id]))

(defprotocol IFirebase2
  (route-changed [this route-map prev-route-map]))

(defn firebase2 [reconciler user-id]
  (let [event-chan (async/chan (async/sliding-buffer 100))
        close-chan (async/chan)
        state (atom {})
        database (.database js/firebase)
        ;; wait 1 second to avoid spamming firebase renders.
        timeout-between-transacts 1000]
    (go-loop []
      (let [[v c] (async/alts! [event-chan close-chan])]
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
      (route-changed [this route-map prev-route-map]
        (let [listener-id-fn (juxt :firebase-route :firebase-route-params)
              old-listeners (:listeners @state)
              new-listeners (into {}
                                  (map (juxt #(listener-id-fn %) identity))
                                  (all-listeners (assoc-in route-map [::special-params]
                                                           {:user-id  user-id
                                                            :locality (:sulo-locality/path
                                                                        (client.auth/current-locality reconciler))})))

              old-keys (set (keys old-listeners))
              new-keys (set (keys new-listeners))]
          ;; Only do stuff when there's stuff to do.
          (when (not= old-keys new-keys)
            (let [keys-to-stop (set/difference old-keys new-keys)
                  keys-to-start (set/difference new-keys old-keys)]
              (debug "Will listen to new keys: " keys-to-start)
              (debug "Will stop listening to: " keys-to-stop)
              ;; Stop listeners we shouldn't listen to anymore.
              (run! unlisten (map :listener (vals (select-keys old-listeners keys-to-stop))))
              ;; Start new listeners
              (swap! state assoc :listeners
                     (into (or (apply dissoc old-listeners keys-to-stop)
                               {})
                           (map (fn [[id m]]
                                  [id (assoc m :listener (listen-to database event-chan m))]))
                           (select-keys new-listeners keys-to-start))))
            ;; Leave store
            (when (= :store (:route prev-route-map))
              (when (or (not= :store (:route route-map))
                        (not= (:store-id (:route-params route-map))
                              (:store-id (:route-params prev-route-map))))
                (unregister-store-visit this
                                        (:store-id (:route-params prev-route-map)))))
            ;; Enter store
            (when (= :store (:route route-map))
              (when (or (not= :store (:route prev-route-map))
                        (not= (:store-id (:route-params route-map))
                              (:store-id (:route-params prev-route-map))))
                (let [store-id (:store-id (:route-params route-map))]
                  (register-store-visit this
                                        store-id
                                        (-> (db/entity (db/to-db reconciler) store-id)
                                            (get-in [:store/locality :sulo-locality/path])))))))))

      shared/IStoppableComponent
      (stop [this]
        (debug "Stopping firebase!")
        (run! unlisten (map :listener (vals (:listeners @state))))
        (async/close! close-chan))

      IFirebase2Actions
      (register-store-owner-presence [this user-id store-id raw-locality]
        (if-let [locality (cond-> raw-locality (map? raw-locality) :sulo-locality/path)]
          (let [refs [(.ref database (path :user-presence/store-owner {:user-id  user-id
                                                                       :locality locality}))
                      (.ref database (path :store/owner-presence {:user-id  user-id
                                                                  :store-id store-id}))]]
            (let [am-online (.ref database ".info/connected")]
              (.on am-online "value" (fn [snapshot]
                                       (when (.val snapshot)
                                         (run! (fn [ref]
                                                 (.set (.onDisconnect ref) firebase-timestamp)
                                                 (.set ref true))
                                               refs))))))
          (debug "No locality path for raw-locality: " raw-locality
                 " store-id: " store-id
                 " user-id: " user-id)))
      (register-store-visit [this store-id raw-locality]
        (if-let [locality (cond-> raw-locality (map? raw-locality) :sulo-locality/path)]
          (let [^js/firebase.database.Reference store-visitors
                (.ref database (path :store/visitors {:store-id store-id}))
                visitor-ref (.push store-visitors)]
            (swap! state assoc-in [:visitor-refs store-id] visitor-ref)
            (.set visitor-ref locality)
            (.remove (.onDisconnect visitor-ref)))

          (debug "No locality path for raw-locality: " raw-locality
                 " store-id: " store-id
                 " user-id: " user-id)))

      (unregister-store-visit [this store-id]
        (debug "Unregistering, here's the key: " store-id " here's the state: " @state)
        (when-let [visitor-ref (get-in @state [:visitor-refs store-id])]
          (swap! state update :visitor-refs dissoc store-id)
          (.remove visitor-ref)))

      (read-chat-notifications [this user-id]
        (some->> (path :user/unread-chat-notifications {:user-id user-id})
                 (.ref database)
                 (.remove))))))

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

(defn is-initialized? []
  (boolean (not-empty (gobj/get js/firebase "apps"))))

(defn initialize [reconciler]
  (let [{:keys [firebase-api-key firebase-auth-domain firebase-database-url
                firebase-project-id firebase-storage-bucket firebase-messaging-sender-id]}
        (db/singleton-value (db/to-db reconciler) :ui.singleton.client-env/env-map)]
    (.initializeApp js/firebase #js{:apiKey            firebase-api-key
                                    :authDomain        firebase-auth-domain
                                    :databaseURL       firebase-database-url
                                    :projectId         firebase-project-id
                                    :storageBucket     firebase-storage-bucket
                                    :messagingSenderId firebase-messaging-sender-id})
    )
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
  (firebase2 reconciler (client.auth/current-auth reconciler))
  )

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

(comment
  "Old prod firebase"
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