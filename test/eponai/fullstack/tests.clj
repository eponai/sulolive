(ns eponai.fullstack.tests
  (:require [om.next :as om]
            [om.util]
            [eponai.common.database.pull :as pull]
            [taoensso.timbre :refer [debug error]]
            [clojure.data :as diff]
            [datascript.core :as datascript]
            [eponai.fullstack.framework :as fw]
            [eponai.fullstack.jvmclient :refer [JvmRoot]])
  (:import (org.eclipse.jetty.server Server)))

(defn- app-state [reconciler]
  (fw/call-parser reconciler (om/get-query (or (om/app-root reconciler) JvmRoot))))

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


(defn test-system-setup [server clients]
  {:label   "System setup should always have a running server."
   :actions [{::fw/transaction [(rand-nth clients) []]
              ::fw/asserts     #(do (assert (.isRunning ^Server server))
                                    (assert (equal-app-states? clients)))}]})

(defn test-create-transaction [_ [client1 :as clients]]
  (let [project-uuid (pull/find-with (pull/db* (om/app-state client1))
                                     {:find-pattern '[?uuid .]
                                      :where        '[[_ :project/uuid ?uuid]]})
        tx {:transaction/tags       [{:tag/name "thailand"}]
            :transaction/date       {:date/ymd "2015-10-10"}
            :transaction/type       :transaction.type/expense
            :transaction/currency   {:currency/code "THB"}
            :transaction/title      "lunch"
            :transaction/project    {:project/uuid project-uuid}
            :transaction/uuid       (datascript/squuid)
            :transaction/amount     "180"
            :transaction/created-at 1}]
    {:label   "created transactions should sync"
     :actions [{::fw/transaction [client1 `[(transaction/create ~tx)]]
                ::fw/asserts     (fn []
                                   (letfn [(has-new-transaction? [client]
                                             (-> (om/app-state client)
                                                 (pull/db*)
                                                 (pull/entity* [:transaction/uuid (:transaction/uuid tx)])
                                                 (keys)
                                                 (seq)))]
                                     (assert (every? has-new-transaction? clients))
                                     (assert (equal-app-states? clients))))}]}))

(defn run []
  (fw/run-tests test-system-setup
                test-create-transaction))
