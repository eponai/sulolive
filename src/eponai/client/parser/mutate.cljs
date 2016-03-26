(ns eponai.client.parser.mutate
  (:require [eponai.common.parser :refer [mutate]]
            [taoensso.timbre :refer-macros [info debug error trace]]
            [eponai.common.format :as format]
            [eponai.common.database.transact :as transact]))

;; ################ Remote mutations ####################
;; Remote mutations goes here. We share these mutations
;; with all client platforms (web, ios, android).
;; Local mutations should be defined in:
;;     eponai.<platform>.parser.mutate

;; --------------------- Transaction ------------

(defmethod mutate 'transaction/create
  [{:keys [state mutation-uuid]} k params]
  (debug "transaction/create with params:" params)
  {:action (fn []
             (let [transaction (format/transaction-create k params)]
               (transact/mutate-one state mutation-uuid transaction)))
   :remote true})

(defmethod mutate 'transaction/edit
  [{:keys [state mutation-uuid]} _ {:keys [transaction/uuid] :as transaction}]
  (debug "transaction/edit with params:" transaction)
  {:action (fn []
             {:pre [(some? uuid)]}
             (let [txs (format/transaction-edit transaction)]
               (debug "editing transaction: " uuid " txs: " txs)
               (transact/mutate state mutation-uuid txs)))
   :remote true})


;; ---------------- Budget --------------

(defmethod mutate 'budget/save
  [{:keys [state mutation-uuid]} _ params]
  (debug "budget/save with params: " params)
  (let [ budget (format/budget nil params)
        dashboard (format/dashboard (:db/id budget) params)]
    {:action (fn []
               (transact/mutate state mutation-uuid [budget dashboard]))
     :remote true}))

(defmethod mutate 'budget/share
  [{:keys [state mutation-uuid]} _ params]
  (debug "budget/share with params: " params)
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

(defmethod mutate 'stripe/charge
  [_ _ params]
  (debug "stripe/charge with params:" params)
  {:remote true})

(defmethod mutate 'stripe/cancel
  [_ _ p]
  (debug "stripe/cancel with params:" p)
  {:remote true})