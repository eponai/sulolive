(ns eponai.client.utils
  (:require [devtools.core :as devtools]
            [eponai.client.logger :as logger]
            [goog.date]
            [cljs-time.coerce :as cljs-time]
            [datascript.db :as db]
            [datascript.core :as d]
            [taoensso.timbre :as timbre :refer-macros [debug]]))

(defprotocol IQueueMutations
  (mutations [this])
  (queue-mutation [this id mutation])
  (copy-queue [this other])
  (mutations-after [this id])
  (keep-mutations-after [this id])
  (clear-queue [this]))

(defn- mutation-queue-entity [db]
  {:post [(some? %)]}
  (d/entity db [:ui/component :ui.component/mutation-queue]))

(defn- update-queue [db update-fn]
  (d/db-with db (vector (-> (mutation-queue-entity db)
                            (update :ui.component.mutation-queue/queue update-fn)))))

(defn- mutation-queue [db]
  (:ui.component.mutation-queue/queue (mutation-queue-entity db)))

(defn queue-contains-id? [queue id]
  (and (some? id)
       (some #(= id (:id %)) queue)))

(defn drop-id-xf [id]
  (comp (drop-while #(not= id (:id %)))
        (drop-while #(= id (:id %)))))

(extend-protocol IQueueMutations
  db/DB
  (mutations [this] (mapv :mutation (mutation-queue this)))
  (copy-queue [this other]
    (reduce (fn [o {:keys [id mutation]}]
              (queue-mutation o id mutation))
            other
            (mutation-queue this)))
  (queue-mutation [this id mutation]
    (let [queue (mutation-queue this)]
      (assert (or (not (queue-contains-id? queue id))
                  (= id (:id (last queue))))
        (str "Queue was in an invalid state. Queue should either not contain"
             " the mutation id, or the last item in the queue should have the"
             " same mutation id. Queue: " queue " id: " id " mutation: " mutation)))
    (update-queue this (fnil (fn [q] (conj q {:id id :mutation mutation}))
                             [])))
  (keep-mutations-after [this id]
    (cond-> this
            (queue-contains-id? (mutation-queue this) id)
            (update-queue (fn [q] (into [] (drop-id-xf id) q)))))
  (clear-queue [this]
    (d/db-with this [[:db/retract [:ui/component :ui.component/mutation-queue]
                      :ui.component.mutation-queue/queue]]))
  (mutations-after [this id]
    (let [queue (mutation-queue this)]
      (cond->> queue
               (queue-contains-id? queue id)
               (into [] (comp (drop-id-xf id)
                              (map :mutation)))))))

;; ------ Dates -----------
;; Extends equality on goog.date's to speed up shouldComponentUpdate.

(defn date-eq [a b]
  (and (satisfies? cljs-time/ICoerce a)
       (satisfies? cljs-time/ICoerce b)
       (= (cljs-time/to-long a)
          (cljs-time/to-long b))))


(extend-protocol IEquiv
  goog.date.Date
  (-equiv [this other] (date-eq this other))

  goog.date.DateTime
  (-equiv [this other] (date-eq this other))

  goog.date.UtcDateTime
  (-equiv [this other] (date-eq this other)))

;; ------ Util functions -----------

(defn distinct-by
  "Distinct by (f input). See clojure.core/distinct."
  ([f]
   (fn [rf]
     (let [seen (volatile! #{})]
       (fn
         ([] (rf))
         ([result] (rf result))
         ([result input]
          (let [by (f input)]
            (if (contains? @seen by)
              result
              (do (vswap! seen conj by)
                  (rf result input)))))))))
  ([f coll]
   (sequence (distinct-by f) coll)))

;; ------ App initialization -----------

(defn set-level [l]
  (timbre/set-level! l))

(def set-trace #(set-level :trace))
(def set-debug #(set-level :debug))

(defn install-app []
  (enable-console-print!)
  (devtools/install!)
  (logger/install-logger!))
