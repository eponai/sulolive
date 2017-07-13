(ns eponai.client.parser.mutate
  (:require
    [eponai.common.parser :as parser :refer [client-mutate client-auth-role]]
    [eponai.common.format :as format]
    [eponai.common.auth :as auth]
    [eponai.client.chat :as client.chat]
    [eponai.client.auth :as client.auth]
    [eponai.client.cart :as client.cart]
    [eponai.common.shared :as shared]
    [eponai.common.database :as db]
    [datascript.core :as datascript]
    [eponai.common :as c]
    [taoensso.timbre :refer [debug warn]]))

; Local mutations

(defmethod client-mutate 'dashboard/change-product-view
  [{:keys [target db state reconciler]} _ {:keys [product-view]}]
  {:action (db/transact-one state {:ui/singleton :ui.singleton/state
                               :ui.singleton.state/product-view product-view})})

(defmethod client-mutate 'client/set-locality
  [{:keys [target db state reconciler]} _ {:keys [locality]}]
  {:action (db/transact-one state {:ui/singleton :ui.singleton/auth
                                   :ui.singleton.auth/locations locality})})

(defmethod client-mutate 'loading-bar/show
  [{:keys [state target]} _ _]
  (when-not target
    {:action (fn []
               (db/transact state [{:ui/singleton                   :ui.singleton/loading-bar
                                    :ui.singleton.loading-bar/show? true}]))}))

(defmethod client-mutate 'loading-bar/hide
  [{:keys [state target]} _ _]
  (when-not target
    {:action (fn []
               (db/transact state [{:ui/singleton                   :ui.singleton/loading-bar
                                    :ui.singleton.loading-bar/show? false}]))}))

(defmethod client-mutate 'login-modal/show
  [{:keys [state target]} _ _]
  (when-not target
    {:action (fn []
               (debug "Transact show login")
               (db/transact state [{:ui/singleton                   :ui.singleton/login-modal
                                    :ui.singleton.login-modal/show? true}]))}))

(defmethod client-mutate 'login-modal/hide
  [{:keys [state target]} _ _]
  (when-not target
    {:action (fn []
               (db/transact state [{:ui/singleton                   :ui.singleton/login-modal
                                    :ui.singleton.login-modal/show? false}]))}))

(defmethod client-mutate 'notification/receive
  [{:keys [state target]} _ notification]
  (when-not target
    {:action (fn []
               (debug "Firebase - notification/receive: " notification)
               (let [db-notification (assoc notification :db/id -1)]
                 (db/transact state [db-notification
                                     [:db/add [:ui/singleton :ui.singleton/notify] :ui.singleton.notify/notifications -1]])))}))

