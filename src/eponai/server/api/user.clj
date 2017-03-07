(ns eponai.server.api.user
  (:require
    [eponai.common.database :as db]
    [eponai.server.external.stripe :as stripe]
    [taoensso.timbre :refer [debug]]
    [eponai.common.format :as f]))

(defn ->order [state o store-id user-id]
  (let [store (db/lookup-entity (db/db state) store-id)]
    (-> o
        (assoc :order/store store :order/user user-id)
        ;(update :order/items (fn [its]
        ;                       (let [skus (filter #(= (:order.item/type %) :sku) its)
        ;                             ;(debug "Order items: " (into [] skus))
        ;                             ;(debug "UUIDs: " (mapv #(f/str->uuid (:order.item/parent %)) skus))
        ;                             db-skus (db/pull-all-with (db/db state)
        ;                                               '[:db/id :store.item/_skus :store.item.sku/uuid]
        ;                                               {:where   '[[?e :store.item.sku/uuid ?uuid]]
        ;                                                :symbols {'[?uuid ...] (mapv #(f/str->uuid (:order.item/parent %)) skus)}})]
        ;                         db-skus)))
        )))

(defn list-orders [{:keys [state system]} user-id]
  (let [orders (db/pull-all-with (db/db state)
                                 [:db/id :order/store :order/id]
                                 {:where   '[[?e :order/user ?u]]
                                  :symbols {'?u user-id}})
        orders-by-store (group-by :order/store orders)
        stripe-orders (reduce-kv (fn [l store orders]
                                   (let [store-id (:db/id store)
                                         {:keys [stripe/secret]} (stripe/pull-stripe (db/db state) store-id)
                                         order-ids (map :order/id orders)
                                         stripe-orders (stripe/list-orders (:system/stripe system) secret {:ids order-ids})]
                                     (apply conj l (map #(->order state % store-id user-id) stripe-orders))))
                                 []
                                 orders-by-store)]
    (map (fn [o]
           (let [db-order (some #(when (= (:order/id %) (:order/id o)) %) orders)]
             (assoc o :db/id (:db/id db-order))))
         stripe-orders)))

(defn get-order [{:keys [state system]} user-id order-id]
  (let [order (db/pull (db/db state) '[:order/store :order/id] order-id)
        store-id (get-in order [:order/store :db/id])
        {:keys [stripe/secret]} (stripe/pull-stripe (db/db state) store-id)]
    (assoc (->order state
                    (stripe/get-order (:system/stripe system)
                                      secret
                                      (:order/id order))
                    store-id
                    user-id)
      :db/id order-id)))