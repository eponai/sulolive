(ns eponai.server.parser.mutate
  (:require
    [environ.core :as env]
    [eponai.common.database :as db]
    [eponai.common.stream :as stream]
    [eponai.common.format :as format]
    [eponai.server.external.mailchimp :as mailchimp]
    [eponai.common.parser :as parser]
    [taoensso.timbre :as timbre :refer [debug info warn]]
    [clojure.data.json :as json]
    [eponai.server.api :as api]
    [eponai.server.external.stripe :as stripe]
    [eponai.server.external.wowza :as wowza]
    [eponai.server.external.chat :as chat]
    [eponai.server.external.vods :as vods]
    [eponai.common :as c]
    [buddy.sign.jwt :as jwt]
    [eponai.server.api.store :as store]
    [eponai.server.external.aws-s3 :as s3]
    [eponai.common.format :as f]
    [eponai.common.ui.om-quill :as quill]
    [eponai.common.auth :as auth]
    [eponai.server.external.cloudinary :as cloudinary]
    [eponai.server.external.email :as email]
    [eponai.server.api.user :as user]
    [eponai.common.format.date :as date]
    [eponai.server.external.firebase :as firebase])
  (:import (java.io File)))

(defmacro defmutation
  "Creates a message and mutate defmethod at the same time.
  The body takes two maps. The first body is the message and the
  other is the mutate.
  The :return and :exception key in env is only available in the
  message body."
  [sym args auth-and-message mutate-body]
  (assert (and (map? auth-and-message) (every? (set (keys auth-and-message)) #{:auth :resp}))
          (str "defmutation's auth and message body needs to be a map with :auth and :resp."
               " was: " auth-and-message))
  (assert (== 3 (count args))
          (str "defread needs argument vector to take 3 arguments, was: " args))
  ;; For return values of :log, see comment in parser/log-param-keys
  (let [log-param-key# (when-let [pk# (:log auth-and-message)]
                         `(defmethod parser/log-param-keys (quote ~sym) ~args ~pk#))]
    `(let [mutation# (quote ~sym)]
       ~log-param-key#
       (defmethod parser/server-message mutation# ~args ~(:resp auth-and-message))
       (defmethod parser/server-auth-role mutation# ~args ~(:auth auth-and-message))
       (defmethod parser/server-mutate mutation# ~args ~mutate-body))))

;; TODO: Make this easier to use. Maybe return {::parser/force-read [:key1 :key2]} in the
;;       return value of the mutate?
(defn force-read-keys! [{:keys [::parser/force-read-without-history] :as env} k & ks]
  (apply swap! force-read-without-history conj k ks)
  nil)

(defmutation shopping-bag/add-items
  [{:keys [state auth] :as env} _ {:keys [skus]}]
  {:auth ::auth/any-user
   :log  [:skus]
   :resp {:success "Item added to bag"
          :error   "Did not add that item, sorry"}}
  {:action (fn []
             (let [{:keys [user-id]} auth
                   cart (db/one-with (db/db state) {:where   '[[?u :user/cart ?e]]
                                                    :symbols {'?u user-id}})
                   sku-ids (into [] (map c/parse-long-safe) skus)]
               (if (some? cart)
                 (db/transact state (into [] (map #(vector :db/add cart :user.cart/items %))
                                          sku-ids))
                 (let [new-cart {:db/id           (db/tempid :db.part/user)
                                 :user.cart/items sku-ids}]
                   (db/transact state [new-cart
                                       [:db/add user-id :user/cart (:db/id new-cart)]])))))})

(defmutation shopping-bag/remove-item
  [{:keys [state auth] :as env} _ {:keys [sku]}]
  {:auth ::auth/any-user
   :log  [:sku]
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

(defmutation location/suggest
  [{:keys [state auth system] ::parser/keys [return exception]} _ {:keys [location site email]}]
  {:auth ::auth/public
   :resp {:success "Thank you! Check your inbox for a confirmation email."
          :error   (if exception (:detail (json/read-str (:body (ex-data exception)) :key-fn keyword) "") "")}}
  {:action (fn []
             (debug "location/suggest with email " email)
             (let [ret (mailchimp/subscribe (:system/mailchimp system)
                                            {:email        email
                                             :merge-fields {"LOCATION" location}
                                             :list-id      (env/env :mailchimp-newsletter-id)})]
               ret))})

(defmutation photo/upload
  [{:keys [state ::parser/return ::parser/exception system auth] :as env} _ params]
  {:auth ::auth/any-user
   :log  [:photo]
   :resp {:success "Your photo was successfully uploaded."
          :error   "Sorry, your photo failed to upload. Try again later."}}
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
  [{:keys [state ::parser/return ::parser/exception system auth] :as env} _ {:keys [:user/name] :as profile}]
  {:auth ::auth/any-user
   :log  [:user/name]
   :resp {:success "Your info was successfully updated."
          :error   (:message (ex-data exception) "Sorry, your info could not be updated. Try again later.")}}
  {:action (fn []
             (user/update-profile env profile))})

(defmutation store.photo/upload
  [{:keys [state ::parser/return ::parser/exception system auth] :as env} _ {:keys [photo photo-key store-id] :as p}]
  {:auth {::auth/store-owner {:store-id store-id}}
   :log  [:store-id :photo-key :photo]
   :resp {:success "Your photo was successfully uploaded."
          :error   "Sorry, your photo failed to upload. Try again later."}}
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
  {:auth {::auth/store-owner {:store-id store-id}}
   :log  [:store-id]
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
  [{:keys [state] ::parser/keys [exception] :as env} k {:keys [stream/token]}]
  ;; We do some custom authing with the stream token.
  {:auth ::auth/public
   :resp {:success "Stream went online"
          :error   (str "Could not go online: " (some-> exception (.getMessage))
                        (some-> exception (.getCause) (.getMessage)))}}
  {:action (fn []
             (if-let [store-id (wowza-token-store-owner env token)]
               (do (debug "Was store owner of store-id: " store-id " setting stream to atleast online.")
                   (ensure-stream-is-atleast-online state store-id))
               (throw (ex-info "User was was not a store owner" {:stream/token token}))))})

(defmutation stream/ensure-online
  [{:keys [state auth system] :as env} k {:keys [store-id] :as p}]
  {:auth {::auth/store-owner {:store-id store-id}}
   :log  [:store-id]
   :resp {:success "Stream is online"
          :error   "Could not ensure stream was online"}}
  {:action (fn []
             (vods/update-store-vods! (:system/vods system) store-id)
             (ensure-stream-is-atleast-online state store-id))})

(defmutation stream/go-live
  [{:keys [state auth] :as env} k {:keys [store-id] :as p}]
  {:auth {::auth/store-owner {:store-id store-id}}
   :log  [:store-id]
   :resp {:success "Went live!"
          :error   "Could not go live"}}
  {:action (fn []
             (db/transact state [{:stream/store store-id
                                  :stream/state :stream.state/live}]))})

(defmutation stream/end-live
  [{:keys [state auth system] :as env} k {:keys [store-id] :as p}]
  {:auth {::auth/store-owner {:store-id store-id}}
   :log  [:store-id]
   :resp {:success "Ended live stream!"
          :error   "Could not end live stream."}}
  {:action (fn []
             (vods/update-store-vods! (:system/vods system) store-id)
             (quiet-cas state [[:db.fn/cas [:stream/store store-id] :stream/state :stream.state/live :stream.state/online]]))})

(defmutation stream/go-offline
  [{:keys [state auth system] :as env} k {:keys [store-id] :as p}]
  {:auth {::auth/store-owner {:store-id store-id}}
   :log  [:store-id]
   :resp {:success "Went offline!"
          :error   "Could not go offline"}}
  {:action (fn []
             (vods/update-store-vods! (:system/vods system) store-id)
             (db/transact state [{:stream/store store-id
                                  :stream/state :stream.state/offline}]))})

(defmutation stream/go-offline-with-token
  [{:keys [state system] :as env} k {:keys [stream/token] :as p}]
  ;; We do some custom authing with the stream token.
  {:auth ::auth/public
   :resp {:success "Went offline!"
          :error   "Could not go offline"}}
  {:action (fn []
             (if-let [store-id (wowza-token-store-owner env token)]
               (do (vods/update-store-vods! (:system/vods system) store-id)
                 (db/transact state [{:stream/store store-id
                                     :stream/state :stream.state/offline}]))
               (throw (ex-info "User in stream token was not a store owner" {:stream/token token}))))})

;########### STORE @############
(defmutation store/update-info
  [{:keys [state ::parser/return ::parser/exception auth system]} _ {:keys [store/profile] :as store}]
  {:auth {::auth/store-owner {:store-id (:db/id store)}}
   :log  (let [profile (select-keys profile [:store.profile/name
                                             :store.profile/description
                                             :store.profile/tagline
                                             :store.profile/return-policy
                                             :store.profile/email])]
           (cond-> profile
                   (contains? profile :store.profile/description)
                   (assoc :store.profile/description true)
                   (contains? profile :store.profile/return-policy)
                   (assoc :store.profile/return-policy true)
                   :always
                   (assoc :store-id (:db/id store))))
   :resp {:success "Your store info was successfully updated."
          :error   "Sorry, your info could not be updated. Try again later."}}
  {:action (fn []
             (let [db-store (db/pull (db/db state) [:store/profile] (:db/id store))
                   s (-> (select-keys profile [:store.profile/name
                                               :store.profile/description
                                               :store.profile/tagline
                                               :store.profile/return-policy
                                               :store.profile/email])
                         (update :store.profile/description #(f/str->bytes (quill/sanitize-html %)))
                         (update :store.profile/return-policy #(f/str->bytes (quill/sanitize-html %)))
                         ;(update :store.profile/shipping-fee #(if (not-empty %)
                         ;                                      (when-let [fee (c/parse-long-safe %)]
                         ;                                        (bigdec fee))
                         ;                                      (bigdec 0)))

                         f/remove-nil-keys
                         (assoc :db/id (:db/id (:store/profile db-store))))]
               (debug "store/update-info with params: " s)
               (db/transact-one state s)))})

(defmutation store/update-status
  [{:keys [state ::parser/return ::parser/exception auth system] :as env} _ {:keys [store-id status]}]
  {:auth {::auth/store-owner {:store-id store-id}}
   :log  [:store-id :status]
   :resp {:success "Your store info was successfully updated."
          :error   "Sorry, your info could not be updated. Try again later."}}
  {:action (fn []
             (store/update-status env store-id status))})

(defmutation store/update-shipping
  [{:keys [state ::parser/return ::parser/exception auth system] :as env} _ {:keys [shipping store-id]}]
  {:auth {::auth/store-owner {:store-id store-id}}
   :log  [:store-id :shipping]
   :resp {:success "Your store info was successfully updated."
          :error   "Sorry, your info could not be updated. Try again later."}}
  {:action (fn []
             (debug "Update shipping: " shipping)
             (store/update-shipping env store-id shipping))})

(defmutation store/update-tax
  [{:keys [state ::parser/return ::parser/exception auth system] :as env} _ {:keys [tax store-id]}]
  {:auth {::auth/store-owner {:store-id store-id}}
   :log  [:store-id :tax]
   :resp {:success "Your store info was successfully updated."
          :error   "Sorry, your info could not be updated. Try again later."}}
  {:action (fn []
             (store/update-tax env store-id tax))})

(defmutation store/update-product-order
  [{:keys [state ::parser/return ::parser/exception auth system]} _ {:keys [items store-id]}]
  {:auth {::auth/store-owner {:store-id store-id}}
   :log  {:store-id store-id :items (into [] (map :db/id) items)}
   :resp {:success "Your product layout was updated."
          :error   "Sorry, could not update product order. Try again later."}}
  {:action (fn []
             (db/transact state (map (fn [p]
                                       [:db/add (:db/id p) :store.item/index (:store.item/index p)])
                                     items)))})

(defmutation store/update-sections
  [{:keys [db] :as env} _ {:keys [store-id] :as p}]
  {:auth {::auth/store-owner {:store-id store-id}}
   :log  [:store-id]
   :resp {:success "Your store sections were updated"
          :error   "Could not update store sections."}}
  {:action (fn []
             (debug "store/update-sections with params: " p)
             (store/update-sections env store-id p))})

(defmutation store/save-shipping-rule
  [{:keys [db] :as env} _ {:keys [store-id] :as p}]
  {:auth {::auth/store-owner {:store-id store-id}}
   :log  [:store-id]
   :resp {:success "Your shipping rule was successfully created."
          :error   "Sorry, failed to create shipping rule. Try again later."}}
  {:action (fn []
             (debug "store/update-sections with params: " p)
             (store/create-shipping-rule env store-id p))})

(defmutation store/delete-shipping-rule
  [{:keys [db] :as env} _ {:keys [store-id rule] :as p}]
  {:auth {::auth/store-owner {:store-id store-id}}
   :log  {:store-id store-id :shipping-rule-id (:db/id rule)}
   :resp {:success "Your shipping rule was successfully created."
          :error   "Sorry, failed to create shipping rule. Try again later."}}
  {:action (fn []
             (debug "store/update-sections with params: " p)
             (store/delete-shipping-rule env store-id rule))})

(defmutation store/update-shipping-rule
  [env _ {:keys [store-id shipping-rule] :as p}]
  {:auth {::auth/store-owner {:store-id store-id}}
   :log  {:store-id store-id :shipping-rule-id (:db/id shipping-rule)}
   :resp {:success "Your shipping rule was successfully updated."
          :error   "Sorry, failed to update shipping rule. Try again later."}}
  {:action (fn []
             (debug "store/update-shipping-rule with params: " p)
             (store/update-shipping-rule env (:db/id shipping-rule) shipping-rule))})

(defmutation store/update-username
  [{:keys [state ::parser/exception ::parser/return]} _ {:keys [store-id username] :as p}]
  {:auth {::auth/store-owner {:store-id store-id}}
   :log  {:store-id store-id :username username}
   :resp {:success return                                   ;"Your username was successfully updated."
          :error   (if exception
                     (ex-data exception)
                     "Sorry, something went wrong when updating your username.")}}
  {:action (fn []
             (debug "store/update-username with params: " p)
             (when (some? store-id)
               (if (not-empty username)
                 (if-let [store-with-username (db/pull (db/db state) [:db/id] [:store/username username])]
                   (when-not (= (:db/id store-with-username) store-id)
                     (throw (ex-info "Store username is already taken"
                                     {:message  "Store username is already taken"
                                      :store-id (:db/id store-with-username)})))
                   (do
                     (db/transact state [[:db/add store-id :store/username username]])
                     {:username username}))
                 (let [store (db/pull (db/db state) [:store/username] store-id)]
                   (db/transact state [[:db/retract store-id :store/username (:store/username store)]])
                   {:username username}))))})

;######## STRIPE ########

(defmutation stripe/upload-identity-document
  [{:keys [state system] ::parser/keys [return exception] :as env} _ {:keys [etag bucket key store-id file-type file-size]}]
  {:auth {::auth/store-owner {:store-id store-id}}
   :log  [:store-id :bucket :key :etag :file-type :file-size]
   :resp {:success "Your identity document was uploaded"
          :error   {:error (or (some-> exception (.getMessage))
                               "Something went wrong")}}}
  {:action (fn []
             (let [temp-file (File/createTempFile "stripe-s3" (str store-id))]
               (debug "Downloading file from s3 to temp.")
               ;; TODO: Use etag?
               (try
                 (s3/download-object (:system/aws-s3 system) {:bucket bucket :key key} temp-file)
                 (if (= file-size (.length temp-file))
                   (debug "Download OK! File sizes are equal: " file-size)
                   (warn "File size downloaded does not match file size on upload. Downloaded: " (.length temp-file)
                         " Uploaded: " file-size))
                 (stripe/upload-identity-document (:system/stripe system)
                                                  (:stripe/id (stripe/pull-stripe (db/db state) store-id))
                                                  {:file      temp-file
                                                   :file-type file-type})
                 (finally
                   (try
                     (.delete temp-file)
                     (catch Exception ignore))))))})

(defn walk-map-entries
  "Returns all the keys of a nested map"
  [m]
  (letfn [(children [x]
            (if (map-entry? x)
              (let [v (val x)]
                (when (coll? v) v))
              (seq x)))]
    (->> (tree-seq coll? children m)
         (filter map-entry?))))

(defmutation stripe/update-account
  [{:keys [state ::parser/return ::parser/exception system client-ip] :as env} _ {:keys [store-id account-params accept-terms?]}]
  {:auth {::auth/store-owner {:store-id store-id}}
   :log  {:store-id           store-id
          :accept-terms?      accept-terms?
          :account-param-keys (not-empty (vec (map key (walk-map-entries account-params))))}
   :resp {:success "Your account was updated"
          :error   (if (some? exception)
                     (or (.getMessage exception) "Something went wrong")
                     "Something went wrong!")}}
  {:action (fn []
             (let [{:stripe/keys [id]} (stripe/pull-stripe (db/db state) store-id)
                   params (cond-> account-params
                                  accept-terms?
                                  (assoc :field/tos-acceptance {:field.tos-acceptance/ip   client-ip
                                                                :field.tos-acceptance/date (date/current-secs)}))
                   _ (debug "Updating account with params: " params)
                   new-account (stripe/update-account (:system/stripe system) id params)]
               (store/stripe-account-updated env new-account)
               new-account))})

(defmutation stripe/update-customer
  [{:keys [state ::parser/return ::parser/exception auth system]} _ {:keys [shipping default-source source remove-source] :as params}]
  {:auth ::auth/any-user
   ;; Do we really want to log params for this mutation?
   :log  nil
   :resp {:success return
          :error   (if (some? exception)
                     (or (.getMessage exception) "Something went wrong")
                     "Something went wrong!")}}
  {:action (fn []
             (debug "Stripe update customer: " params)
             (let [{:user/keys          [email]
                    {:stripe/keys [id]} :user/stripe} (db/pull (db/db state) [:user/email {:user/stripe [:stripe/id]}] (:user-id auth))
                   customer-id (or id
                                   (:stripe/id (stripe/create-customer (:system/stripe system) {:email email})))
                   new-card (when source (stripe/create-card (:system/stripe system) customer-id source))
                   remove-source (when remove-source (stripe/delete-card (:system/stripe system) customer-id remove-source))

                   address (:shipping/address shipping)
                   stripe-params (cond-> {}
                                         (some? shipping)
                                         (assoc :shipping {:name    (:shipping/name shipping)
                                                           :address {:line1       (:shipping.address/street address)
                                                                     :line2       (:shipping.address/street2 address)
                                                                     :postal_code (:shipping.address/postal address)
                                                                     :city        (:shipping.address/locality address)
                                                                     :state       (:shipping.address/region address)
                                                                     :country     (:country/code (:shipping.address/country address))}})

                                         (some? default-source)
                                         (assoc :default_source default-source))]
               (when (and (nil? id) (some? customer-id))
                 (let [db-customer (f/add-tempid {:stripe/id customer-id})]
                   (db/transact state [db-customer
                                       [:db/add (:user-id auth) :user/stripe (:db/id db-customer)]])))
               (when (not-empty stripe-params)
                 (stripe/update-customer (:system/stripe system) customer-id stripe-params))

               {:new-card       new-card
                :shipping       shipping
                :default-source default-source
                :deleted-card   remove-source}))})

(defmutation store/create
  [{:keys [state ::parser/return ::parser/exception auth system] :as env} _ params]
  {:auth ::auth/any-user
   :log  [:country :name :locality]
   :resp {:success return
          :error   (:message (ex-data exception) "Something went wrong when starting your shop, try again later.")}}
  {:action (fn []
             (let [{store-owner :store.owner/_user} (db/pull (db/db state) [:store.owner/_user] (:user-id auth))]
               (debug "Store owner: " store-owner)
               (if (some? store-owner)
                 (throw (ex-info "Cannot create multiple stores for one user."
                                 {:user-id (:user-id auth)}))
                 (store/create env params))))})

(defn log-product
  "Return map with fields we want to log of a product."
  [product]
  (-> (select-keys product [:db/id :store.item/name :store.item/price :store.item/uuid :store.item/category])
      (assoc :store.item/description-length
             (when-let [d (:store.item/description product)]
               (when (seqable? d)
                 (count d))))))

(defmutation store/create-product
  [{:keys [db] :as env} _ {:keys [product store-id] :as p}]
  {:auth {::auth/store-owner {:store-id store-id}}
   :log  (-> (log-product product)
             (assoc :store-id store-id))
   :resp {:success "Your product was created."
          :error   "Sorry, could not create your product. Try again later."}}
  {:action (fn []
             (debug "store/create-product with params: " p)
             (store/create-product env store-id product))})

(defmutation store/update-product
  [{:keys [db] :as env} _ {:keys [product store-id product-id] :as p}]
  {:auth {::auth/store-owner {:store-id store-id}}
   :log  (merge (log-product product)
                {:store-id store-id :product-id product-id})
   :resp {:success "Your product was updated."
          :error   "Sorry, could not update your product. Try again later."}}
  {:action (fn []
             (debug "store/update-product with params: " p)
             (store/update-product env product-id product))})

(defmutation store/delete-product
  [env _ {:keys [product store-id]}]
  {:auth {::auth/store-owner {:store-id store-id}}
   :log  {:store-id store-id :product-id (:db/id product)}
   :resp {:success "Your product was deleted."
          :error   "Sorry, could not delete your product. Try again later."}}
  {:action (fn []
             (store/delete-product env (:db/id product)))})

(defmutation store/create-order
  [{::parser/keys [return exception db] :as env} _ {:keys [order store-id] :as p}]
  {:auth ::auth/any-user
   :log  (merge (select-keys p [:subtotal :grandtotal :tax-amount :shipping-rate])
                {:store-id store-id :items (into [] (map :db/id) (:items order))})
   :resp {:success return
          :error   (let [default-msg "Could not create order"]
                     (if (some? exception)
                       (:message (ex-data exception) default-msg)
                       default-msg))}}
  {:action (fn []
             (store/create-order env store-id order))})

(defmutation chat/send-message
  [{::parser/keys [exception] :keys [state system] :as env} k {:keys [store text user]}]
  {:auth {::auth/exact-user {:user-id (:db/id user)}}
   :log  ::parser/no-logging
   :resp {:success "Message sent"
          :error   (if (some? exception)
                     (.getMessage exception)
                     "Someting went wrong!")}}
  {:action (fn []
             (debug "In send-message.")
             (let [message (c/substring text 0 500)]
               (chat/write-message (:system/chat system) store user message))
             (let [user-entity (db/lookup-entity (db/db state) (:db/id user))
                   store-entity (db/lookup-entity (db/db state) (:db/id store))
                   {:keys [store/owners]} (db/pull (db/db state) [{:store/owners [:store.owner/user]}] (:db/id store))]
               (debug "Will send notification to: " owners)
               (when-not (= (:db/id (:store.owner/user owners)) (:db/id user-entity))
                 (firebase/-send-chat-notification (:system/firebase system)
                                                   (get-in owners [:store.owner/user :db/id])
                                                   {:title    (c/substring (get-in store-entity [:store/profile :store.profile/name]) 0 20)
                                                    :subtitle (str (c/substring (get-in user-entity [:user/profile :user.profile/name] "anonymous") 0 20) " wrote:")
                                                    :type     :notification.type/chat
                                                    :message  (c/substring text 0 100)})))
             nil)})

(defmutation store/update-order
  [{:keys [db] :as env} _ {:keys [order-id store-id params] :as p}]
  {:auth {::auth/store-owner {:store-id store-id}}
   :log  {:store-id store-id :order-id order-id :order/status (:order/status params)}
   :resp {:success "Order was updated"
          :error   "Sorry, could not update order. Try again later."}}
  {:action (fn []
             (store/update-order env store-id (c/parse-long order-id) params))})

(defmutation store/delete
  [{:keys [db] :as env} _ {:keys [store-id] :as p}]
  {:auth {::auth/store-owner {:store-id store-id}}
   :log  {:store-id store-id}
   :resp {:success "Your store was deleted."
          :error   "Could not delete store."}}
  {:action (fn []
             (store/delete env store-id))})

(defmutation user/request-store-access
  [{:keys [system auth state]} _ params]
  {:auth ::auth/public
   :log  nil
   :resp {:success "Thank you! Your request has been sent to us and we'll get in touch shortly."
          :error   "Sorry, your request couldn't be sent. Try again later."}}
  {:action (fn []
             (debug "AUTH: " auth)
             (let [user-id (when (some? (:email auth))
                             (db/one-with (db/db state) {:where   '[[?e :user/email ?email]]
                                                         :symbols {'?email (:email auth)}}))]
               (email/-send-store-access-request (:system/email system) (assoc params :user-id user-id))))})

(defmutation user/create
  [{:keys [system auth state ::parser/exception ::parser/return query-params] :as env} _ params]
  {:auth ::auth/public
   :log  nil
   :resp {:success return
          :error   (:message (ex-data exception) "Something went wrong when creating your account.")}}
  {:action (fn []
             (user/create env params))})

(defmutation user/unlink-account
  [{:keys [system auth state ::parser/exception ::parser/return] :as env} _ params]
  {:auth ::auth/public
   :log  nil
   :resp {:success "Some message"
          :error   (if exception
                     (ex-data exception)
                     "Something went wrong when creating your account.")}}
  {:action (fn []
             (debug "user/unlink-account with params: " params)
             {:unlinked-accounts (user/unlink-user env params)})})

(defmutation firebase/register-token
  [{:keys [system auth state ::parser/exception ::parser/return] :as env} _ {:keys [token]}]
  {:auth ::auth/public
   :log  nil
   :resp {:success "Some message"
          :error   "Something went wrong when creating your account."}}
  {:action (fn []
             (debug "FIREBASE ROKEN FOR AUTH: " auth)
             (firebase/-register-device-token (:system/firebase system) (:user-id auth) token)
             nil)})


(defmutation checkout/apply-coupon
  [{:keys [system auth state ::parser/exception ::parser/return] :as env} _ {:keys [code]}]
  {:auth ::auth/any-user
   :log  [:code code]
   :resp {:success return
          :error   (or (ex-data exception)
                       {:message "Couldn't apply coupon"})}}
  {:action (fn []
             (let [coupon (stripe/get-coupon (:system/stripe system) code)]
               (if (:valid coupon)
                 coupon
                 (throw (ex-info "Coupon is invalid" {:message "Coupon is invalid"})))))})