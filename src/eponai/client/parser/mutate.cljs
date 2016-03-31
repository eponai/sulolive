(ns eponai.client.parser.mutate
  (:require [eponai.common.parser :refer [mutate]]
            [taoensso.timbre :refer-macros [info debug error trace]]
            [datascript.core :as d]
            [eponai.common.format :as format]
            [eponai.common.database.transact :as transact]
            [eponai.common.validate :as validate]))

;; ################ Remote mutations ####################
;; Remote mutations goes here. We share these mutations
;; with all client platforms (web, ios, android).
;; Local mutations should be defined in:
;;     eponai.<platform>.parser.mutate

;; --------------------- Transaction ------------

(defmethod mutate 'transaction/create
  [{:keys [state mutation-uuid parser] :as e} k input-transaction]
  (let [user-uuid (-> (parser e '[{:query/current-user [:user/uuid]}])
                      (get-in [:query/current-user :user/uuid]))]
    (validate/validate e k {:transaction input-transaction :user-uuid user-uuid})
    {:action (fn []
               (let [transaction (format/transaction input-transaction)]
                 (transact/mutate-one state mutation-uuid transaction)))
     :remote true}))

(defmethod mutate 'transaction/edit
  [{:keys [state mutation-uuid]} _ {:keys [transaction/uuid] :as transaction}]
  (debug "transaction/edit with params:" transaction "mutation-uuid: " mutation-uuid)
  {:action (fn []
             {:pre [(some? uuid)]}
             (let [txs (format/transaction-edit transaction)]
               (debug "editing transaction: " uuid " txs: " txs)
               (transact/mutate state mutation-uuid txs)))
   :remote true})


;; ---------------- project --------------

(defmethod mutate 'project/save
  [{:keys [state mutation-uuid]} _ params]
  (debug "project/save with params: " params)
  (let [ project (format/project nil params)
        dashboard (format/dashboard (:db/id project) params)]
    {:action (fn []
               (transact/mutate state mutation-uuid [project dashboard]))
     :remote true}))

(defmethod mutate 'project/share
  [{:keys [state mutation-uuid]} _ params]
  (debug "project/share with params: " params)
  {:remote true})

;; -------------- Widget ---------------

(defmethod mutate 'widget/save
  [{:keys [state mutation-uuid]} _ params]
  (debug "widget/save with params: " params)
  (let [widget (format/widget-create params)]
    {:action (fn []
               (transact/mutate-map state mutation-uuid widget))
     :remote true}))

(defmethod mutate 'widget/delete
  [{:keys [state mutation-uuid]} _ params]
  (debug "widget/delete with params: " params)
  (let [widget-uuid (:widget/uuid params)]
    {:action (fn []
               (transact/mutate-one state mutation-uuid [:db.fn/retractEntity [:widget/uuid widget-uuid]]))
     :remote true}))

;; ---------------------- Dashboard -----------------------

(defmethod mutate 'dashboard/save
  [{:keys [state mutation-uuid]} _ {:keys [widget-layout] :as params}]
  (debug "dashboard/save with params: " params)
  {:action (fn []
             (when widget-layout
               (transact/mutate state mutation-uuid (format/add-tempid widget-layout))))
   :remote (some? widget-layout)})

;; ------------------- User account related ------------------

(defmethod mutate 'settings/save
  [{:keys [state mutation-uuid]} _ {:keys [currency user] :as params}]
  (debug "settings/save with params: " params)
  {:action (fn []
             (transact/mutate-one state mutation-uuid [:db/add [:user/uuid (:user/uuid user)] :user/currency [:currency/code currency]]))
   :remote true})

(defmethod mutate 'signup/email
  [_ _ params]
  (debug "signup/email with params:" params)
  {:remote true})

(defmethod mutate 'stripe/subscribe
  [_ _ params]
  (debug "stripe/charge with params:" params)
  {:remote true})

(defmethod mutate 'stripe/trial
  [_ _ params]
  (debug "stripe/trial with params:" params)
  {:remote true})

(defmethod mutate 'stripe/cancel
  [_ _ p]
  (debug "stripe/cancel with params:" p)
  {:remote true})