(ns eponai.common.parser.mutate
 (:require [clojure.set :refer [rename-keys]]
           [eponai.common.format :as format]
           [eponai.common.validate :as validate]
           [eponai.common.database.transact :as transact]
           [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [debug error info warn]]
   #?(:clj
           [eponai.server.api :as api])

   #?(:clj [clojure.core.async :refer [go >! chan]])
  #?(:clj [eponai.server.datomic.pull :as server.pull])
  #?(:cljs [datascript.core :as d])
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

(defn- transaction-create [{:keys [state db auth]} k {:keys [input-tags] :as params}]
  (fn []
    (when-not (= (frequencies (set input-tags))
                 (frequencies input-tags))
      (throw (ex-info "Illegal argument :input-tags. Each tag must be unique."
                      {:input-tags input-tags
                       :mutate     k
                       :params     params})))
    (let [renames {:input-title       :transaction/name
                   :input-amount      :transaction/amount
                   :input-description :transaction/details
                   :input-date        :transaction/date
                   :input-tags        :transaction/tags
                   :input-currency    :transaction/currency
                   :input-created-at  :transaction/created-at
                   :input-uuid        :transaction/uuid
                   :input-budget      :transaction/budget}
          user-tx (rename-keys params renames)
          #?@(:clj [currency-chan (chan 1)])
          _ (validate/valid-user-transaction? user-tx)
          db-tx (format/user-transaction->db-entity user-tx)]
      (transact/transact state [db-tx])
      #?(:clj (go (>! currency-chan (:transaction/date user-tx))))
      #?(:clj {:currency-chan currency-chan}
         :cljs nil))))

(defmethod mutate 'transaction/create
  [env k params]
  {:action (transaction-create env k params)
   #?@(:cljs [:remote true])})

(defmethod mutate 'email/verify
  [{:keys [state]} _ {:keys [uuid]}]
  (debug "email/verify for uuid:" uuid)
  #?(:cljs {:remote true}
     :clj  {:action (fn []
                      (api/verify-email state uuid)
                      uuid)}))

(defmethod mutate 'signup/email
  [{:keys [state]} _ params]
  (debug "signup/email with params:" params)
  #?(:cljs {:remote true}
     :clj  {:action (fn []
                      {:email-chan (api/signin state (:input-email params))})}))