(ns eponai.common.parser.mutate
 (:require [clojure.set :refer [rename-keys]]
           [eponai.common.format :as format]
           [eponai.common.validate :as validate]
           [eponai.common.transact :as transact]
   #?(:clj
           [eponai.server.api :as api])

   #?(:clj [clojure.core.async :refer [go >!]])
  #?(:clj [eponai.server.datomic.pull :as server.pull])
  #?(:clj  [datomic.api :as d]
     :cljs [datascript.core :as d])
  #?(:cljs [om.next :as om])))

(defmulti mutate (fn [_ k _] k))

;; -------- Badly scoped mutations. TODO:(FIX THIS)

#?(:cljs
   (defmethod mutate 'datascript/transact
    [{:keys [state]} _ {:keys [txs]}]
    {:action #(d/transact! state txs)}))

#?(:cljs
   (defn cas! [component id key old-value new-value]
    (om/transact! component
                  `[(datascript/transact
                     {:txs [[:db.fn/cas ~id ~key ~old-value ~new-value]]})])))

;; -------- Remote mutations

(defmethod mutate 'transaction/create
 [{:keys [state auth currency-chan]} k {:keys [input-tags] :as params}]
 (when-not (= (frequencies (set input-tags))
              (frequencies input-tags))
  (throw (ex-info "Illegal argument :input-tags. Each tag must be unique."
                  {:input-tags input-tags
                   :mutate k
                   :params params})))
 (let [renames {:input-title       :transaction/name
                :input-amount      :transaction/amount
                :input-description :transaction/details
                :input-date        :transaction/date
                :input-tags        :transaction/tags
                :input-currency    :transaction/currency
                :input-created-at  :transaction/created-at
                :input-uuid        :transaction/uuid}
       user-tx (rename-keys params renames)
       #?@(:clj [user-tx (assoc user-tx :transaction/budget
                                        (:budget/uuid (server.pull/budget (d/db state)
                                                                          (:username auth))))])]
   (validate/valid-user-transaction? user-tx)
   (let [db-tx (format/user-transaction->db-entity user-tx)]
     {#?@(:cljs [:remote true])
      :action (fn []
                (transact/transact state [db-tx])
                #?(:clj (go (>! currency-chan (:transaction/date user-tx))))
                ;; TODO: Figure out what to return from these mutations.
                nil)})))

(defmethod mutate 'email/verify
  [{:keys [state]} _ {:keys [uuid]}]
  (println "Verification verify: " uuid)
#?(:cljs {:remote true}
   :clj {:action (fn []
                    (api/verify state uuid)
                    uuid)}))