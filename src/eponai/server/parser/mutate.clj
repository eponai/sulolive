(ns eponai.server.parser.mutate
  (:require
    [clojure.core.async :as async]
    [eponai.common.database.transact :as transact]
    [eponai.common.format :as format]
    [eponai.common.parser :as parser :refer [server-mutate server-message]]
    [eponai.common.validate :as validate]
    [taoensso.timbre :refer [debug info]]
    [eponai.server.api :as api]
    [eponai.common.database.pull :as p]
    [datomic.api :as d]
    [eponai.common.format :as common.format]
    [environ.core :refer [env]]))

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
  [{:keys [state auth tx-meta] :as env} k {:keys [transaction/title] :as input-transaction}]
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
  [{:keys [state auth] :as env} k {:keys [old new] :as p}]
  (let [title (when-let [return (::parser/return env)]
                (-> return
                    :db-after
                    (p/entity* (:db/id old))
                    :transaction/title))]
    {:success (str "Edited transaction " title)
     :error   (str "Error editing transaction ")})

  {:action (fn []
             (validate/edit env k p)
             (debug "validated transaction")
             (let [txs (format/edit env k p format/transaction)
                   _ (debug "editing transaction: " (:db/id old) " txs: " txs)
                   ret (transact/transact state txs)]
               ret))})

(defmutation transaction/delete
  [{:keys [state] :as env} k {:keys [transaction] :as p}]
  {:success (str "Deleted transaction " (:db/id transaction))
   :error   (str "Error deleting transaction " (:db/id transaction))}
  {:action (fn []
             (when-let [transaction-id (:db/id transaction)]
               (transact/transact-one state [:db.fn/retractEntity transaction-id])))})

;; ----------------- project --------------------

(defmutation project/save
  [{:keys [state auth]} _ params]
  ["Project saved" "Unable to save project"]
  {:action (fn []
             (debug "project/save with params: " params)
             (let [user-ref [:user/uuid (:username auth)]]
               (transact/transact-one state (format/project user-ref params))))})

(defmutation project/share
  [{:keys [state]} _ {:keys [project/uuid user/email] :as params}]
  ["Shared project" "Unable to share project"]
  {:action (fn []
             (debug "project/save with params: " params)
             (api/share-project state uuid email))})

(defmutation project/delete
  [{:keys [state]} _ {:keys [project-dbid] :as params}]
  ["Deleted project" "Unable to delete project"]
  {:action (fn []
             (debug "project/delete with params: " params)
             (api/delete-project state project-dbid))})

(defmutation project.category/save
  [{:keys [state]} _ {:keys [category project]}]
  [(str "Created new category '" (:category/name category) "'.")
   (str "Couldn't create category '" (:category/name category) "'.")]
  {:action (fn []
             (let [c (format/category* category)]
               (transact/transact state [c
                                         [:db/add (:db/id project) :project/categories (:db/id c)]])))})

;; ------------------- User account related ------------------

(defn force-read-keys! [{:keys [::parser/force-read-without-history] :as env} k & ks]
  (apply swap! force-read-without-history conj k ks)
  nil)

(defmutation settings/save
  [{:keys [state auth stripe-fn] :as env} _ {:keys [currency user paywhatyouwant] :as params}]
  ["Saved settings" "Error saving settings"]
  {:action (fn []
             (let [db (d/db state)
                   stripe-eid (p/one-with db {:where   '[[?u :user/uuid ?user-uuid]
                                                         [?e :stripe/user ?u]]
                                              :symbols {'?user-uuid (:username auth)}})
                   stripe-account (when stripe-eid
                                    (p/pull db [:stripe/customer
                                                {:stripe/subscription [:stripe.subscription/id]}]
                                            stripe-eid))]
               (debug "settings/save with params: " params)
               (when (number? paywhatyouwant)
                 (api/stripe-update-subscription state stripe-fn stripe-account {:quantity paywhatyouwant})
                 (force-read-keys! env :query/stripe)))
             (when currency
               (transact/transact-one state [:db/add [:user/uuid (:user/uuid user)] :user/currency [:currency/code currency]])))})

(defmutation stripe/update-card
  [{:keys [state auth stripe-fn] :as env} _ p]
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
               (force-read-keys! env :query/stripe))}))

(defmutation stripe/delete-card
  [{:keys [state auth stripe-fn] :as env} _ p]
  ["Card deleted" "Could not delete card"]
  {:action (fn []
             (let [db (d/db state)
                   _ (debug "stripe/delete-card with params:" p)
                   stripe-eid (p/one-with db {:where   '[[?u :user/uuid ?user-uuid]
                                                         [?e :stripe/user ?u]]
                                              :symbols {'?user-uuid (:username auth)}})
                   stripe-account (when stripe-eid
                                    (p/pull db [:stripe/customer
                                                {:stripe/subscription [:stripe.subscription/id]}]
                                            stripe-eid))]
               (debug "Stripe information: " stripe-account)
               (when stripe-eid
                 (debug "User: " (p/pull (d/db state) [:user/email :stripe/_user] [:user/uuid (:username auth)])))
               (api/stripe-delete-card state stripe-fn stripe-account p)
               (force-read-keys! env :query/stripe)))})

(defmutation stripe/trial
  [{:keys [state auth stripe-fn]} _ _]
  ["Started trial" "Error starting trial"]
  (let [user (p/pull (d/db state) [:user/email :stripe/_user] [:user/uuid (:username auth)])]
    {:action (fn []
               (api/stripe-trial state stripe-fn user)
               (force-read-keys! env :query/stripe))}))

;; ############# Session mutations #################

(defmutation session.connect/facebook
  [{:keys [state fb-validate-fn auth ::parser/force-read-without-history]} _ {:keys [access-token user-id] :as p}]
  ["Connected to Facebook" "Error connecting to facebook"]
  {:action (fn []
             (debug "session.connect/facebook with params: " p)
             (api/facebook-connect state (assoc p :user-uuid (:username auth)) fb-validate-fn)
             (swap! force-read-without-history conj :query/fb-user)
             nil)})

(defmutation session.disconnect/facebook
  [{:keys [state auth]} _ _]
  ["Disconnected from Facebook" "Error disconnecting from facebook"]
  {:action (fn []
             (debug "session.disconnect/facebook")
             (api/facebook-disconnect state {:user-uuid (:username auth)}))})

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
