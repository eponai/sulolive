(ns eponai.fullstack.tests
  (:require [om.next :as om]
            [om.util]
            [eponai.common.database.pull :as pull]
            [eponai.common.parser :as parser]
            [eponai.common.format.date :as date]
            [eponai.common.format :as format]
            [eponai.common.database.transact :as transact]
            [taoensso.timbre :refer [info debug error]]
            [clojure.data :as diff]
            [datascript.core :as datascript]
            [eponai.fullstack.framework :as fw]
            [eponai.fullstack.jvmclient :refer [JvmRoot]]
            [clojure.test :as test]
            [eponai.fullstack.utils :as fs.utils]
            [clojure.walk :as walk]
            [aprint.dispatch :as adispatch]
            [datomic.api :as d])
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

(defn has-edit?
  ([tx {:keys [edit-fn key-fn compare-fn] :as edit} client]
   {:pre [(map? tx) (map? edit) (some? edit-fn) (some? key-fn) (om/reconciler? client)]}
   ((or compare-fn =)
     (key-fn (entity-map client [:transaction/uuid (:transaction/uuid tx)]))
     (key-fn (edit-fn tx)))))

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

(defn edit-transaction! [server clients client tx edit & [mutation-params]]
  {:pre [(om/reconciler? client)
         (map? tx)
         (and (map? edit) (some? (:edit-fn edit)))
         (or (nil? mutation-params) (map? mutation-params))]}
  (fn []
    (let [tx (entity-map client [:transaction/uuid (:transaction/uuid tx)])]
      {::fw/transaction [client `[(transaction/edit ~(merge {:old tx :new ((:edit-fn edit) tx)} mutation-params))]]
       ::fw/assert      (if (is-running? server)
                          #(assert (every? (partial has-edit? tx edit) clients))
                          #(do (assert (has-edit? tx edit client))
                               (assert (not-any? (partial has-edit? tx edit)
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
               (edit-transaction! server clients client1 tx {:edit-fn inc-amount :key-fn get-amount})]}))

(defn test-edit-transaction-offline [server [client1 client2 :as clients]]
  (let [tx (new-transaction client1)
        c1-edit {:key-fn get-amount :edit-fn inc-amount }
        c2-edit {:key-fn get-amount :edit-fn (comp inc-amount inc-amount)}
        days-from-now (comp date/date->long date/days-from-now)]
    {:label   "Last edit should persist"
     :actions [(create-transaction! server clients client1 tx)
               (stop-server! server :asserts #(assert (every? (partial has-transaction? tx) clients)))
               (edit-transaction! server clients client1 tx c1-edit {::parser/created-at (days-from-now 2)})
               (edit-transaction! server clients client2 tx c2-edit {::parser/created-at (days-from-now 3)})
               (start-server! server
                              :await [client1 client2]
                              :asserts
                              #(do (assert (not-any? (partial has-edit? tx c1-edit) clients))
                                   (assert (every? (partial has-edit? tx c2-edit) clients))))]}))

(defn- create+edit-offline-test [label & edit-params]
  {:pre [(some? label)]}
  (fn [server [client1 :as clients]]
    (let [tx (new-transaction client1)
          edit (zipmap [:edit-fn :key-fn :compare-fn] edit-params)]
      {:label   label
       :actions [(stop-server! server)
                 (create-transaction! server clients client1 tx)
                 (edit-transaction! server clients client1 tx edit)
                 (start-server! server
                                :await [client1]
                                :asserts #(do (assert (every? (partial has-edit? tx edit) clients))))]})))

(def test-create+edit-amount-offline
  (create+edit-offline-test "create+edit amount offline"
                            inc-amount
                            get-amount))

(def test-create+edit-title-offline
  (create+edit-offline-test "create+edit title offline"
                            #(assoc % :transaction/title "title")
                            :transaction/title
                            #(test/is (= "title" % %2))))

(def test-create+edit-category-offline
  (create+edit-offline-test "create+edit category offline"
                            #(assoc % :transaction/category {:category/name "category"})
                            (comp :category/name :transaction/category)
                            (partial = "category")))

(defn new-project []
  {:project/uuid (d/squuid)
   :project/name "fullstack test project"})

(defn has-project? [project client]
  (pull/lookup-entity (db client) [:project/uuid (:project/uuid project)]))

(defn test-edit-transaction-offline-to-new-offline-project
  [server [client1 :as clients]]
  (let [tx (new-transaction client1)
        proj (new-project)
        uuid (:project/uuid proj)
        edit {:edit-fn    #(assoc % :transaction/project {:project/uuid uuid})
              :key-fn     #(-> % :transaction/project :project/uuid)
              :compare-fn (partial = uuid)}]
    {:label   "Creation of project "
     :actions [(stop-server! server)
               (create-transaction! server clients client1 tx)
               {::fw/transaction [client1 `[(project/save ~proj)]]
                ::fw/asserts     #(do (assert (has-project? proj client1))
                                      (assert (not-any? (partial has-project? proj)
                                                        (remove (partial = client1) clients))))}
               (edit-transaction! server clients client1 tx edit)
               (start-server! server
                              :await [client1]
                              :asserts #(do (assert (every? (partial has-transaction? tx) clients))
                                            (assert (every? (partial has-project? proj) clients))
                                            (assert (every? (partial has-edit? tx edit) clients))))]}))

(defn run []
  (fs.utils/with-less-loud-logger
    #(do (fw/run-tests (->> [
                             test-system-setup
                             test-create-transaction
                             test-edit-transaction
                             test-create-transaction-offline
                             test-edit-transaction-offline
                             test-create+edit-amount-offline
                             test-create+edit-title-offline
                             test-create+edit-category-offline
                             test-edit-transaction-offline-to-new-offline-project
                             ;; test-edit-tags with retracts and adds on different clients.
                            ]
                            ;; (filter (partial = test-edit-transaction))
                            ;; (reverse)
                            ;; (take 1)
                           ))
        nil)))

