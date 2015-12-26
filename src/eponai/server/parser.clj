(ns eponai.server.parser
  (:refer-clojure :exclude [read])
  (:require [om.next.server :as om]
            [clojure.set :refer [rename-keys]]
            [clojure.core.async :refer [go >!]]
            [eponai.server.datomic.pull :as p]
            [eponai.server.datomic.transact :as t]
            [eponai.server.datomic.format :as f]
            [datomic.api :only [db q] :as d]))

(defn dispatch [_ k _]
  k)

(defmulti read dispatch)
(defmulti mutate dispatch)

(defmethod read :query/all-dates
  [{:keys [state query]} _ _]
  {:value (p/all state query '[[?e :date/ymd]])})

(defmethod read :query/all-currencies
  [{:keys [state query]} _ _]
  {:value (p/all state query '[[?e :currency/code]])})

(defmethod mutate 'transaction/create
  [{:keys [state auth currency-chan]} _ params]
  (let [renames {:input-title       :transaction/name
                 :input-amount      :transaction/amount
                 :input-description :transaction/details
                 :input-date        :transaction/date
                 :input-tags        :transaction/tags
                 :input-currency    :transaction/currency
                 :input-created-at  :transaction/created-at
                 :input-uuid        :transaction/uuid}
        user-tx (rename-keys params renames)
        budget (p/budget (d/db state) (:username auth))
        user-data (assoc user-tx :transaction/budget (:budget/uuid budget))]
    (go (>! currency-chan (:transaction/date user-data)))
    {:action (fn []
               (t/user-txs state [user-data])
               [user-data])}))

(defn parser [opts]
  (om/parser opts))