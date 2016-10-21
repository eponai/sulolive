(ns eponai.fullstack.tests
  (:require [om.next :as om]
            [om.util]
            [eponai.common.database.pull :as pull]
            [eponai.common.parser :as parser]
            [eponai.common.format.date :as date]
            [taoensso.timbre :refer [info debug error]]
            [clojure.data :as diff]
            [datascript.core :as datascript]
            [eponai.fullstack.framework :as fw]
            [eponai.fullstack.jvmclient :refer [JvmRoot]]
            [clojure.test :as test]
            [eponai.fullstack.utils :as fs.utils]
            [aprint.dispatch :as adispatch])
  (:import (org.eclipse.jetty.server Server)))

(defn- app-state [reconciler]
  (fw/call-parser reconciler (om/get-query (or (om/app-root reconciler) JvmRoot))))

(defn db [client]
  (pull/db* (om/app-state client)))

(defn entity [client lookup-ref]
  (pull/entity* (db client) lookup-ref))

(defn entity-map [client lookup-ref]
  (let [e (entity client lookup-ref)]
    (into {:db/id (:db/id e)} e)))

(defn equal-app-states? [clients]
  (let [app-states (map app-state clients)
        eq? (apply = app-states)]
    (if eq?
      eq?
      (run! (fn [[a b]]
              (when (not= a b)
                (let [[left right both] (vec (diff/diff a b))]
                  (error "App state NOT eq. diff: ")
                  (error "in left one: " left)
                  (error "in right one: " right)
                  (error "in both: " both))))
            (partition 2 1 app-states)))))

