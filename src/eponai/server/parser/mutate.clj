(ns eponai.server.parser.mutate
  (:require
    [environ.core :as env]
    [eponai.common.database :as db]
    [eponai.server.external.mailchimp :as mailchimp]
    [eponai.common.parser :as parser :refer [server-mutate server-message]]
    [taoensso.timbre :refer [debug info]]
    [clojure.data.json :as json]
    [eponai.server.api :as api]
    [eponai.server.external.stripe :as stripe]))

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

;; TODO: Make this easier to use. Maybe return {::parser/force-read [:key1 :key2]} in the
;;       return value of the mutate?
(defn force-read-keys! [{:keys [::parser/force-read-without-history] :as env} k & ks]
  (apply swap! force-read-without-history conj k ks)
  nil)


(defmutation shopping-cart/add-item
  [{:keys [state auth]} _ {:keys [item]}]
  {}
  {:action (fn []
             (let [cart (db/one-with (db/db state) {:where '[[?e :cart/items]]})]
               (db/transact-one state [:db/add cart :cart/items (:db/id item)])))})

(defmutation beta/vendor
  [{:keys [state auth system] ::parser/keys [return exception]} _ {:keys [name site email]}]
  {:success "Cool! Check your inbox for a confirmation email"
   :error   (if exception (:detail (json/read-str (:body (ex-data exception)) :key-fn keyword) "") "")}
  {:action (fn []
             (let [ret (mailchimp/subscribe (:system/mailchimp system)
                                            {:email        email
                                             :list-id      (env/env :mail-chimp-list-beta-id)
                                             :merge-fields {"NAME" name
                                                            "SITE" site}})]
               ret))})

(defmutation photo/upload
  [{:keys [state ::parser/return ::parser/exception auth]} _ params]
  {:success "Photo uploaded"
   :error   "Could not upload photo :("}
  {:action (fn []
             (debug "Upload photo for auth: " auth)
             (let [user-entity (db/one-with (db/db state) {:where   '[[?e :user/email ?email]]
                                                           :symbols {'?email (:email auth)}})]
               (api/upload-user-photo state (:photo-info params) user-entity)))})


;######## STRIPE ########

(defmutation stripe/create-account
  [{:keys [state ::parser/return ::parser/exception auth system]} _ _]
  {:success "Your account was created"
   :error "Could not create Stripe account"}
  {:action (fn []
             (let [account (stripe/create-account (:system/stripe system)
                                                  {:country "CA"})
                   _ (debug "Stripe account created: " account)
                   store (db/one-with (db/db state) {:where   '[[?user :user/email ?auth]
                                                                [?owner :store.owner/user ?user]
                                                                [?e :store/owners ?owner]]
                                                     :symbols {'?auth (:email auth)}})]
               (db/transact state [[:db/add store :store/stripe (:id account)]])))})