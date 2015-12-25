(ns eponai.client.parser
  (:require [om.next :as om]
            [datascript.core :as d]
            [eponai.client.format :as f]))

(defmulti read om/dispatch)
(defmulti mutate om/dispatch)

(defn debug-read [{:keys [target] :as env} k params]
  (prn "Reading: " {:key k :target target})
  (let [ret (read env k params)]
    (prn {:read k :ret ret})
    ret))

(defmethod read :default
  [{:keys [parser query target] :as env} k _]
  (cond
    (= "proxy" (namespace k))
    (let [ret (parser env query target)]
      (if (and target (seq ret))
        {target (om/query->ast ret)}
        {:value ret}))
    :else (prn "WARN: Returning nil for read key: " k)))

(defn pull-all
  "takes the database, a pull query and where-clauses, where the where-clauses
  return some entity ?e."
  [state query where-clauses]
  (d/q (vec (concat '[:find [(pull ?e ?query) ...]
                         :in $ ?query
                         :where]
                       where-clauses))
          (d/db state)
       query))

(defmethod mutate 'datascript/transact
  [{:keys [state]} _ {:keys [txs]}]
  {:action #(d/transact! state txs)})

(defn cas! [component id key old-value new-value]
  (om/transact! component
                `[(datascript/transact
                    {:txs [[:db.fn/cas ~id ~key ~old-value ~new-value]]})]))

;--------- Readers for UI components


(defmethod read :query/header
  [{:keys [state query]} _ _]
  {:value (pull-all state query '[[?e :ui/singleton :budget/header]])})

; Remote readers

(defmethod read :query/all-dates
  [{:keys [state query]} _ _]
  {:value (pull-all state query '[[?e :date/ymd]])
   :remote true})

(defmethod read :query/all-currencies
  [{:keys [state query]} _ _]
  {:value (pull-all state query '[[?e :currency/code]])
   :remote true})

;------- Mutations

(defmethod mutate 'transaction/create
  [{:keys [state]} _ {:keys [input-amount input-currency input-title
                             input-date input-description input-tags
                             input-uuid input-created-at]}]
  (try                                                      ;TODO use some function or whatever to generate tempids.
    (let [date (assoc (f/input->date input-date) :db/id -1)
          curr (assoc (f/input->currency input-currency) :db/id -2)
          tags (map #(assoc %2 :db/id %1)
                    (range (- (- (count input-tags)) 2) -2)
                    (f/input->tags input-tags))
          transaction (-> (f/input->transaction input-amount
                                              input-title
                                              input-description
                                              input-uuid
                                              input-created-at)
                          (assoc :transaction/date -1)
                          (assoc :transaction/status :transaction.status/pending)
                          (assoc :transaction/currency -2)
                          (assoc :transaction/tags (mapv :db/id tags)))
          entities (concat tags [date curr transaction])]
      {:remote true
       :value  {:tempids [-1 -2]}
       :action (fn []
                 (try
                   (d/transact! state entities)
                   (catch :default e
                     (prn "error mutating transaction/create: " e))))})
    (catch :default e
      (prn e))))

