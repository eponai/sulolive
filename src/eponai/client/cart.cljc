(ns eponai.client.cart
  (:require
    [eponai.common.database :as db]
    [eponai.client.local-storage :as local-storage]
    [eponai.common.shared :as shared]
    [taoensso.timbre :refer [debug]]))

(defn find-user-cart
  "Returns a tuple of [user-id cart], where cart is the :db/id of :user/cart ?cart."
  [db user-id]
  (if (some? user-id)
    [user-id (db/one-with db {:where   '[[?user :user/cart ?e]]
                              :symbols {'?user user-id}})]
    (some->> (seq (db/datoms db :aevt :user/cart))
             (first)
             ((juxt :e :v)))))

(defn store-cart-in-local-storage [db local-storage]
  (let [[_ cart] (find-user-cart db nil)]
    (local-storage/set-item! local-storage
                             ::anonymous-cart
                             (db/pull db [{:user.cart/items [:db/id]}] cart))))

(defn remove-cart [reconciler]
  (local-storage/remove-item! (shared/by-key reconciler :shared/local-storage)
                              ::anonymous-cart))

(defn- get-cart [reconciler]
  (local-storage/get-item (shared/by-key reconciler :shared/local-storage)
                          ::anonymous-cart))

(defn get-skus [reconciler]
  (into #{} (map :db/id) (:user.cart/items (get-cart reconciler))))

(defn restore-cart [reconciler]
  (let [state (get-in reconciler [:config :state])
        _ (assert (db/connection? state) "Couldn't get state from reconciler")
        [user-id cart] (find-user-cart (db/db state) nil)
        cart-items (get-cart reconciler)]
    (when (some? cart-items)
      (assert (map? cart-items)
              (str "cart items was not a map. Was: " cart-items))
      (debug "Restoring cart: " cart-items)
      (db/transact state [{:db/id     user-id
                           :user/cart (assoc cart-items :db/id cart)}]))))