(defn new-transaction [client]
  (let [project-uuid (pull/find-with (pull/db* (om/app-state client))
                                     {:find-pattern '[?uuid .]
                                      :where        '[[_ :project/uuid ?uuid]]})]
    {:transaction/tags       [{:tag/name "thailand"}]
     :transaction/date       {:date/ymd "2015-10-10"}
     :transaction/type       :transaction.type/expense
     :transaction/currency   {:currency/code "THB"}
     :transaction/title      "lunch"
     :transaction/project    {:project/uuid project-uuid}
     :transaction/uuid       (datascript/squuid)
     :transaction/amount     "180"
     :transaction/created-at 1}))

(defn has-transaction? [tx client]
  (pull/lookup-entity (db client)
                      [:transaction/uuid (:transaction/uuid tx)]))

(defn has-edit? [tx edit-fn key-fn client]
  {:pre [(map? tx) (fn? edit-fn) (fn? key-fn) (om/reconciler? client)]}
  (= (key-fn (entity-map client [:transaction/uuid (:transaction/uuid tx)]))
     (key-fn (edit-fn tx))))

(defn is-running? [^Server server]
  (.isRunning server))

(defn stop-server! [server]
  {::fw/transaction #(do (.stop server)
                         (.join server))
   ::fw/asserts     #(assert (not (is-running? server)))})

(defn start-server! [server & {:keys [await asserts]}]
  {:pre [(vector? await) (fn? asserts)]}
  {::fw/transaction   #(.start server)
   ::fw/await-clients await
   ::fw/sync-clients! true
   ::fw/asserts       #(do (assert (is-running? server))
                           (asserts))})

(defn create-transaction! [server clients client tx]
  (fn []
    (let [asserts (if (is-running? server)
                    ;; Server is running before creation, assert everyone
                    ;; gets the transaction.
                    (fn []
                      (assert (every? (partial has-transaction? tx) clients))
                      (assert (equal-app-states? clients)))
                    ;; Server was not running. Only the client gets the transaction.
                    (fn []
                      (assert (test/is (has-transaction? tx client)))
                      (assert (test/is (not-any? (partial has-transaction? tx)
                                                 (remove #(= client %) clients))))))]
      {::fw/transaction (fn [] [client `[(transaction/create ~tx)]])
       ::fw/asserts     asserts})))

(defn transaction-edit [client tx edit-fn & [extra-params]]
  (fn []
    (let [tx (pull/pull (db client)
                        [:db/id :transaction/amount]
                        [:transaction/uuid (:transaction/uuid tx)])]
      (assert (some? (:db/id tx))
              (str "client: " client " did not have tx: " tx))
      [client `[(transaction/edit ~(merge {:old tx :new (edit-fn tx)}
                                          extra-params))]])))

(defn edit-transaction! [server clients client tx edit-fn key-fn]
  (fn []
    (let [asserts (if (is-running? server)
                    (fn []
                      (assert (every? (partial has-edit? tx edit-fn key-fn) clients)))
                    (fn []
                      (assert (has-edit? tx edit-fn key-fn client))
                      (assert (not-any? (partial has-edit? tx edit-fn key-fn)
                                        (remove #(= client %) clients)))))]
      {::fw/transaction (transaction-edit client tx edit-fn)
       ::fw/assert      asserts})))

(defmethod print-method om.next.Reconciler [x writer]
  (print-method (str "[Reconciler id=[" (get-in x [:config :id-key]) "]]") writer))

(defmethod adispatch/color-dispatch om.next.Reconciler [x]
  (adispatch/color-dispatch [(str "Reconciler" :id-key (get-in x [:config :id-key]))]))

(defn test-system-setup [server clients]
  {:label   "System setup should always have a running server."
   :actions [{::fw/transaction [(rand-nth clients) []]
              ::fw/asserts     #(do (assert (.isRunning ^Server server))
                                    (assert (equal-app-states? clients)))}]})

(defn test-create-transaction [_ [client1 :as clients]]
  (let [tx (new-transaction client1)]
    {:label   "created transactions should sync"
     :actions [{::fw/transaction [client1 `[(transaction/create ~tx)]]
                ::fw/asserts     (fn []
                                   (assert (every? (partial has-transaction? tx) clients))
                                   (assert (equal-app-states? clients)))}]}))

(defn test-create-transaction-offline [server [client1 :as clients]]
  (let [tx (new-transaction client1)]
    {:label   "Creating transaction offline should sync when client/server goes online"
     :actions [{::fw/transaction #(do (.stop server)
                                      (.join server))
                ::fw/asserts     #(assert (test/is (not (.isRunning server))))}
               {::fw/transaction [client1 `[(transaction/create ~tx)]]
                ::fw/asserts     #(do (assert (test/is (has-transaction? tx client1)))
                                      (assert (test/is (not-any? (partial has-transaction? tx)
                                                                 (rest clients)))))}
               {::fw/transaction   #(do (.start server))
                ::fw/await-clients [client1]
                ::fw/sync-clients! true
                ::fw/asserts       #(do
                                     (assert (.isRunning server))
                                     (assert (every? (partial has-transaction? tx) clients)))}]}))

(def inc-amount #(update % :transaction/amount (comp str inc bigdec)))
(def get-amount #(-> % :transaction/amount bigdec))

(defn test-edit-transaction [_ [client1 :as clients]]
  (let [tx (new-transaction client1)]
    {:label   "edit transaction: Last made edit should persist"
     :actions [{::fw/transaction [client1 `[(transaction/create ~tx)]]
                ::fw/asserts     #(assert (every? (partial has-transaction? tx) clients))}
               {::fw/transaction (transaction-edit client1 tx inc-amount)
                ::fw/asserts     (fn []
                                   (assert (every? (partial has-edit? tx inc-amount get-amount)
                                                   clients)))}]}))

(defn test-edit-transaction-offline [server [client1 client2 :as clients]]
  (let [tx (new-transaction client1)
        c1-edit inc-amount
        c2-edit (comp inc-amount inc-amount)
        days-from-now (comp date/date->long date/days-from-now)]
    {:label   "Last edit should persist"
     :actions [{::fw/transaction [client1 `[(transaction/create ~tx)]]
                ::fw/asserts     #(assert (every? (partial has-transaction? tx) clients))}
               {::fw/transaction #(do (.stop server)
                                      (.join server))
                ::fw/asserts     #(do (assert (test/is (not (.isRunning server))))
                                      (assert (every? (partial has-transaction? tx) clients)))}
               {::fw/transaction (transaction-edit client1 tx c1-edit
                                                   {::parser/created-at (days-from-now 2)})
                ::fw/asserts     #(do (assert (has-edit? tx c1-edit get-amount client1))
                                      (assert (not (has-edit? tx c1-edit get-amount client2))))}
               {::fw/transaction (transaction-edit client2 tx c2-edit
                                                   {::parser/created-at (days-from-now 3)})
                ::fw/asserts     #(do (assert (has-edit? tx c2-edit get-amount client2))
                                      (assert (not (has-edit? tx c2-edit get-amount client1))))}
               {::fw/transaction   #(.start server)
                ::fw/await-clients [client1 client2]
                ::fw/sync-clients! true
                ::fw/asserts       #(do (assert (not-any? (partial has-edit? tx c1-edit get-amount) clients))
                                        (assert (every? (partial has-edit? tx c2-edit get-amount) clients)))}]}))

(defn test-create+edit-transaction-offline [server [client1 :as clients]]
  (let [tx (new-transaction client1)]
    {:label   "Offline create and edit should persist"
     :actions [(stop-server! server)
               (create-transaction! server clients client1 tx)
               (edit-transaction! server clients client1 tx inc-amount get-amount)
               (start-server! server
                              :await [client1]
                              :asserts #(do (assert (every? (partial has-edit? tx inc-amount get-amount)
                                                            clients))))]}))

(defn new-project [client]
  (throw (ex-info "TODO" {})))

(defn test-edit-transaction-offline-to-new-offline-project
  [server [client1 :as clients]]
  (let [tx (new-transaction client1)
        proj (new-project client1)]
    {:label "Creation of project "}))

(defn run []
  (fs.utils/with-less-loud-logger
    #(do (fw/run-tests (->> [
                             test-system-setup
                             test-create-transaction
                             test-edit-transaction
                             test-create-transaction-offline
                             test-edit-transaction-offline
                             test-create+edit-transaction-offline ;;-> sync should see create+edit.
                             ;;test-edit-transaction-offline-to-new-offline-project
                            ]
                            ;; (filter (partial = test-edit-transaction))
                            ;;(reverse)
                            ;;(take 1)
                           ))
        nil)))

