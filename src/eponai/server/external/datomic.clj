(ns eponai.server.external.datomic
  (:require [com.stuartsierra.component :as component]
            [eponai.server.datomic-dev :as datomic-dev]
            [eponai.common.database :as db]
            [datomic.api :as datomic]
            [suspendable.core :as suspendable]
            [clojure.core.async :as async]
            [taoensso.timbre :refer [debug error]])
  (:import (java.util.concurrent BlockingQueue)))

(defprotocol IDatomic
  (add-tx-listener [this tx-listener on-error]
    "returns an id which can be used in remove listener.")
  (remove-tx-listener [this listener-id]
    "removes listener from datomic. listener-id was returned by add-tx-listener."))

(defrecord Datomic [db-url provided-conn add-mocked-data?]
  IDatomic
  (add-tx-listener [this tx-listener on-error]
    (when-let [listeners (:tx-listeners this)]
      (let [id (datomic/squuid)]
        (swap! listeners assoc id {:on-tx-report tx-listener
                                   :on-error     on-error})
        id)))
  (remove-tx-listener [this listener-id]
    (when-let [listeners (:tx-listeners this)]
      (swap! listeners dissoc listener-id)))

  db/ConnectionApi
  (db* [this]
    (db/db (:conn this)))

  component/Lifecycle
  (start [this]
    (if (:conn this)
      this
      (let [conn (or provided-conn
                     (datomic-dev/create-connection db-url
                                                    {::datomic-dev/add-data? add-mocked-data?}))
            tx-listeners (atom {})
            control-chan (async/chan)
            tx-report-queue (datomic/tx-report-queue conn)]
        (async/thread
          (try
            (loop []
              (let [tx-report (.take ^BlockingQueue tx-report-queue)]
                (when-not (= ::finished tx-report)
                  (->>
                    (deref tx-listeners)
                    (vals)
                    (run!
                      (fn [{:keys [on-tx-report on-error]}]
                        (try
                          (on-tx-report tx-report)
                          (catch Throwable e
                            (error "Error in DatomicChat thread reading tx-report-queue: " e)
                            (try
                              (on-error e)
                              (catch Throwable e
                                (error "on-error threw exception, really?: " e))))))))
                  (recur))))
            (catch InterruptedException e
              (error "Error in DatomicChat thread reading tx-report-queue: " e)))
          (debug "Exiting Datomic async/thread..")
          (async/close! control-chan))
        (assoc this :conn conn
                    :control-chan control-chan
                    :tx-listeners tx-listeners
                    :tx-report-queue tx-report-queue))))
  (stop [this]
    (when-let [conn (:conn this)]
      (datomic/remove-tx-report-queue conn)
      ;; Adding ::finished to will eventually close the control-chan
      (.add ^BlockingQueue (:tx-report-queue this) ::finished)
      (let [[v c] (async/alts!! [(:control-chan this) (async/timeout 1000)])]
        (if (= c (:control-chan this))
          (debug "Datomic report-queue successfully stopped.")
          (debug "Datomic report-queue timed out when stopping.")))
      (datomic/release conn))
    (dissoc this :conn :control-chan :tx-listeners :tx-report-queue))

  suspendable/Suspendable
  (suspend [this]
    this)
  (resume [this old-this]
    ;; Keep the database across code reloads
    (if-let [conn (:conn old-this)]
      (assoc this :conn conn
                  :control-chan (:control-chan old-this)
                  :tx-listeners (:tx-listeners old-this)
                  :tx-report-queue (:tx-report-queue old-this))
      (do (component/stop old-this)
          (component/start this)))))
