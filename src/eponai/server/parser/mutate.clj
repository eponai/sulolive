(ns eponai.server.parser.mutate
  (:require
    [environ.core :as env]
    [eponai.common.database :as db]
    [eponai.server.external.mailchimp :as mailchimp]
    [eponai.common.parser :as parser :refer [server-mutate server-message]]
    [taoensso.timbre :refer [debug info]]
    [clojure.data.json :as json]
    [eponai.server.api :as api]
    [eponai.server.external.stripe :as stripe]
    [eponai.common :as c]))

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
             (let [user-eid (db/one-with (db/db state) {:where   '[[?e :user/email ?email]]
                                                        :symbols {'?email (:email auth)}})
                   photo (api/upload-user-photo (:photo params))]
               (db/transact state [photo [:db/add user-eid :user/photo (:db/id photo)]])))})


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

(defmutation stripe/create-product
  [{:keys [state ::parser/return ::parser/exception auth system]} _ {:keys [product store-id] :as p}]
  {:success "Your account was created"
   :error   "Could not create Stripe account"}
  {:action (fn []
             (debug "stripe/create-product with params: " p)
             (debug "Got photo: " (:photo product))
             (let [db-product {:db/id           (db/tempid :db.part/user)
                               :store.item/name (:name product)
                               :store.item/uuid (db/squuid)}
                   {:keys [stripe/secret]} (stripe/pull-stripe (db/db state) (c/parse-long store-id))
                   stripe-p (stripe/create-product (:system/stripe system)
                                                   secret
                                                   db-product)
                   photo-upload (api/upload-user-photo (:photo product))]
               (debug "Created product in stripe: " stripe-p)
               (debug "Uploaded item photo: " photo-upload)
               (db/transact state [db-product
                                   photo-upload
                                   [:db/add (c/parse-long store-id) :store/items (:db/id db-product)]
                                   [:db/add (:db/id db-product) :store.item/photos (:db/id photo-upload)]])))})

(defmutation stripe/update-product
  [{:keys [state ::parser/return ::parser/exception auth system]} _ {:keys [product store-id product-id]}]
  {:success "Your account was created"
   :error   "Could not create Stripe account"}
  {:action (fn []
             (let [{:keys [store.item/uuid store.item/photos]} (db/pull (db/db state) [:store.item/uuid :store.item/photos] (c/parse-long product-id))
                   {:keys [stripe/secret]} (stripe/pull-stripe (db/db state) (c/parse-long store-id))
                   stripe-p (stripe/update-product (:system/stripe system)
                                                   secret
                                                   uuid
                                                   product)
                   new-item {:store.item/uuid uuid
                             :store.item/name (:name product)}
                   txs (if (some? (:photo product))
                         (if (some? (first photos))
                           (let [updated-photo (assoc (first photos) :photo/path (get-in product [:photo :location]))]
                             [new-item updated-photo])
                           (let [new-photo {:db/id      (db/tempid :db.part/user)
                                            :photo/path (get-in product [:photo :location])}]
                             [new-photo
                              [:db/add [:store.item/uuid uuid] :store.item/photos (:db/id new-photo)]]))
                         [new-item])]
               (debug "Created product in stripe: " stripe-p)
               (debug "Transaction into datomic: " txs)
               (db/transact state txs)))})

(defmutation stripe/delete-product
  [{:keys [state ::parser/return ::parser/exception auth system]} _ {:keys [product]}]
  {:success "Product deleted"
   :error   "Could not delete product"}
  {:action (fn []
             (let [{:keys [store.item/uuid]} (db/pull (db/db state) [:store.item/uuid] (:db/id product))
                   store (db/pull-one-with (db/db state) [:db/id {:store/stripe '[*]}] {:where   '[[?e :store/items ?p]]
                                                                                        :symbols {'?p (:db/id product)}})
                   _ (debug "Found store: " store)
                   {:keys [stripe/secret]} (db/pull-one-with (db/db state) [:stripe/secret] {:where   '[[?s :store/items ?p]
                                                                                                        [?s :store/stripe ?e]]
                                                                                             :symbols {'?p (:db/id product)}})
                   _ (debug "Will delete product with UUID: " uuid " secret: " secret)
                   stripe-p (stripe/delete-product (:system/stripe system)
                                                   secret
                                                   (str uuid))]
               (debug "Deleted product in stripe: " stripe-p)
               (db/transact state [[:db.fn/retractEntity (:db/id product)]])))})