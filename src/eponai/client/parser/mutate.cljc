(ns eponai.client.parser.mutate
  (:require [eponai.common.parser :refer [client-mutate]]
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

(defmethod client-mutate 'transaction/create
  [{:keys [state parser] :as e} k input-transaction]
  (let [user-uuid (-> (parser e '[{:query/current-user [:user/uuid]}])
                      (get-in [:query/current-user :user/uuid]))]
    (validate/validate e k {:transaction input-transaction :user-uuid user-uuid})
    (debug "Transaction//create: " input-transaction)
    {:action (fn []
               (let [transaction (format/transaction input-transaction)]
                 (transact/transact-one state transaction)))
     :remote true}))

(defmethod client-mutate 'transaction/edit
  [{:keys [state] :as env} k {:keys [old new] :as p}]
  {:action (fn []
             (validate/edit env k p)
             (let [txs (format/edit env k p format/transaction)
                   ;;_ (assert (vector? txs))
                   ;; txs (into txs (datascript/mark-entity-txs id :transaction/uuid uuid))
                   _ (debug "editing transaction: " (:db/id old) " txs: " txs)
                   ret (transact/transact state txs)]
               ret))
   :remote true})


;; ---------------- project --------------

(defmethod client-mutate 'project/save
  [{:keys [state]} _ params]
  (debug "project/save with params: " params)
  (let [ project (format/project nil params)
        dashboard (format/dashboard (:db/id project) params)]
    {:action (fn []
               (transact/transact state [project dashboard]))
     :remote true}))

(defmethod client-mutate 'project/share
  [{:keys [state]} _ params]
  (debug "project/share with params: " params)
  {:remote true})

(defmethod client-mutate 'project/delete
  [{:keys [state]} _ {:keys [project-dbid] :as params}]
  (debug "project/delete with params: " project-dbid)
  {:action (fn []
             (transact/transact-one state [:db.fn/retractEntity project-dbid]))
   :remote true})
;; -------------- Widget ---------------

;(defmethod client-mutate 'widget/create
;  [{:keys [state parser] :as e} _ params]
;  (debug "widget/save with params: " params)
;  {:action (fn []
;             (let [widget (format/widget-create params)]
;               (transact/transact-one state widget)))
;   :remote true})
;
;(defmethod client-mutate 'widget/edit
;  [{:keys [state]} _ params]
;  (debug "widget/edit with params: " params)
;  (let [widget (format/widget-edit params)]
;    {:action (fn []
;               (transact/transact state widget))
;     :remote true}))
;
;(defmethod client-mutate 'widget/delete
;  [{:keys [state]} _ params]
;  (debug "widget/delete with params: " params)
;  (let [widget-uuid (:widget/uuid params)]
;    {:action (fn []
;               (transact/transact-one state [:db.fn/retractEntity [:widget/uuid widget-uuid]]))
;     :remote true}))

;; ---------------------- Dashboard -----------------------

;(defmethod client-mutate 'dashboard/save
;  [{:keys [state]} _ {:keys [widget-layout] :as params}]
;  (debug "dashboard/save with params: " params)
;  {:action (fn []
;             (when widget-layout
;               (transact/transact state (format/add-tempid widget-layout))))
;   :remote (some? widget-layout)})

;; ------------------- User account related ------------------

(defmethod client-mutate 'settings/save
  [{:keys [state]} _ {:keys [currency user] :as params}]
  (debug "settings/save with params: " params)
  {:action (fn []
             (transact/transact-one state [:db/add [:user/uuid (:user/uuid user)] :user/currency [:currency/code currency]]))
   :remote true})

;(defmethod client-mutate 'stripe/subscribe
;  [_ _ params]
;  (debug "stripe/charge with params:" params)
;  {:remote true})

;(defmethod client-mutate 'stripe/cancel
;  [{:keys [state]} _ params]
;  (debug "stripe/cancel with params:" params)
;  {:action (fn []
;             (transact/transact-one state {:ui/singleton                :ui.singleton/loader
;                                                       :ui.singleton.loader/visible true}))
;   :remote true})



(defmethod client-mutate 'stripe/trial
  [_ _ p]
  (debug "stripe/trial with params:" p)
  {:remote true})

(defmethod client-mutate 'stripe/update-card
  [_ _ p]
  (debug "stripe/update-card with params: " p)
  {:remote true})

(defmethod client-mutate 'stripe/delete-card
  [_ _ p]
  (debug "stripe/delete-card with params: " p)
  {:remote true})


;; ############# Session mutations #################

(defmethod client-mutate 'session.signin/email
  [_ _ params]
  (debug "session.signin/email with params:" params)
  {:remote true})

(defmethod client-mutate 'session.signin/facebook
  [_ _ p]
  (debug "session.signin/facebook with params:" p)
  {:remote true})

(defmethod client-mutate 'session.signin/activate
  [{:keys [auth]} _ {:keys [user-uuid user-email] :as p}]
  (assert (and user-uuid user-email) (str "Mutation 'session.signin/activate needs params user-uuid and user-email. Got params: " p))
  (debug "session.signin/activate with params: " p)
  {:remote true})

(defmethod client-mutate 'session.signin.email/verify
  [_ _ {:keys [verify-uuid] :as p}]
  (assert (some? verify-uuid) (str "Mutation 'session.signin.email/verify needs a value for key :verification-uuid. Got params: " p))
  (debug "session.signin.email/verify with params: " p)
  {:remote true})
