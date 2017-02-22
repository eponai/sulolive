(ns eponai.server.parser.mutate
  (:require
    [environ.core :as env]
    [eponai.common.database :as db]
    [eponai.common.stream :as stream]
    [eponai.common.format :as format]
    [eponai.server.external.mailchimp :as mailchimp]
    [eponai.common.parser :as parser :refer [server-mutate server-message]]
    [taoensso.timbre :as timbre :refer [debug info]]
    [clojure.data.json :as json]
    [eponai.server.api :as api]
    [eponai.server.external.stripe :as stripe]
    [eponai.server.external.wowza :as wowza]
    [eponai.server.external.chat :as chat]
    [eponai.common :as c]
    [buddy.sign.jwt :as jwt]
    [eponai.server.api.store :as store]
    [eponai.server.external.aws-s3 :as s3]))

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


(defmutation shopping-bag/add-item
  [{:keys [state auth]} _ {:keys [item]}]
  {:success "Item added to bag"
   :error "Did not add that item, sorry"}
  {:action (fn []
             (let [cart (db/one-with (db/db state) {:where   '[[?u :user/email ?email]
                                                               [?u :user/cart ?e]]
                                                    :symbols {'?email (:email auth)}})]
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

(defn- query-user-id [{:keys [state auth]}]
  (db/one-with (db/db state) {:where   '[[?e :user/email ?email]]
                              :symbols {'?email (:email auth)}}))

(defmutation photo/upload
  [{:keys [state ::parser/return ::parser/exception system auth] :as env} _ params]
  {:success "Photo uploaded"
   :error   "Could not upload photo :("}
  {:action (fn []
             (debug "Upload photo for auth: " auth)
             ;; TODO: Cache the user-id query for each call to parser and auth, since more mutations
             ;; and reads will use the user-eid
             (let [user-eid (query-user-id env)
                   photo (s3/upload-photo (:system/aws-s3) (:photo params))]
               (db/transact state [photo [:db/add user-eid :user/photo (:db/id photo)]])))})

(defmutation stream-token/generate
  [{:keys [state db parser ::parser/return ::parser/exception auth system] :as env} k {:keys [store-id]}]
  ;; Responing with a map works!
  {:success return
   :error   "Error generating stream token"}
  {:action (fn []
             (if-let [store (db/one-with db {:where   '[[?e :store/owners ?owner]
                                                        [?owner :store.owner/user ?u]
                                                        [?u :user/email ?email]]
                                             :symbols {'?e     store-id
                                                       '?email (:email auth)}})]
               {:token (jwt/sign {:auth       (:email auth)
                                  :streamName (stream/stream-name (db/entity db store))}
                                 (wowza/jwt-secret (:system/wowza system)))}
               (throw (ex-info "Can only generate tokens for streams which you are owner for"
                               {:store-id store-id
                                :mutation k
                                :auth     auth}))))})

;######## STRIPE ########

(defmutation stripe/create-account
  [{:keys [state ::parser/return ::parser/exception auth system]} _ _]
  {:success "Your account was created"
   :error   "Could not create Stripe account"}
  {:action (fn []
             (let [{:keys [id secret publ] :as acc} (stripe/create-account (:system/stripe system)
                                                                           {:country "CA"})
                   _ (debug "Stripe account created: " acc)
                   store (db/one-with (db/db state) {:where   '[[?user :user/email ?auth]
                                                                [?owner :store.owner/user ?user]
                                                                [?e :store/owners ?owner]]
                                                     :symbols {'?auth (:email auth)}})
                   stripe-info {:db/id         (db/tempid :db.part/user)
                                :stripe/id     id
                                :stripe/secret secret
                                :stripe/publ   publ}]
               (db/transact state [stripe-info
                                   [:db/add store :store/stripe (:db/id stripe-info)]])))})

(defmutation store/create-product
  [env _ {:keys [product store-id] :as p}]
  {:success "Your account was created"
   :error   "Could not create Stripe account"}
  {:action (fn []
             (debug "store/create-product with params: " p)
             (store/create-product env (c/parse-long store-id) product))})

(defmutation store/update-product
  [env _ {:keys [product store-id product-id] :as p}]
  {:success "Your account was created"
   :error   "Could not create Stripe account"}
  {:action (fn []
             (debug "store/update-product with params: " p)
             (store/update-product env (c/parse-long store-id) (c/parse-long product-id) product))})

(defmutation store/delete-product
  [env _ {:keys [product]}]
  {:success "Product deleted"
   :error   "Could not delete product"}
  {:action (fn []
             (store/delete-product env (:db/id product)))})

(defmutation store/create-order
  [env _ {:keys [order store-id] :as p}]
  {:success "Order created"
   :error   "Could not create order"}
  {:action (fn []
             (store/create-order env (c/parse-long store-id) order))})

(defmutation chat/send-message
  [{::parser/keys [exception] :keys [state system] :as env} k {:keys [store text user]}]
  {:success "Message sent"
   :error   (if (some? exception)
              (.getMessage exception)
              "Someting went wrong!")}
  {:action (fn []
             (let [user-id (query-user-id env)]
               (if (= (:db/id user) user-id)
                 (chat/write-message (:system/chat system) store user text)
                 (throw (ex-info "User authed does not match user who sent message."
                                 {:client-user-id (:db/id user)
                                  :server-user-id user-id
                                  :mutation       k})))))})

(defmutation store/update-order
  [env _ {:keys [order-id store-id params] :as p}]
  {:success "Order created"
   :error   "Could not create order"}
  {:action (fn []
             (store/update-order env (c/parse-long store-id) order-id params))})
