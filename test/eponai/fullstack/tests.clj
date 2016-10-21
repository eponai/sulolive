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
            [clojure.walk :as walk]
            [aprint.dispatch :as adispatch])
  (:import (org.eclipse.jetty.server Server)
           (datomic.Entity)
           (datascript.impl.entity.Entity)))

(defn- app-state [reconciler]
  (fw/call-parser reconciler (om/get-query (or (om/app-root reconciler) JvmRoot))))

(defn db [client]
  (pull/db* (om/app-state client)))

(defn entity? [x]
  (or
    (instance? datascript.impl.entity.Entity x)
    (instance? datomic.Entity x)))

(defn entity-map
  [client lookup-ref]
  (->> (pull/entity* (db client) lookup-ref)
       (walk/prewalk #(cond->> %
                               (entity? %)
                               (into {:db/id (:db/id %)})))))

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

(defn stop-server! [server & {:keys [asserts]}]
  {:pre [(or (nil? asserts) (fn? asserts))]}
  {::fw/transaction #(do (.stop server)
                         (.join server))
   ::fw/asserts     #(do (assert (not (is-running? server)))
                         (when asserts (asserts)))})

(defn start-server! [server & {:keys [await asserts]}]
  {:pre [(vector? await) (fn? asserts)]}
  {::fw/transaction   #(.start server)
   ::fw/await-clients await
   ::fw/sync-clients! true
   ::fw/asserts       #(do (assert (is-running? server))
                           (asserts))})

(defn create-transaction! [server clients client tx]
  (fn []
    {::fw/transaction (fn [] [client `[(transaction/create ~tx)]])
     ::fw/asserts     (if (is-running? server)
                        ;; Server is running before creation, assert everyone
                        ;; gets the transaction.
                        (fn []
                          (assert (every? (partial has-transaction? tx) clients))
                          (assert (equal-app-states? clients)))
                        ;; Server was not running. Only the client gets the transaction.
                        (fn []
                          (assert (test/is (has-transaction? tx client)))
                          (assert (test/is (not-any? (partial has-transaction? tx)
                                                     (remove #(= client %) clients))))))}))

(defn edit-transaction! [server clients client tx edit-fn key-fn & [mutation-params]]
  {:pre [(om/reconciler? client)
         (map? tx)
         (fn? edit-fn)
         (fn? key-fn)
         (or (nil? mutation-params) (map? mutation-params))]}
  (fn []
    (let [tx (entity-map client [:transaction/uuid (:transaction/uuid tx)])]
      {::fw/transaction [client `[(transaction/edit ~(merge {:old tx :new (edit-fn tx)} mutation-params))]]
       ::fw/assert      (if (is-running? server)
                          #(assert (every? (partial has-edit? tx edit-fn key-fn) clients))
                          #(do (assert (has-edit? tx edit-fn key-fn client))
                               (assert (not-any? (partial has-edit? tx edit-fn key-fn)
                                                 (remove (partial = client) clients)))))})))

(defmethod print-method om.next.Reconciler [x writer]
  (print-method (str "[Reconciler id=[" (get-in x [:config :id-key]) "]]") writer))

(defmethod adispatch/color-dispatch om.next.Reconciler [x]
  (adispatch/color-dispatch [(str "Reconciler" :id-key (get-in x [:config :id-key]))]))

(def inc-amount #(update % :transaction/amount (comp str inc bigdec)))
(def get-amount #(-> % :transaction/amount bigdec))

(defn test-system-setup [server clients]
  {:label   "System setup should always have a running server."
   :actions [{::fw/transaction [(rand-nth clients) []]
              ::fw/asserts     #(do (assert (.isRunning ^Server server))
                                    (assert (equal-app-states? clients)))}]})

(defn test-create-transaction [server [client1 :as clients]]
  (let [tx (new-transaction client1)]
    {:label   "created transactions should sync"
     :actions [(create-transaction! server clients client1 tx)
               {::fw/transaction [client1 `[(transaction/create ~tx)]]
                ::fw/asserts     (fn []
                                   (assert (every? (partial has-transaction? tx) clients))
                                   (assert (equal-app-states? clients)))}]}))

(defn test-create-transaction-offline [server [client1 :as clients]]
  (let [tx (new-transaction client1)]
    {:label   "Creating transaction offline should sync when client/server goes online"
     :actions [(stop-server! server)
               (create-transaction! server clients client1 tx)
               (start-server! server :await [client1]
                              :asserts #(assert (every? (partial has-transaction? tx) clients)) )]}))

(defn test-edit-transaction [server [client1 :as clients]]
  (let [tx (new-transaction client1)]
    {:label   "edit transaction: Last made edit should persist"
     :actions [(create-transaction! server clients client1 tx)
               (edit-transaction! server clients client1 tx inc-amount get-amount)]}))

(defn test-edit-transaction-offline [server [client1 client2 :as clients]]
  (let [tx (new-transaction client1)
        c1-edit inc-amount
        c2-edit (comp inc-amount inc-amount)
        days-from-now (comp date/date->long date/days-from-now)]
    {:label   "Last edit should persist"
     :actions [(create-transaction! server clients client1 tx)
               (stop-server! server :asserts #(assert (every? (partial has-transaction? tx) clients)))
               (edit-transaction! server clients client1 tx c1-edit get-amount
                                  {::parser/created-at (days-from-now 2)})
               (edit-transaction! server clients client2 tx c2-edit get-amount
                                  {::parser/created-at (days-from-now 3)})
               (start-server! server
                              :await [client1 client2]
                              :asserts
                              #(do (assert (not-any? (partial has-edit? tx c1-edit get-amount) clients))
                                   (assert (every? (partial has-edit? tx c2-edit get-amount) clients))))]}))

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

