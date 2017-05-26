(ns eponai.server.parser.mutate
  (:require
    [environ.core :as env]
    [eponai.common.database :as db]
    [eponai.common.stream :as stream]
    [eponai.common.format :as format]
    [eponai.server.external.mailchimp :as mailchimp]
    [eponai.common.parser :as parser]
    [taoensso.timbre :as timbre :refer [debug info]]
    [clojure.data.json :as json]
    [eponai.server.api :as api]
    [eponai.server.external.stripe :as stripe]
    [eponai.server.external.wowza :as wowza]
    [eponai.server.external.chat :as chat]
    [eponai.common :as c]
    [buddy.sign.jwt :as jwt]
    [eponai.server.api.store :as store]
    [eponai.server.external.aws-s3 :as s3]
    [eponai.common.format :as f]
    [eponai.common.ui.om-quill :as quill]
    [eponai.common.auth :as auth]
    [eponai.server.external.cloudinary :as cloudinary]))

(defmacro defmutation
  "Creates a message and mutate defmethod at the same time.
  The body takes two maps. The first body is the message and the
  other is the mutate.
  The :return and :exception key in env is only available in the
  message body."
  [sym args auth-and-message mutate-body]
  (assert (and (map? auth-and-message) (= #{:auth :resp} (set (keys auth-and-message))))
          (str "defmutation's auth and message body needs to be a map with :auth and :resp."
               " was: " auth-and-message))
  `(let [mutation# (quote ~sym)]
     (defmethod parser/server-message mutation# ~args ~(:resp auth-and-message))
     (defmethod parser/server-auth-role mutation# ~args ~(:auth auth-and-message))
     (defmethod parser/server-mutate mutation# ~args ~mutate-body)))

;; TODO: Make this easier to use. Maybe return {::parser/force-read [:key1 :key2]} in the
;;       return value of the mutate?
(defn force-read-keys! [{:keys [::parser/force-read-without-history] :as env} k & ks]
  (apply swap! force-read-without-history conj k ks)
  nil)

(defn- query-user-id [{:keys [state auth]}]
  (db/one-with (db/db state) {:where   '[[?e :user/email ?email]]
                              :symbols {'?email (:email auth)}}))

(defmutation shopping-bag/add-item
  [{:keys [state auth] :as env} _ {:keys [sku]}]
  {:auth ::auth/any-user
   :resp {:success "Item added to bag"
          :error   "Did not add that item, sorry"}}
  {:action (fn []
             (let [{:keys [user-id]} auth
                   cart (db/one-with (db/db state) {:where   '[[?u :user/cart ?e]]
                                                    :symbols {'?u user-id}})]
               (if (some? cart)
                 (db/transact-one state [:db/add cart :user.cart/items (c/parse-long sku)])
                 (let [new-cart {:db/id           (db/tempid :db.part/user)
                                 :user.cart/items [(c/parse-long sku)]}]
                   (db/transact state [new-cart
                                       [:db/add user-id :user/cart (:db/id new-cart)]])))))})

(defmutation shopping-bag/remove-item
  [{:keys [state auth] :as env} _ {:keys [sku]}]
  {:auth ::auth/any-user
   :resp {:success "Item added to bag"
          :error   "Did not add that item, sorry"}}
  {:action (fn []
             (let [{:keys [user-id]} auth
                   cart (db/one-with (db/db state) {:where   '[[?u :user/cart ?e]]
                                                    :symbols {'?u user-id}})]
               (when (some? cart)
                 (db/transact-one state [:db/retract cart :user.cart/items (c/parse-long sku)]))))})

(defmutation beta/vendor
  [{:keys [state auth system] ::parser/keys [return exception]} _ {:keys [name site email]}]
  {:auth ::auth/public
   :resp {:success "Thank you! Check your inbox for a confirmation email"
          :error   (if exception (:detail (json/read-str (:body (ex-data exception)) :key-fn keyword) "") "")}}
  {:action (fn []
             (let [ret (mailchimp/subscribe (:system/mailchimp system)
                                            {:email        email
                                             :list-id      (env/env :mailchimp-vendor-beta-id)
                                             :merge-fields {"NAME" name
                                                            "SITE" site}})]
               ret))})

(defmutation beta/customer
  [{:keys [state auth system] ::parser/keys [return exception]} _ {:keys [name site email]}]
  {:auth ::auth/public
   :resp {:success "Thank you! You'll hear from us soon. Check your inbox for a confirmation email."
          :error   (if exception (:detail (json/read-str (:body (ex-data exception)) :key-fn keyword) "") "")}}
  {:action (fn []
             (debug "beta/customer with email " email)
             (let [ret (mailchimp/subscribe (:system/mailchimp system)
                                            {:email   email
                                             :list-id (env/env :mailchimp-customer-beta-id)})]
               ret))})

(defmutation photo/upload
  [{:keys [state ::parser/return ::parser/exception system auth] :as env} _ params]
  {:auth ::auth/any-user
   :resp {:success "Photo uploaded"
          :error   "Could not upload photo :("}}
  {:action (fn []
             (let [old-profile (db/one-with (db/db state) {:where   '[[?u :user/profile ?e]]
                                                           :symbols {'?u (:user-id auth)}})
                   photo (f/add-tempid (cloudinary/upload-dynamic-photo (:system/cloudinary system) (:photo params)))
                   photo-txs [photo]
                   profile-txs (if (some? old-profile)
                                 (conj photo-txs [:db/add old-profile :user.profile/photo (:db/id photo)])
                                 (let [new-profile (f/add-tempid {:user.profile/photo (:db/id photo)})]
                                   (into photo-txs [new-profile
                                                    [:db/add (:user-id auth) :user/profile (:db/id new-profile)]])))]

               (debug "UPloaded photo: " photo)
               (db/transact state profile-txs)))})

(defmutation user.info/update
  [{:keys [state ::parser/return ::parser/exception system auth] :as env} _ {:keys [:user/name]}]
  {:auth ::auth/any-user
   :resp {:success "Photo uploaded"
          :error   "Could not upload photo :("}}
  {:action (fn []
             (let [old-profile (db/one-with (db/db state) {:where   '[[?u :user/profile ?e]]
                                                           :symbols {'?u (:user-id auth)}})
                   profile-txs (if (some? old-profile)
                                 [[:db/add old-profile :user.profile/name name]]
                                 (let [new-profile (f/add-tempid {:user.profile/name name})]
                                   [new-profile
                                    [:db/add (:user-id auth) :user/profile (:db/id new-profile)]]))]
               (db/transact state profile-txs)))})

(defmutation store.photo/upload
  [{:keys [state ::parser/return ::parser/exception system auth] :as env} _ {:keys [photo photo-key store-id] :as p}]
  {:auth {::auth/store-owner store-id}
   :resp {:success "Photo uploaded"
          :error   "Could not upload photo :("}}
  {:action (fn []
             (debug "Photo upload Store with params: " p)
             (when (keyword? photo-key)
               (let [{old-profile :store/profile} (db/pull (db/db state) [{:store/profile [:db/id {photo-key [:db/id :photo/id]}]}] store-id)
                     old-photo (get old-profile photo-key)]
                 (if (some? photo)
                   (when-not (= (:photo/id old-photo)
                                (cloudinary/real-photo-id (:system/cloudinary system) (:public_id photo)))
                     (let [cl-photo (cloudinary/upload-dynamic-photo (:system/cloudinary system) photo)
                           new-photo (cond-> (f/add-tempid cl-photo)
                                             (some? (:db/id old-photo))
                                             (assoc :db/id (:db/id old-photo)))
                           dbtxs (cond-> [new-photo]
                                         (nil? old-photo)
                                         (conj [:db/add (:db/id old-profile) photo-key (:db/id new-photo)]))]
                       (db/transact state dbtxs)))
                   (when (some? (:db/id old-photo))
                     (db/transact state [:db.fn/retractEntity (:db/id old-photo)]))))))})

(defmutation stream-token/generate
  [{:keys [state db parser ::parser/return auth system] :as env} k {:keys [store-id] :as p}]
  {:auth {::auth/store-owner store-id}
   :resp {:success return
          :error   "Error generating stream token"}}
  {:action (fn []
             (let [token (jwt/sign {:uuid              (str (db/squuid))
                                    :user-id           (:user-id auth)
                                    :store-id          store-id
                                    :wowza/stream-name (stream/stream-id (db/entity db store-id))}
                                   (wowza/jwt-secret (:system/wowza system)))]
               (db/transact state [{:stream/store store-id
                                    :stream/token token}])
               {:token token}))})

(defn- wowza-token-store-owner [{:keys [system state]} token]
  (let [{:keys [user-id store-id]} (jwt/unsign token (wowza/jwt-secret (:system/wowza system)))]
    (db/find-with (db/db state)
                  (->> (auth/auth-role-query ::auth/store-owner nil {:store-id store-id})
                       (db/merge-query {:find    '[?store .]
                                        :where   '[[?stream :stream/store ?store]
                                                   [?stream :stream/token ?token]]
                                        :symbols {'?store store-id
                                                  '?token token
                                                  '?user  user-id}})))))

(defn- quiet-cas [conn cas-tx]
  (try
    (db/transact conn cas-tx)
    (catch Exception e
      ;; We don't care if this fails.
      (debug "Ignoring cas exception : " (.getMessage e))
      nil)))

(defn- ensure-stream-is-atleast-online [conn store-id]
  (quiet-cas conn [[:db.fn/cas [:stream/store store-id] :stream/state :stream.state/offline :stream.state/online]]))

(defmutation stream/go-online
  [{:keys [state] :as env} k {:keys [stream/token]}]
  ;; We do some custom authing with the stream token.
  {:auth ::auth/public
   :resp {:success "Stream went online"
          :error   "Could not go online"}}
  {:action (fn []
             (if-let [store-id (wowza-token-store-owner env token)]
               (do (debug "Was store owner of store-id: " store-id " setting stream to atleast online.")
                   (ensure-stream-is-atleast-online state store-id))
               (throw (ex-info "User was was not a store owner" {:stream/token token}))))})

(defmutation stream/ensure-online
  [{:keys [state auth] :as env} k {:keys [store-id] :as p}]
  {:auth {::auth/store-owner store-id}
   :resp {:success "Stream is online"
          :error   "Could not ensure stream was online"}}
  {:action (fn []
             (ensure-stream-is-atleast-online state store-id))})

(defmutation stream/go-live
  [{:keys [state auth] :as env} k {:keys [store-id] :as p}]
  {:auth {::auth/store-owner store-id}
   :resp {:success "Went live!"
          :error   "Could not go live"}}
  {:action (fn []
             (db/transact state [{:stream/store store-id
                                  :stream/state :stream.state/live}]))})

(defmutation stream/end-live
  [{:keys [state auth] :as env} k {:keys [store-id] :as p}]
  {:auth {::auth/store-owner store-id}
   :resp {:success "Ended live stream!"
          :error   "Could not end live stream."}}
  {:action (fn []
             (quiet-cas state [[:db.fn/cas [:stream/store store-id] :stream/state :stream.state/live :stream.state/online]]))})

(defmutation stream/go-offline
  [{:keys [state auth] :as env} k {:keys [store-id] :as p}]
  {:auth {::auth/store-owner store-id}
   :resp {:success "Went offline!"
          :error   "Could not go offline"}}
  {:action (fn []
             (db/transact state [{:stream/store store-id
                                  :stream/state :stream.state/offline}]))})

(defmutation stream/go-offline-with-token
  [{:keys [state] :as env} k {:keys [stream/token] :as p}]
  ;; We do some custom authing with the stream token.
  {:auth ::auth/public
   :resp {:success "Went offline!"
          :error   "Could not go offline"}}
  {:action (fn []
             (if-let [store-id (wowza-token-store-owner env token)]
               (db/transact state [{:stream/store store-id
                                    :stream/state :stream.state/offline}])
               (throw (ex-info "User in stream token was not a store owner" {:stream/token token}))))})

;########### STORE @############
(defmutation store/update-info
  [{:keys [state ::parser/return ::parser/exception auth system]} _ {:keys [store/profile] :as store}]
  {:auth {::auth/store-owner (:db/id store)}
   :resp {:success "Your store info was updated"
          :error   "Could not update store info"}}
  {:action (fn []
             (let [db-store (db/pull (db/db state) [:store/profile] (:db/id store))
                   s (-> (select-keys profile [:store.profile/name :store.profile/description :store.profile/tagline :store.profile/return-policy])
                         (update :store.profile/description #(f/str->bytes (quill/sanitize-html %)))
                         (update :store.profile/return-policy #(f/str->bytes (quill/sanitize-html %)))
                         ;(update :store.profile/shipping-fee #(if (not-empty %)
                         ;                                      (when-let [fee (c/parse-long-safe %)]
                         ;                                        (bigdec fee))
                         ;                                      (bigdec 0)))
                         ;(update :store.profile/shipping-policy #(f/str->bytes (quill/sanitize-html %)))
                         f/remove-nil-keys
                         (assoc :db/id (:db/id (:store/profile db-store))))]
               (debug "store/update-info with params: " s)
               (db/transact-one state s)))})

(defmutation store/update-product-order
  [{:keys [state ::parser/return ::parser/exception auth system]} _ {:keys [items store-id]}]
  {:auth {::auth/store-owner store-id}
   :resp {:success "Your store info was updated"
          :error   "Could not update store info"}}
  {:action (fn []
             (db/transact state (map (fn [p]
                                       [:db/add (:db/id p) :store.item/index (:store.item/index p)])
                                     items)))})

(defmutation store/update-sections
  [env _ {:keys [store-id] :as p}]
  {:auth {::auth/store-owner store-id}
   :resp {:success "Your store sections were updated"
          :error   "Could not update store sections."}}
  {:action (fn []
             (debug "store/update-sections with params: " p)
             (store/update-sections env (c/parse-long store-id) p))})

;######## STRIPE ########

(comment
  ;; This mutation is not safe. It finds any store that a user is owner of and adds a stripe account to it.
  ;; Should probably pass which store it is creating an account for and use ::auth/store-owner as auth.
  (defmutation stripe/create-account
    [{:keys [state ::parser/return ::parser/exception auth system]} _ _]
    {:auth {:TODO-stripe-create-account-auth true}
     :resp {:success "Your account was created"
            :error   "Could not create Stripe account"}}
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
                                     [:db/add store :store/stripe (:db/id stripe-info)]])))}))

(defmutation stripe/update-account
  [{:keys [state ::parser/return ::parser/exception auth system]} _ {:keys [store-id account-params]}]
  {:auth {::auth/store-owner store-id}
   :resp {:success "Your account was updated"
          :error   (if (some? exception)
                     (or (.getMessage exception) "Something went wrong")
                     "Something went wrong!")}}
  {:action (fn []
             (let [{:stripe/keys [id]} (stripe/pull-stripe (db/db state) store-id)]
               (stripe/update-account (:system/stripe system) id account-params)))})

(defmutation stripe/update-customer
  [{:keys [state ::parser/return ::parser/exception auth system]} _ {:keys [shipping source]}]
  {:auth {::auth/any-user true}
   :resp {:success return
          :error   (if (some? exception)
                     (or (.getMessage exception) "Something went wrong")
                     "Something went wrong!")}}
  {:action (fn []
             (let [{:stripe/keys [id]} (stripe/pull-user-stripe (db/db state) (:user-id auth))
                   new-card (when source (stripe/create-card (:system/stripe system) id source))]
               (when shipping
                 (let [address (:shipping/address shipping)]
                   (stripe/update-customer (:system/stripe system)
                                           id
                                           {:shipping {:name    (:shipping/name shipping)
                                                       :address {:line1       (:shipping.address/street address)
                                                                 :line2       (:shipping.address/street2 address)
                                                                 :postal_code (:shipping.address/postal address)
                                                                 :city        (:shipping.address/locality address)
                                                                 :state       (:shipping.address/region address)}}})))
               {:new-card     new-card}))})

(defmutation store/create
  [{:keys [state ::parser/return ::parser/exception auth system] :as env} _ params]
  {:auth ::auth/any-user
   :resp {:success return
          :error   "Could not create Stripe account"}}
  {:action (fn []
             (let [{store-owner :store.owner/_user} (db/pull (db/db state) [:store.owner/_user] (:user-id auth))]
               (debug "Store owner: " store-owner)
               (if (some? store-owner)
                 (throw (ex-info "Cannot create multiple stores for one user."
                                 {:user-id (:user-id auth)}))
                 (store/create env params))))})

(defmutation store/create-product
  [env _ {:keys [product store-id] :as p}]
  {:auth {::auth/store-owner store-id}
   :resp {:success "Your account was created"
          :error   "Could not create Stripe account"}}
  {:action (fn []
             (debug "store/create-product with params: " p)
             (store/create-product env (c/parse-long store-id) product))})

(defmutation store/update-product
  [env _ {:keys [product store-id product-id] :as p}]
  {:auth {::auth/store-owner store-id}
   :resp {:success "Your account was created"
          :error   "Could not create Stripe account"}}
  {:action (fn []
             (debug "store/update-product with params: " p)
             (store/update-product env (c/parse-long product-id) product))})

(defmutation store/delete-product
  [env _ {:keys [product store-id]}]
  {:auth {::auth/store-owner store-id}
   :resp {:success "Product deleted"
          :error   "Could not delete product"}}
  {:action (fn []
             (store/delete-product env (:db/id product)))})

(defmutation store/create-order
  [{::parser/keys [return exception] :as env} _ {:keys [order store-id] :as p}]
  {:auth {::auth/any-user true}
   :resp {:success return
          :error   (let [default-msg "Could not create order"]
                     (if (some? exception)
                       (:user-message (ex-data exception) default-msg)
                       default-msg))}}
  {:action (fn []
             (store/create-order env store-id order))})

(defmutation chat/send-message
  [{::parser/keys [exception] :keys [state system] :as env} k {:keys [store text user]}]
  {:auth {::auth/exact-user (:db/id user)}
   :resp {:success "Message sent"
          :error   (if (some? exception)
                     (.getMessage exception)
                     "Someting went wrong!")}}
  {:action (fn []
             (chat/write-message (:system/chat system) store user text))})

(defmutation store/update-order
  [env _ {:keys [order-id store-id params] :as p}]
  {:auth {::auth/store-owner store-id}
   :resp {:success "Order created"
          :error   "Could not create order"}}
  {:action (fn []
             (store/update-order env (c/parse-long store-id) (c/parse-long order-id) params))})
