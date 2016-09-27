(ns eponai.client.parser.mutate
  (:require [eponai.common.parser :refer [mutate]]
            [eponai.common.datascript :as datascript]
            [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [info debug error trace warn]]
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
  [{:keys [state parser] :as e} k input-transaction]
  (let [user-uuid (-> (parser e '[{:query/current-user [:user/uuid]}])
                      (get-in [:query/current-user :user/uuid]))]
    (validate/validate e k {:transaction input-transaction :user-uuid user-uuid})
    {:action (fn []
               (let [transaction (format/transaction input-transaction)]
                 (transact/transact-one state transaction)))
     :remote true}))

(defmethod mutate 'transaction/edit
  [{:keys [state]} _ {:keys [transaction/uuid db/id] :as transaction}]
  (debug "transaction/edit with params:" transaction)
  {:action (fn []
             {:pre [(some? uuid) (some? id)]}
             (let [txs (format/transaction-edit transaction)
                   _ (assert (vector? txs))
                   txs (into txs (datascript/mark-entity-txs id :transaction/uuid uuid))
                   _ (debug "editing transaction: " uuid " txs: " txs)
                   ret (transact/transact state txs)]
               (debug "Edit transaction tx-report: " ret)
               ret))
   :remote true})


;; ---------------- project --------------

(defmethod mutate 'project/save
  [{:keys [state]} _ params]
  (debug "project/save with params: " params)
  (let [ project (format/project nil params)
        dashboard (format/dashboard (:db/id project) params)]
    {:action (fn []
               (transact/transact state [project dashboard]))
     :remote true}))

(defmethod mutate 'project/share
  [{:keys [state]} _ params]
  (debug "project/share with params: " params)
  {:remote true})

;; -------------- Widget ---------------

(defmethod mutate 'widget/create
  [{:keys [state parser] :as e} _ params]
  (debug "widget/save with params: " params)
  {:action (fn []
             (let [widget (format/widget-create params)]
               (transact/transact-one state widget)))
   :remote true})

(defmethod mutate 'widget/edit
  [{:keys [state]} _ params]
  (debug "widget/edit with params: " params)
  (let [widget (format/widget-edit params)]
    {:action (fn []
               (transact/transact state widget))
     :remote true}))

(defmethod mutate 'widget/delete
  [{:keys [state]} _ params]
  (debug "widget/delete with params: " params)
  (let [widget-uuid (:widget/uuid params)]
    {:action (fn []
               (transact/transact-one state [:db.fn/retractEntity [:widget/uuid widget-uuid]]))
     :remote true}))

;; ---------------------- Dashboard -----------------------

(defmethod mutate 'dashboard/save
  [{:keys [state]} _ {:keys [widget-layout] :as params}]
  (debug "dashboard/save with params: " params)
  {:action (fn []
             (when widget-layout
               (transact/transact state (format/add-tempid widget-layout))))
   :remote (some? widget-layout)})

;; ------------------- User account related ------------------

(defmethod mutate 'settings/save
  [{:keys [state]} _ {:keys [currency user] :as params}]
  (debug "settings/save with params: " params)
  {:action (fn []
             (transact/transact-one state [:db/add [:user/uuid (:user/uuid user)] :user/currency [:currency/code currency]]))
   :remote true})

(defmethod mutate 'stripe/subscribe
  [_ _ params]
  (debug "stripe/charge with params:" params)
  {:remote true})

(defmethod mutate 'stripe/cancel
  [{:keys [state]} _ params]
  (debug "stripe/cancel with params:" params)
  {:action (fn []
             (transact/transact-one state {:ui/singleton                :ui.singleton/loader
                                                       :ui.singleton.loader/visible true}))
   :remote true})



(defmethod mutate 'stripe/trial
  [_ _ p]
  (debug "stripe/trial with params:" p)
  {:remote true})


;; ############# Session mutations #################

(defmethod mutate 'session.signin/email
  [_ _ params]
  (debug "session.signin/email with params:" params)
  {:remote true})

(defmethod mutate 'session.signin/facebook
  [_ _ p]
  (debug "session.signin/facebook with params:" p)
  {:remote true})

(defmethod mutate 'session.signin/activate
  [{:keys [auth]} _ {:keys [user-uuid user-email] :as p}]
  (assert (and user-uuid user-email) (str "Mutation 'session.signin/activate needs params user-uuid and user-email. Got params: " p))
  (debug "session.signin/activate with params: " p)
  {:remote true})