(ns eponai.server.parser.mutate
  (:require
    [clojure.core.async :as async]
    [eponai.common.database.transact :as transact]
    [eponai.common.format :as format]
    [eponai.common.parser :refer [mutate]]
    [taoensso.timbre :refer [debug]]
    [eponai.server.api :as api]))

;; ------------------- Transaction --------------------

(defmethod mutate 'transaction/create
  [{:keys [state mutation-uuid]} k params]
  (debug "transaction/create with params:" params)
  {:action (fn []
             (let [transaction (format/transaction-create k params)
                   currency-chan (async/chan 1)
                   tx-report (transact/mutate-one state mutation-uuid transaction)]
               (async/go (async/>! currency-chan (:transaction/date transaction)))
               (assoc tx-report :currency-chan currency-chan)))})

(defmethod mutate 'transaction/edit
  [{:keys [state mutation-uuid]} _ {:keys [transaction/uuid] :as transaction}]
  (debug "transaction/edit with params:" transaction)
  {:action (fn []
             {:pre [(some? uuid)]}
             (let [txs (format/transaction-edit transaction)]
               (debug "editing transaction: " uuid " txs: " txs)
               (transact/mutate state mutation-uuid txs)))})

;; ----------------- Budget --------------------

(defmethod mutate 'budget/save
  [{:keys [state auth mutation-uuid]} _ params]
  (debug "budget/save with params: " params)
  (let [user-ref [:user/uuid (:username auth)]
        budget (format/budget user-ref params)
        dashboard (format/dashboard (:db/id budget) params)]
    {:action (fn []
               (transact/mutate state mutation-uuid [budget dashboard]))}))

(defmethod mutate 'budget/share
  [{:keys [state]} _ {:keys [budget/uuid user/email] :as params}]
  (debug "budget/save with params: " params)
  {:action (fn []
             (api/share-budget state uuid email))})

;; --------------- Widget ----------------

(defmethod mutate 'widget/save
  [{:keys [state mutation-uuid]} _ params]
  (debug "widget/save with params: " params)
  (let [widget (format/widget-create params)]
    {:action (fn []
               (transact/mutate-map state mutation-uuid widget))}))

(defmethod mutate 'widget/delete
  [{:keys [state mutation-uuid]} _ params]
  (debug "widget/delete with params: " params)
  (let [widget-uuid (:widget/uuid params)]
    {:action (fn []
               (transact/mutate-one state mutation-uuid [:db.fn/retractEntity [:widget/uuid widget-uuid]]))
     :remote true}))

;; ---------------- Dashboard ----------------

(defmethod mutate 'dashboard/save
  [{:keys [state mutation-uuid]} _ {:keys [widget-layout] :as params}]
  (debug "dashboard/save with params: " params)
  {:action (fn []
             (transact/mutate state mutation-uuid (format/add-tempid widget-layout)))})

;; ------------------- User account related ------------------

(defmethod mutate 'settings/save
  [{:keys [state mutation-uuid]} _ {:keys [currency user] :as params}]
  (debug "settings/save with params: " params)
  {:action (fn []
             (transact/mutate-one state mutation-uuid [:db/add [:user/uuid (:user/uuid user)] :user/currency [:currency/code currency]]))})

(defmethod mutate 'signup/email
  [{:keys [state]} _ params]
  (debug "signup/email with params:" params)
  {:action (fn []
             (api/signin state (:input-email params)))})

(defmethod mutate 'stripe/charge
  [{:keys [state]} _ {:keys [token] :as p}]
  (debug "stripe/charge with params:" p)
  {:action (fn []
             (api/stripe-charge state token))})

(defmethod mutate 'stripe/cancel
  [{:keys [state auth]} _ p]
  (debug "stripe/cancel with params:" p)
  {:action (fn []
             (api/stripe-cancel state (:username auth)))})