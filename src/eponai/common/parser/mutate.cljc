(ns eponai.common.parser.mutate
  (:require [clojure.set :refer [rename-keys]]
            [eponai.common.format :as format]
            [eponai.common.validate :as validate]
            [eponai.common.database.transact :as transact]
            [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [debug error info warn]]
    #?(:clj
            [eponai.server.api :as api])
    #?(:clj
            [clojure.core.async :refer [go >! chan]])
    #?(:cljs [datascript.core :as d]
       :clj  [datomic.api :as d])
    #?(:cljs [om.next :as om])
            [eponai.common.format :as f]))

(defmulti mutate (fn [_ k _] k))

;; -------- Remote mutations

(defmethod mutate 'transaction/edit
  [{:keys [state mutation-uuid]} _ {:keys [transaction/tags transaction/uuid] :as transaction}]
  {:action (fn []
             {:pre [(some? uuid)]}
             (let [tag->txs (fn [{:keys [removed tag/name] :as tag}]
                              {:pre [(some? name)]}
                              (if removed
                                [[:db/retract [:transaction/uuid uuid] :transaction/tags [:tag/name name]]]
                                (let [tempid (d/tempid :db.part/user)]
                                  ;; Create new tag and add it to the transaction
                                  [(-> tag (dissoc :removed) (assoc :db/id tempid))
                                   [:db/add [:transaction/uuid uuid] :transaction/tags tempid]])))
                   transaction (-> transaction
                                   (dissoc :transaction/tags)
                                   (assoc :db/id (d/tempid :db.part/user))
                                   (->> (reduce-kv (fn [m k v]
                                                     (assoc m k (condp = k
                                                                  :transaction/amount #?(:cljs v :clj (bigint v))
                                                                  :transaction/currency (assoc v :db/id (d/tempid :db.part/user))
                                                                  :transaction/type (assoc v :db/id (d/tempid :db.part/user))
                                                                  :transaction/date (format/date (:date/ymd v))
                                                                  v)))
                                                   {})))
                   txs (cond-> [transaction]
                               (seq tags)
                               (into (mapcat tag->txs) tags))]
               (debug "editing transaction: " uuid " txs: " txs)
               (transact/mutate state mutation-uuid txs)))
   :remote true})

(defmethod mutate 'budget/create
  [{:keys [state auth]} _ {:keys [input-uuid input-budget-name]}]
  (debug "budget/create for uuid:" input-uuid)
  #?(:cljs {:remote true}
     :clj  {:action #(transact/transact-one state
                                            (f/budget [:user/uuid (:username auth)]
                                                      {:budget/uuid input-uuid
                                                       :budget/name input-budget-name}))}))

(defmethod mutate 'signup/email
  [{:keys [state]} _ params]
  (debug "signup/email with params:" params)
  #?(:cljs {:remote true}
     :clj  {:action #(api/signin state (:input-email params))}))

(defmethod mutate 'stripe/charge
  [{:keys [state]} _ {:keys [token]}]
  #?(:cljs {:remote true}
     :clj  {:action #(api/stripe-charge state token)}))

(defmethod mutate 'stripe/cancel
  [{:keys [state auth]} _ _]
  #?(:cljs {:remote true}
     :clj  {:action (fn []
                      (api/stripe-cancel state (:username auth)))}))

(defmethod mutate 'widget/save
  [{:keys [state mutation-uuid]} _ params]
  (debug "widget/save with params: " params)
  (let [widget (format/widget-map params)]
    {:action #(transact/mutate-map state mutation-uuid widget)
     #?@(:cljs [:remote true])}))

(defmethod mutate 'widget/delete
  [{:keys [state mutation-uuid]} _ params]
  (debug "widget/delete with params: " params)
  (let [widget-uuid (:widget/uuid params)]
    {:action (fn []
               (transact/mutate-one state mutation-uuid [:db.fn/retractEntity [:widget/uuid widget-uuid]]))
     #?@(:cljs [:remote true])}))

(defmethod mutate 'budget/save
  [{:keys [state auth mutation-uuid]} _ params]
  (debug "budget/save with params: " params)
  (let [user-ref (when (:username auth)
                   [:user/uuid (:username auth)])
        budget (format/budget user-ref params)
        dashboard (format/dashboard (:db/id budget) params)]
    {:action (fn []
               (transact/mutate state mutation-uuid [budget dashboard]))
     #?@(:cljs [:remote true])}))

(defmethod mutate 'settings/save
  [{:keys [state mutation-uuid]} _ {:keys [currency user] :as params}]
  (debug "settings/save with params: " params)
  {:action (fn []
             (transact/mutate-one state mutation-uuid [:db/add [:user/uuid (:user/uuid user)] :user/currency [:currency/code currency]]))
   #?@(:cljs [:remote true])})

(defmethod mutate 'dashboard/save
  [{:keys [state mutation-uuid]} _ {:keys [widget-layout] :as params}]
  (debug "dashboard/save with params: " params)
  {:action (fn []
             (transact/mutate state mutation-uuid (format/widget-layout widget-layout)))
   #?@(:cljs [:remote true])})
