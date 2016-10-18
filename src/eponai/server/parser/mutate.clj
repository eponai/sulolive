(ns eponai.server.parser.mutate
  (:require
    [clojure.core.async :as async]
    [eponai.common.database.transact :as transact]
    [eponai.common.format :as format]
    [eponai.common.parser :as parser :refer [server-mutate server-message]]
    [eponai.common.validate :as validate]
    [taoensso.timbre :as timbre :refer [debug]]
    [eponai.server.api :as api]
    [eponai.common.database.pull :as p]
    [datomic.api :as d]
    [eponai.common.format :as common.format]
    [environ.core :refer [env]]
    [eponai.server.external.facebook :as fb]
    [eponai.server.auth.credentials :as a]))

(defmacro defmutation
  "Creates a message and mutate defmethod at the same time.
  The body takes two maps. The first body is the message and the
  other is the mutate.
  The :return and :exception key in env is only available in the
  message body."
  [sym args message-body mutate-body]
  `(do
     (defmethod server-message (quote ~sym) ~args ~message-body)
     (defmethod server-mutate (quote ~sym) ~args ~mutate-body)))

;; ------------------- Transaction --------------------

(defmutation transaction/create
  [{:keys [state auth] :as env} k {:keys [transaction/title] :as input-transaction}]
  {:success (str "Created transaction " title)
   :error   (str "Error creating transaction " title)}
  {:action (fn []
             (debug "transaction/create with params:" input-transaction)
             (validate/validate env k {:transaction input-transaction
                                       :user-uuid   (:username auth)})
             (let [transaction (common.format/transaction input-transaction)
                   currency-chan (async/chan 1)
                   tx-report (transact/transact-one state transaction)]
               (async/go (async/>! currency-chan (:transaction/date transaction)))
               (assoc tx-report :currency-chan currency-chan)))})

(defmutation transaction/edit
  [{:keys [state auth] :as env} k {:keys [transaction/uuid transaction/title] :as transaction}]
  {:success (str "Edited transaction " title)
   :error   (str "Error editing transaction " title)}
  {:action (fn []
             (validate/validate env k {:transaction transaction
                                       :user-uuid (:username auth)})
             (debug "validated transaction")
             (let [txs (format/transaction-edit transaction)]
               (debug "editing transaction: " uuid " txs: " txs)
               (transact/transact state txs)))})

;; ----------------- project --------------------

(defmutation project/save
  [{:keys [state auth]} _ params]
  ["Project saved" "Unable to save project"]
  {:action (fn []
             (debug "project/save with params: " params)
             (let [user-ref [:user/uuid (:username auth)]
                   project (format/project user-ref params)
                   dashboard (format/dashboard (:db/id project) params)]
               (transact/transact state [project dashboard])))})

(defmutation project/share
  [{:keys [state]} _ {:keys [project/uuid user/email] :as params}]
  ["Shared project" "Unable to share project"]
  {:action (fn []
             (debug "project/save with params: " params)
             (api/share-project state uuid email))})

;; --------------- Widget ----------------

;(defmutation widget/create
;  [{:keys [state auth] :as env} k params]
;  ["Created widget" "Error creating widget"]
;  {:action (fn []
;             (debug "widget/create with params: " params)
;             (validate/validate env k {:widget    params
;                                       :user-uuid (:username auth)})
;             (let [widget (format/widget-create params)]
;               (transact/transact-one state widget)))})
;
;(defmutation widget/edit
;  [{:keys [state]} _ params]
;  ["Edited widget" "Error editing widget"]
;  {:action (fn []
;             (debug "widget/edit with params: " params)
;             (let [widget (format/widget-edit params)]
;               (transact/transact state widget)))
;   :remote true})
;
;(defmutation widget/delete
;  [{:keys [state]} _ {:keys [widget/uuid] :as params}]
;  ["Deleted widget" "Error deleting widget"]
;  {:action (fn []
;             (debug "widget/delete with params: " params)
;             (transact/transact-one state [:db.fn/retractEntity [:widget/uuid uuid]]))
;   :remote true})

;; ---------------- Dashboard ----------------

;(defmutation dashboard/save
;  [{:keys [state]} _ {:keys [widget-layout] :as params}]
;  ["Saved dashboard" "Error saving dashboard"]
;  {:action (fn []
;             (debug "dashboard/save with params: " params)
;             (transact/transact state (format/add-tempid widget-layout)))})

;; ------------------- User account related ------------------

(defmutation settings/save
  [{:keys [state]} _ {:keys [currency user] :as params}]
  ["Saved settings" "Error saving settings"]
  {:action (fn []
             (debug "settings/save with params: " params)
             (transact/transact-one state [:db/add [:user/uuid (:user/uuid user)] :user/currency [:currency/code currency]]))})


(defmutation stripe/subscribe
  [{:keys [state auth stripe-fn]} _ p]
  ["Subscribed!" "Error subscribing"]
  (let [db (d/db state)
        _ (debug "stripe/subscribe with params:" p)
        stripe-eid (p/one-with db {:where '[[?u :user/uuid ?user-uuid]
                                            [?e :stripe/user ?u]]
                                   :symbols {'?user-uuid (:username auth)}})
        stripe-account (when stripe-eid
                         (p/pull db [:stripe/customer
                                     {:stripe/subscription [:stripe.subscription/id]}]
                                 stripe-eid))]
    {:action (fn []
               (debug "Stripe information: " stripe-account)
               (when stripe-eid
                 (debug "User: " (p/pull (d/db state) [:user/email :stripe/_user] [:user/uuid (:username auth)])))
               (api/stripe-subscribe state stripe-fn stripe-account p))}))

(defmutation stripe/update-card
  [{:keys [state auth stripe-fn]} _ p]
  ["Card details updated!" "Could not update card details"]
  (let [db (d/db state)
        _ (debug "stripe/card-update with params:" p)
        stripe-eid (p/one-with db {:where '[[?u :user/uuid ?user-uuid]
                                            [?e :stripe/user ?u]]
                                   :symbols {'?user-uuid (:username auth)}})
        stripe-account (when stripe-eid
                         (p/pull db [:stripe/customer
                                     {:stripe/subscription [:stripe.subscription/id]}]
                                 stripe-eid))]
    {:action (fn []
               (debug "Stripe information: " stripe-account)
               (when stripe-eid
                 (debug "User: " (p/pull (d/db state) [:user/email :stripe/_user] [:user/uuid (:username auth)])))
               (api/stripe-update-card state stripe-fn stripe-account p)
               ;(api/stripe-update-card state stripe-fn stripe-account p)
               )}))

(defmutation stripe/trial
  [{:keys [state auth stripe-fn]} _ _]
  ["Started trial" "Error starting trial"]
  (let [user (p/pull (d/db state) [:user/email :stripe/_user] [:user/uuid (:username auth)])]
    {:action (fn []
               (api/stripe-trial state stripe-fn user))}))

(defmutation stripe/cancel
  [{:keys [state auth] :as env} _ p]
  ["Subscription cancelled" "Error cancelling subscription"]
  (let [db (d/db state)
        _ (debug "stripe/cancel with params:" p)
        eid (p/one-with db {:where   '[[?u :user/uuid ?user-uuid]
                                       [?e :stripe/user ?u]]
                            :symbols {'?user-uuid (:username auth)}})
        stripe-account (when eid
                         (p/pull db [:stripe/customer
                                     {:stripe/subscription [:stripe.subscription/id]}] eid))]
    {:action (fn []
               (api/stripe-cancel env stripe-account))}))

;; ############# Session mutations #################

(defmutation session.signin/email
  [{:keys [state]} k {:keys [device] :as params}]
  ["Signed in via email" "Error signing in via email"]
  {:action (fn []
             (debug "signup/email with params:" params)
             ;; TODO: Need a more generic way of specifying required parameters for mutations.
             ;; TODO: clojure.spec?
             (when-not device
               (throw (ex-info (str "No device specified for " k
                                    ". Specify :device with either :web, :ios or whatever"
                                    " send email needs.")
                               {:mutation k :params params})))
             (-> (api/signin state (:input-email params))
                 (assoc :device device)))})

(defmutation session.signin.email/verify
  [{:keys [auth]} _ {:keys [verify-uuid] :as p}]
  ["Verified signin via email" "Error verifying signin via email"]
  {:action (fn []
             (debug "session.signin.email/verify with params: " p)
             {:auth (some? auth)})})

(defmutation session.signin/facebook
  [{:keys [auth]} _ {:keys [access-token user-id] :as p}]
  ["Signed in via facebook" "Error signing in via facebook"]
  {:action (fn []
             (debug "session.signin/facebook with params: " p)
             {:auth (some? auth)})})

(defmutation session.signin/activate
  [{:keys [auth]} _ {:keys [user-uuid user-email] :as p}]
  ["Activate successful" "Error activating"]
  {:action (fn []
             (debug "session.signin/activate with params: " p)
             {:auth (some? auth)})})

(defmutation session/signout
  [{:keys [auth]} _ p]
  ["Signout complete" "Error signing out"]
  {:action (fn []
             (debug "session/signout with params: " p)
             {:auth (some? auth)})})
