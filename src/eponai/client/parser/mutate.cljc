(ns eponai.client.parser.mutate
  (:require
    [eponai.common.parser :as parser :refer [client-mutate client-auth-role]]
    [eponai.common.format :as format]
    [eponai.common.auth :as auth]
    [eponai.client.chat :as chat]
    [eponai.common.database :as db]
    [taoensso.timbre :refer [debug warn]]))

;; ################ Remote mutations ####################
;; Remote mutations goes here. We share these mutations
;; with all client platforms (web, ios, android).
;; Local mutations should be defined in:
;;     eponai.<platform>.parser.mutate

(defn logged-in-update-cart [state item]
  (let [cart (db/one-with (db/db state) {:where '[[?e :user.cart/items]]})]
    (db/transact-one state [:db/add cart :user.cart/items (:db/id item)])))

(defmethod client-mutate 'shopping-bag/add-item
  [{:keys [target state]} _ {:keys [item]}]
  (if target
    {:remote true}                                          ;(auth/is-logged-in?)
    {:action (fn []
               ;(let [cart (db/one-with (db/db state) {:where '[[?e :cart/items]]})]
               ;  (db/transact-one state [:db/add cart :cart/items (:db/id item)]))

               ;(if (auth/is-logged-in?)
               ;  (logged-in-update-cart state item)
               ;  (anon-update-cart item))
               )}))

(defmethod client-mutate 'search/search
  [{:keys [target]} _ {:keys [search-string]}]
  (if target
    {:remote true}
    {:action (fn []
               (debug "Mutate search string: " search-string))}))

(defmethod client-mutate 'routes/set-route!
  [{:keys [state]} _ {:keys [route route-params]}]
  {:action (fn []
             (debug "Setting route: " route " route-params: " route-params)
             (let [id (:db/id (db/entity (db/db state) [:ui/singleton :ui.singleton/routes]))]
               (db/transact state [[:db/add id :ui.singleton.routes/current-route route]
                                   (if (seq route-params)
                                     [:db/add id :ui.singleton.routes/route-params route-params]
                                     [:db.fn/retractAttribute id :ui.singleton.routes/route-params])])))})

(defmethod client-mutate 'beta/vendor
  [{:keys [target]} _ p]
  (debug "beta/vendor with params: " p)
  (if target
    {:remote true}))

(defmethod client-mutate 'photo/upload
  [{:keys [target]} _ p]
  (debug "photo/upload with params: " p)
  (if target
    {:remote true}))

(defmethod client-mutate 'store.photo/upload
  [{:keys [target]} _ p]
  (debug "store.photo/upload with params: " p)
  (if target
    {:remote (some? (:photo p))}))

(defmethod client-mutate 'store/create
  [{:keys [target]} _ p]
  (if target
    {:remote true}))

;; ########### STORE #################

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
                 (let [tx (format/chat-message db store {:db/id user-id} text)
                       message-id (::format/message-id (meta tx))]
                   (db/transact state (conj tx {:db/id                             message-id
                                                :chat.message/client-side-message? true}))))})))

(defmethod client-mutate 'chat/queue-update
  [{:keys [state target]} k {:keys [store-id basis-t]}]
  (when-not target
    {:action (fn []
               (let [last-basis-t (chat/queued-basis-t (db/db state) store-id)]
                 (when (or (nil? last-basis-t) (< last-basis-t basis-t))
                   (db/transact state (chat/queue-basis-t-tx store-id basis-t)))))}))

(defmethod client-mutate 'stream/go-live
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

(defmethod client-mutate 'loading-bar/show
  [{:keys [state target]} _ _]
  (when-not target
    {:action (fn []
               (db/transact state [{:ui/singleton :ui.singleton/loading-bar
                                    :ui.singleton.loading-bar/show true}]))}))

(defmethod client-mutate 'loading-bar/hide
  [{:keys [state target]} _ _]
  (when-not target
    {:action (fn []
               (db/transact state [{:ui/singleton :ui.singleton/loading-bar
                                    :ui.singleton.loading-bar/show false}]))}))