(ns eponai.server.parser.mutate
  (:require
    [clojure.core.async :as async]
    [eponai.common.database.transact :as transact]
    [eponai.common.format :as format]
    [eponai.common.parser :refer [mutate]]
    [eponai.common.validate :as validate]
    [taoensso.timbre :as timbre :refer [debug]]
    [eponai.server.api :as api]
    [eponai.common.database.pull :as p]
    [datomic.api :as d]
    [eponai.common.format :as common.format]
    [environ.core :refer [env]]
    [eponai.server.external.facebook :as fb]
    [eponai.server.auth.credentials :as a]))

;; ------------------- Transaction --------------------

(defmethod mutate 'transaction/create
  [{:keys [state mutation-uuid auth] :as env} k input-transaction]
  (debug "transaction/create with params:" input-transaction)
  {:action (fn []
             (validate/validate env k {:transaction input-transaction
                                       :user-uuid   (:username auth)})
             (let [transaction (common.format/transaction input-transaction)
                   currency-chan (async/chan 1)
                   tx-report (transact/mutate-one state mutation-uuid transaction)]
               (async/go (async/>! currency-chan (:transaction/date transaction)))
               (assoc tx-report :currency-chan currency-chan)))})

(defmethod mutate 'transaction/edit
  [{:keys [state mutation-uuid auth] :as env} k {:keys [transaction/uuid] :as transaction}]
  (debug "transaction/edit with params:" transaction)
  {:action (fn []
             (validate/validate env k {:transaction transaction
                                       :user-uuid (:username auth)})
             (debug "validated transaction")
             (let [txs (format/transaction-edit transaction)]
               (debug "editing transaction: " uuid " txs: " txs)
               (transact/mutate state mutation-uuid txs)))})

;; ----------------- project --------------------

(defmethod mutate 'project/save
  [{:keys [state auth mutation-uuid]} _ params]
  (debug "project/save with params: " params)
  {:action (fn []
             (let [user-ref [:user/uuid (:username auth)]
                   project (format/project user-ref params)
                   dashboard (format/dashboard (:db/id project) params)]
               (transact/mutate state mutation-uuid [project dashboard])))})

(defmethod mutate 'project/share
  [{:keys [state]} _ {:keys [project/uuid user/email] :as params}]
  (debug "project/save with params: " params)
  {:action (fn []
             (api/share-project state uuid email))})

;; --------------- Widget ----------------

(defmethod mutate 'widget/create
  [{:keys [state mutation-uuid auth] :as env} k params]
  (debug "widget/create with params: " params)
  {:action (fn []
             (validate/validate env k {:widget    params
                                       :user-uuid (:username auth)})
             (let [widget (format/widget-create params)]
               (transact/mutate-one state mutation-uuid widget)))})

(defmethod mutate 'widget/edit
  [{:keys [state mutation-uuid]} _ params]
  (debug "widget/edit with params: " params)
  {:action (fn []
             (let [widget (format/widget-edit params)]
               (transact/mutate state mutation-uuid widget)))
   :remote true})

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


(defmethod mutate 'stripe/subscribe
  [{:keys [state auth stripe-fn]} _ p]
  (debug "stripe/subscribe with params:" p)
  (let [db (d/db state)
        stripe-eid (p/one-with db {:where '[[?u :user/uuid ?useruuid]
                                            [?e :stripe/user ?u]]
                                   :symbols {'?user-uuid (:username auth)}})
        stripe-account (when stripe-eid
                         (p/pull db [:stripe/customer
                                     {:stripe/subscription [:stripe.subscription/id]}]
                                 stripe-eid))]
    {:action (fn []
               (api/stripe-subscribe state stripe-fn stripe-account p))}))

(defmethod mutate 'stripe/trial
  [{:keys [state auth stripe-fn]} _ _]
  (let [user (p/pull (d/db state) [:user/email :stripe/_user] [:user/uuid (:username auth)])]
    {:action (fn []
               (api/stripe-trial state stripe-fn user))}))

(defmethod mutate 'stripe/cancel
  [{:keys [state auth] :as env} _ p]
  (debug "stripe/cancel with params:" p)
  (let [db (d/db state)
        eid (p/one-with db {:where   '[[?u :user/uuid ?user-uuid]
                                       [?e :stripe/user ?u]]
                            :symbols {'?user-uuid (:username auth)}})
        stripe-account (when eid
                         (p/pull db [:stripe/customer
                                     {:stripe/subscription [:stripe.subscription/id]}] eid))]
    {:action (fn []
               (api/stripe-cancel env stripe-account))}))

;; ############# Session mutations #################

(defmethod mutate 'session.signin/email
  [{:keys [state]} k {:keys [device] :as params}]
  (debug "signup/email with params:" params)
  {:action (fn []
             ;; TODO: Need a more generic way of specifying required parameters for mutations.
             (when-not device
               (throw (ex-info (str "No device specified for " k
                                    ". Specify :device with either :web, :ios or whatever"
                                    " send email needs.")
                               {:mutation k :params params})))
             (-> (api/signin state (:input-email params))
                 (assoc :device device)))})

(defmethod mutate 'session.signin.email/verify
  [{:keys [auth]} _ {:keys [verify-uuid] :as p}]
  (debug "session.signin.email/verify with params: " p)
  {:action (fn []
             {:auth (some? auth)})})

(defmethod mutate 'session.signin/facebook
  [{:keys [auth]} _ {:keys [access-token user-id] :as p}]
  (debug "session.signin/facebook with params: " p)
  {:action (fn []
             {:auth (some? auth)})})

(defmethod mutate 'session/signout
  [_ _ p]
  (debug "session/signout with params: " p)
  {:action (fn [])})