(defmethod client-mutate 'notification/remove
  [{:keys [state target]} _ {:keys [id]}]
  (when-not target
    {:action (fn []
               (debug "Firebase - notification/remove: " id)
               (when-let [notification (db/one-with (db/db state) {:where   '[[?e :notification/id ?id]]
                                                                   :symbols {'?id id}})]
                 (db/transact state [[:db.fn/retractEntity notification]])))}))

;; ################ Remote mutations ####################
;; Remote mutations goes here. We share these mutations
;; with all client platforms (web, ios, android).
;; Local mutations should be defined in:
;;     eponai.<platform>.parser.mutate

(defmethod client-mutate 'shopping-bag/add-items
  [{:keys [target db state reconciler]} _ {:keys [skus]}]
  (let [user-id (client.auth/current-auth db)
        logged-in? (some? user-id)]
    (if target
      (when logged-in?
        {:remote true})
      {:action (fn []
                 (let [[user-id cart] (client.cart/find-user-cart db user-id)
                       sku-ids (into [] (map c/parse-long-safe) skus)]
                   (if (some? cart)
                     (db/transact state (into []
                                              (map #(vector :db/add cart :user.cart/items %))
                                              sku-ids))
                     (let [new-cart {:db/id           (db/tempid :db.part/user)
                                     :user.cart/items sku-ids}]
                       (db/transact state [new-cart
                                           [:db/add (or user-id (db/tempid :db.part/user))
                                            :user/cart (:db/id new-cart)]])))
                   (when (not logged-in?)
                     (client.cart/store-cart-in-local-storage
                       (db/db state)
                       (shared/by-key reconciler :shared/local-storage)))))})))

(defmethod client-mutate 'shopping-bag/remove-item
  [{:keys [target db state reconciler]} _ {:keys [sku]}]
  (let [user-id (client.auth/current-auth db)
        logged-in? (some? user-id)]
    (if target
      (when logged-in?
        {:remote true})
      {:action (fn []
                 (let [[_ cart] (client.cart/find-user-cart db user-id)
                       sku-id (c/parse-long-safe sku)]
                   (when (some? cart)
                     (db/transact-one state [:db/retract cart :user.cart/items sku-id])
                     (when (not logged-in?)
                       (client.cart/store-cart-in-local-storage
                         (db/db state)
                         (shared/by-key reconciler :shared/local-storage))))))})))

(defmethod client-mutate 'search/search
  [{:keys [target]} _ {:keys [search-string]}]
  (if target
    {:remote true}
    {:action (fn []
               (debug "Mutate search string: " search-string))}))

(defmethod client-mutate 'routes/set-route!
  [{:keys [state]} _ {:keys [route route-params query-params]}]
  {:action (fn []
             (debug "Setting route: " route " route-params: " route-params)
             (let [id (:db/id (db/entity (db/db state) [:ui/singleton :ui.singleton/routes]))]
               (db/transact state [[:db/add id :ui.singleton.routes/current-route route]
                                   (if (seq route-params)
                                     [:db/add id :ui.singleton.routes/route-params route-params]
                                     [:db.fn/retractAttribute id :ui.singleton.routes/route-params])
                                   (if (seq query-params)
                                     [:db/add id :ui.singleton.routes/query-params query-params]
                                     [:db.fn/retractAttribute id :ui.singleton.routes/query-params])])))})

(defmethod client-mutate 'beta/vendor
  [{:keys [target]} _ p]
  (debug "beta/vendor with params: " p)
  (if target
    {:remote true}))

(defmethod client-mutate 'beta/customer
  [{:keys [target]} _ p]
  (debug "beta/customer with params: " p)
  (if target
    {:remote true}))

(defmethod client-mutate 'location/suggest
  [{:keys [target]} _ p]
  (debug "location/suggest with params: " p)
  (if target
    {:remote true}))

(defmethod client-mutate 'photo/upload
  [{:keys [target]} _ p]
  (debug "photo/upload with params: " p)
  (if target
    {:remote true}))

(defmethod client-mutate 'user.info/update
  [{:keys [target]} _ p]
  (debug "user.info/update with params: " p)
  (if target
    {:remote true}))

(defmethod client-mutate 'store.photo/upload
  [{:keys [target]} _ p]
  (debug "store.photo/upload with params: " p)
  (if target
    {:remote (some? (:photo p))}))

;; ########### STORE #################

(defmethod client-mutate 'store/create
  [{:keys [target]} _ p]
  (if target
    {:remote true}))

(defmethod client-mutate 'store/delete
  [{:keys [target]} _ p]
  (if target
    {:remote true}))

(defmethod client-mutate 'store/update-info
  [{:keys [target]} _ p]
  (debug "store/update-info with params: " p)
  (if target
    {:remote true}))

(defmethod client-mutate 'store/update-sections
  [{:keys [target]} _ p]
  (debug "store/update-sections with params: " p)
  (if target
    {:remote true}))

(defmethod client-mutate 'store/update-product-order
  [{:keys [state target]} _ {:keys [items] :as p}]
  (debug "store/update-sections with params: " p)
  (if target
    {:remote true}
    {:action (fn []
               (db/transact state (map (fn [p]
                                         [:db/add (:db/id p) :store.item/index (:store.item/index p)])
                                       items)))}))

(defmethod client-mutate 'store/update-status
  [{:keys [state target]} _ {:keys [items] :as p}]
  (if target
    {:remote true}))

;; ########### STRIPE ###############

(defmethod client-mutate 'stripe/create-account
  [{:keys [target]} _ p]
  (debug "stripe/create-account with params: " p)
  (if target
    {:remote true}))

(defmethod client-mutate 'stripe/update-account
  [{:keys [target]} _ p]
  (debug "stripe/update-account with params: " p)
  (if target
    {:remote true}))

(defmethod client-mutate 'stripe/update-customer
  [{:keys [target]} _ p]
  (debug "stripe/update-customer with params: " p)
  (if target
    {:remote true}))

(defmethod client-mutate 'stripe/upload-identity-document
  [{:keys [target]} _ p]
  (debug "stripe/upload-identity-document with params: " p)
  (if target
    {:remote true}))

;; ########### STORE ##############

(defmethod client-mutate 'store/create-product
  [{:keys [target]} _ p]
  (debug "store/create-product with params: " p)
  (if target
    {:remote true}))

(defmethod client-mutate 'store/update-product
  [{:keys [target]} _ p]
  (debug "store/update-product with params: " p)
  (if target
    {:remote true}))

(defmethod client-mutate 'store/delete-product
  [{:keys [target]} _ p]
  (debug "store/delete-product with params: " p)
  (if target
    {:remote true}))

(defmethod client-mutate 'store/create-order
  [{:keys [target]} _ p]
  (debug "store/create-order with params: " p)
  (if target
    {:remote true}))

(defmethod client-mutate 'store/update-order
  [{:keys [target]} _ p]
  (debug "store/update-order with params: " p)
  (if target
    {:remote true}))

(defmethod client-mutate 'store/save-shipping-rule
  [{:keys [target]} _ p]
  (debug "store/save-shipping-rule with params: " p)
  (if target
    {:remote true}))

(defmethod client-mutate 'store/delete-shipping-rule
  [{:keys [target]} _ p]
  (debug "store/delete-shipping-rule with params: " p)
  (if target
    {:remote true}))

(defmethod client-mutate 'store/update-shipping-rule
  [{:keys [target]} _ p]
  (debug "store/update-shipping-rule with params: " p)
  (if target
    {:remote true}))

(defmethod client-mutate 'store/update-shipping
  [{:keys [target]} _ p]
  (debug "store/update-shipping with params: " p)
  (if target
    {:remote true}))

(defmethod client-mutate 'store/update-tax
  [{:keys [target state]} _ {:keys [store-id tax] :as p}]
  (debug "store/update-tax with params: " p)
  (if target
    {:remote true}
    {:action (fn []
               (let [{old-tax :store/tax} (db/pull (db/db state) [{:store/tax [:db/id :tax/rules]}] store-id)
                     new-tax (cond-> (format/tax tax)
                                     (some? (:db/id old-tax))
                                     (assoc :db/id (:db/id old-tax)))

                     txs (cond-> [new-tax]
                                 (db/tempid? (:db/id new-tax))
                                 (conj [:db/add store-id :store/tax (:db/id new-tax)]))]
                 (debug "Transacting new tax: " txs)
                 (db/transact state txs)))}))

(defmethod client-mutate 'store/update-username
  [{:keys [target]} _ p]
  (debug "store/update-username with params: " p)
  (if target
    {:remote true}))



(defmethod client-mutate 'stream-token/generate
  [{:keys [target]} k p]
  (assert (some? (:store-id p))
          (str ":store-id required for mutation: " k))
  (if target
    {:remote true}))

(defmethod client-auth-role 'chat/send-message [_ _ _]
  ::auth/any-user)
(defmethod client-mutate 'chat/send-message
  [{:keys [state target db ast auth]} k {:keys [store text]}]
  (let [user-id (:user-id auth)]
    (if target
      {:remote/chat (assoc-in ast [:params :user :db/id] user-id)}
      {:action (fn []
                 (when-let [chat-db (client.chat/get-chat-db db)]
                   (let [tx (format/chat-message chat-db store {:db/id user-id} text)
                         message-id (::format/message-id (meta tx))
                         chat-db (datascript/db-with chat-db (conj tx {:db/id                             message-id
                                                                       :chat.message/client-side-message? true}))]
                     (db/transact state (client.chat/set-chat-db-tx chat-db)))))})))

(defmethod client-mutate 'chat/queue-update
  [{:keys [state target]} k {:keys [store-id basis-t]}]
  (when-not target
    {:action (fn []
               (let [last-basis-t (client.chat/queued-basis-t (db/db state) store-id)]
                 (when (or (nil? last-basis-t) (< last-basis-t basis-t))
                   (db/transact state (client.chat/queue-basis-t-tx store-id basis-t)))))}))

(defmethod client-mutate 'stream/go-live
  [{:keys [state target]} _ {:keys [store-id]}]
  (when target
    {:remote true}))

(defmethod client-mutate 'stream/end-live
  [{:keys [state target]} _ {:keys [store-id]}]
  (when target
    {:remote true}))

(defmethod client-mutate 'stream/go-offline
  [{:keys [state target]} _ {:keys [store-id]}]
  (when target
    {:remote true}))

(defmethod client-mutate 'stream/ensure-online
  [{:keys [target]} _ _]
  (when target
    {:remote true}))

(defmethod client-mutate 'user/checkout
  [{:keys [target]} k p]
  (if target
    {:remote true}))

(defmethod client-mutate 'user/request-store-access
  [{:keys [state target]} _ _]
  (when target
    {:remote true}))

(defmethod client-mutate 'user/create
  [{:keys [state target]} _ _]
  (when target
    {:remote true}))

(defmethod client-mutate 'user/unlink-account
  [{:keys [state target]} _ _]
  (when target
    {:remote true}))

(defmethod client-mutate 'firebase/register-token
  [{:keys [state target]} _ _]
  (when target
    {:remote true}))

(defmethod client-mutate 'firebase/transact-event
  [{:keys [state target]} _ {:keys [txs]}]
  (if (nil? target)
    {:action (fn []
               (db/transact state txs))}))
