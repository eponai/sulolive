(ns eponai.fullstack.tests
  (:require [om.next :as om]
            [om.util]
            [eponai.common.database.pull :as pull]
            [taoensso.timbre :refer [debug error]]
            [clojure.data :as diff]
            [clojure.pprint :as pp]
            [datascript.core :as datascript]
            [eponai.fullstack.framework :as fw]
            [eponai.fullstack.jvmclient :refer [JvmRoot]]
            [clojure.test :as test]
            [eponai.fullstack.utils :as fs.utils]
            )
  (:import (org.eclipse.jetty.server Server)))

(defn- app-state [reconciler]
  (fw/call-parser reconciler (om/get-query (or (om/app-root reconciler) JvmRoot))))

(defn entity [client lookup-ref]
  (-> (om/app-state client)
      (pull/db*)
      (pull/entity* lookup-ref)))

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
  (-> (entity client [:transaction/uuid (:transaction/uuid tx)])
      (keys)
      (seq)))

(defmethod print-method om.next.Reconciler [x writer]
  (print-method (str "[Reconciler id=[" (get-in x [:config :id-key]) "]]") writer))


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

(defn test-edit-transaction [_ [client1 :as clients]]
  (let [tx (new-transaction client1)
        inc-str (comp str inc read-string)
        has-edit? (fn [client]
                    (test/is
                      (= (str (:transaction/amount
                                (entity client [:transaction/uuid (:transaction/uuid tx)])))
                         (inc-str (:transaction/amount tx)))))]
    {:label   "edit transaction: Last made edit should persist"
     :actions [{::fw/transaction [client1 `[(transaction/create ~tx)]]}
               {::fw/transaction (fn []
                                   (let [db-id (:db/id (entity client1 [:transaction/uuid (:transaction/uuid tx)]))]
                                     [client1 `[(transaction/edit ~(-> tx
                                                                       (select-keys [:transaction/uuid :transaction/amount])
                                                                       (assoc :db/id db-id)
                                                                       (update :transaction/amount inc-str)))]]))
                ::fw/asserts     (fn []
                                   (assert (every? has-edit? clients)))}]}))

(defn test-create-transaction-offline [server [client1 :as clients]]
  (let [tx (new-transaction client1)]
    {:label   "Creating transaction offline should sync when client/server goes online"
     :actions [{::fw/transaction #(do (.stop server)
                                      (.join server))
                ::fw/asserts     #(assert (test/is (not (.isRunning server))))}
               {::fw/transaction [client1 `[(transaction/create ~tx)]]
                ::fw/asserts     #(do (assert (test/is (has-transaction? tx client1)))
                                      (assert (test/is (not-any? (partial has-transaction? tx) (rest clients)))))}]}))


(defn run []
  (fs.utils/with-less-loud-logger
    (do (fw/run-tests test-system-setup
                      test-create-transaction
                      test-edit-transaction
                      ;;test-create-transaction-offline
                      )
        nil)))

