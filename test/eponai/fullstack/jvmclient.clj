(ns eponai.fullstack.jvmclient
  (:require [om.next :as om]
            [om.dom :as dom]
            [om.util]
            [eponai.common.parser :as parser]
            [eponai.client.parser.mutate]
            [eponai.client.parser.read]
            [eponai.client.backend :as backend]
            [eponai.client.utils :as utils]
            [eponai.client.remotes :as remotes]
            [eponai.client.parser.merge :as merge]
            [eponai.fullstack.utils :as fs.utils]
            [eponai.server.datomic-dev :as datomic-dev]
            [clj-http.cookies :as cookies]
            [clojure.core.async :as async]
            [datascript.core :as d]
            [taoensso.timbre :refer [info debug error]]))

(om/defui JvmRoot
  static om/IQuery
  (query [this] [:datascript/schema
                 {:query/current-user [:user/uuid :user/email]}
                 :user/current
                 {:query/all-projects [:project/uuid
                                       :project/created-at
                                       :project/users]}
                 {:query/transactions [:transaction/conversion
                                       :transaction/currency
                                       {:transaction/category [:category/name]}
                                       {:transaction/project [:db/id
                                                              :project/uuid
                                                              :project/created-at]}
                                       :transaction/title
                                       :transaction/uuid
                                       :transaction/amount
                                       {:transaction/tags [:tag/name]}
                                       {:transaction/date [:db/id :date/timestamp]}]}])
  Object
  (render [this]
    (dom/div nil
             (dom/p nil (str "Schema keys: " (keys (:datascript/schema (om/props this)))))
             (dom/p nil "First 10 transaction titles:")
             (apply dom/div nil (map #(dom/p nil (:transaction/title %))
                                     (:query/transactions (om/props this)))))))

(defmulti jvm-client-merge om/dispatch)

(defn create-client [idx endpoint-atom did-merge-fn teardown-atom]
  (let [conn (utils/create-conn)
        reconciler-atom (atom nil)
        cookie-store (cookies/cookie-store)
        send (backend/send! reconciler-atom
                            {:remote (-> (remotes/post-to-url nil)
                                         (remotes/wrap-update :url (fn [& _] @endpoint-atom))
                                         (remotes/read-basis-t-remote-middleware conn)
                                         (remotes/wrap-update :opts assoc :cookie-store cookie-store)
                                         (remotes/wrap-update :shutting-down? (fn [& _] @teardown-atom)))}
                            did-merge-fn)
        reconciler (om/reconciler {:id-key (str "jvmclient reconciler: " idx)
                                   :state   conn
                                   :parser  (parser/client-parser)
                                   :send    send
                                   :merge   (merge/merge! jvm-client-merge)
                                   :migrate nil
                                   :history 100})]
    (reset! reconciler-atom reconciler)
    (om/add-root! reconciler JvmRoot nil)
    reconciler))

(defn verify-email [client email-chan callback-chan]
  (let [{:keys [verification]} (fs.utils/take-with-timeout email-chan "email with verification")
        {:keys [:verification/uuid]} verification]
    (backend/drain-channel callback-chan)
    (om/transact! client `[(session.signin.email/verify ~{:verify-uuid (str uuid)})])
    (fs.utils/take-with-timeout callback-chan "email verification")))

(defn log-in-with-email [email]
  (fn [client email-chan callback-chan]
    (om/transact! client `[(session.signin/email ~{:input-email email
                                                     :device      :jvm})])
    (fs.utils/take-with-timeout callback-chan "email signin transact!")
    (verify-email client email-chan callback-chan)))

(defn log-in-with-facebook [user-id access-token email default-email]
  (fn [client email-chan callback-chan]
    (om/transact! client `[(session.signin/facebook ~{:user-id      user-id
                                                      :access-token access-token})
                           {:query/current-user [:db/id :user/uuid]}])
    (fs.utils/take-with-timeout callback-chan "facebook signin transact!")
    ;; If there's an email, we're done here.
    ;; Otherwise, we need to activate the user with an email.
    (when (nil? email)
      (let [db (d/db (om/app-state client))
           user-uuids (d/q '{:find [?uuid] :where [[_ :user/uuid ?uuid]]} db)
           user-uuid (do (assert (= 1 (count user-uuids)) (str "Had more than one user-uuids: " user-uuids))
                         (ffirst user-uuids))]
       (om/transact! client `[(session.signin/activate ~{:user-uuid  (str user-uuid)
                                                         :user-email default-email})])
       (fs.utils/take-with-timeout callback-chan "Facebook activation")
       ;; Activate sends an email and we need to verify it.
       (verify-email client email-chan callback-chan)))))

(defn log-in! [client log-in-fn]
  (let [{:keys [::set-logged-in! ::email-chan ::callback-chan]} (meta client)]
    (assert (fn? set-logged-in!))
    (assert (some? email-chan))
    (assert (some? callback-chan))
    (log-in-fn client email-chan callback-chan)
    (set-logged-in!)))


(defn log-out! [client]
  (let [{:keys [::set-logged-out! ::callback-chan]} (meta client)]
    (set-logged-out!)
    (om/transact! client `[(session/signout)])
    (fs.utils/take-with-timeout callback-chan "logging out")
    ))

(defn logged-in-client [idx server-url email-chan callback-chan teardown-atom]
  {:post [(some? (om/app-root %))]}
  (let [endpoint-atom (atom (str server-url "/api"))
        did-merge-fn (fn [client] (async/put! callback-chan client))
        client (create-client idx endpoint-atom did-merge-fn teardown-atom)
        client (with-meta client {::set-logged-in!  #(reset! endpoint-atom (str server-url "/api/user"))
                                  ::set-logged-out! #(reset! endpoint-atom (str server-url "/api"))
                                  ::email-chan      email-chan
                                  ::callback-chan   callback-chan})]
    (fs.utils/take-with-timeout callback-chan "initial merge")
    (log-in! client (log-in-with-email (throw (ex-info "Need a test-user email" {:was 'datomic-dev/test-user-email}))))
    (om/force-root-render! client)
    client))

(defn update-callback-chan [client callback-chan]
  (vary-meta client assoc ::callback-chan callback-chan))
