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
            [taoensso.timbre :refer [debug error]]))

(om/defui JvmRoot
  static om/IQuery
  (query [this] [:datascript/schema
                 {:query/current-user [:user/uuid]}
                 :user/current
                 {:query/all-projects [:project/uuid
                                       :project/created-at
                                       :project/users]}
                 {:query/transactions [:transaction/conversion
                                       :transaction/currency
                                       {:transaction/category [:category/name]}
                                       :transaction/project
                                       :transaction/title
                                       :transaction/uuid
                                       :transaction/amount
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
                                         (remotes/wrap-update :opts assoc
                                                              :cookie-store cookie-store)
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

(defn- log-in! [client email-chan callback-chan]
  (om/transact! client `[(session.signin/email ~{:input-email datomic-dev/test-user-email
                                                 :device      :jvm})])
  (fs.utils/take-with-timeout callback-chan "email transact!")
  (let [{:keys [verification]} (fs.utils/take-with-timeout email-chan "email with verification")
        {:keys [:verification/uuid]} verification]
    (backend/drain-channel callback-chan)
    (om/transact! client `[(session.signin.email/verify ~{:verify-uuid (str uuid)})])
    (fs.utils/take-with-timeout callback-chan "verification")))

(defn logged-in-client [idx server-url email-chan callback-chan teardown-atom]
  {:post [(some? (om/app-root %))]}
  (let [endpoint-atom (atom (str server-url "/api"))
        did-merge-fn (fn [client] (async/put! callback-chan client))
        client (create-client idx endpoint-atom did-merge-fn teardown-atom)]
    (fs.utils/take-with-timeout callback-chan "initial merge")
    (log-in! client email-chan callback-chan)
    (reset! endpoint-atom (str server-url "/api/user"))
    (om/force-root-render! client)
    client))
