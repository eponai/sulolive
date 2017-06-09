(ns eponai.server.api.store.order-test
  (:require
    [clojure.test :refer :all]
    [eponai.server.api.store :as api]
    [eponai.server.datomic.format :as f]
    [eponai.server.api.store.test-util :refer [store-test stripe-test-payment-succeeded stripe-test-payment-failed user-test]]
    [eponai.server.test-util :refer [new-db]]
    [clojure.core.async :as async]
    [eponai.common.database :as db]
    [eponai.common.format :as cf])
  (:import (clojure.lang ExceptionInfo)))

(deftest create-order-payment-succeeded
  (testing "Creating order should call Stripe and create a charge."
    (let [;; Prepare DB with products to order
          product (-> (f/product {:store.item/name "product" :store.item/price "10"})
                      (assoc :store.item/skus [(f/sku {:store.item.sku/variation "sku"})]))
          store (-> (store-test)
                    (assoc :store/items [product]))
          user (assoc (user-test) :user/cart (cf/add-tempid {:user.cart/items (:store.item/skus product)}))
          conn (new-db [store user])
          db-store (db/pull (db/db conn) [:db/id] [:store/uuid (:store/uuid store)])
          db-sku (db/pull-one-with (db/db conn) [:db/id {:store.item/_skus [:store.item/price]}] {:where '[[_ :store.item/skus ?e]]})
          db-user (db/pull (db/db conn) [:db/id] [:user/email (:user/email user)])

          ;; Prepare parameters for order creation
          stripe-chan (async/chan 1)
          order-params {:items        [db-sku]
                        :shipping     {:shipping/address {:shipping.address/country "se"}}
                        :source       "payment-source"
                        :subtotal     0
                        :shipping-rate {:amount 0 :title "title" :description "desc"}}
          ;; Create new order
          new-order (api/create-order {:state  conn
                                       :system {:system/stripe (stripe-test-payment-succeeded stripe-chan)}
                                       :auth   {:user-id (:db/id db-user)}}
                                      (:db/id db-store)
                                      order-params)]

      (let [new-db-store (db/pull (db/db conn) [:db/id :order/_store] [:store/uuid (:store/uuid store)])
            new-db-user (db/pull (db/db conn) [:db/id :order/_user] [:user/email (:user/email user)])
            db-order (db/pull (db/db conn) [:db/id :order/status :order/uuid] (:db/id new-order))
            stripe-params (async/poll! stripe-chan)]

        ;; Verify
        (is (= (:order/status db-order) :order.status/paid))
        (is (= (:source stripe-params) (:source order-params))) ;; Verify Stripe was called with the source
        (is (= (get-in stripe-params [:metadata :order_uuid]) (:order/uuid db-order))) ; Verify Stripe was called with the order UUID in the metadata
        (is (= 1 (count (:order/_store new-db-store)) (count (:order/_user new-db-user)))))))) ;; Verify our user has one order

(deftest create-order-payment-failed
  (testing "Creating order should call Stripe and create a charge, charge failed should set order status to created."
    (let [;; Prepare DB with products to order
          product (-> (f/product {:store.item/name "product" :store.item/price "10"})
                      (assoc :store.item/skus [(f/sku {:store.item.sku/variation "sku"})]))
          store (-> (store-test)
                    (assoc :store/items [product]))
          user (assoc (user-test) :user/cart (cf/add-tempid {:user.cart/items (:store.item/skus product)}))
          conn (new-db [store user])
          db-store (db/pull (db/db conn) [:db/id] [:store/uuid (:store/uuid store)])
          db-sku (db/pull-one-with (db/db conn) [:db/id {:store.item/_skus [:store.item/price]}] {:where '[[_ :store.item/skus ?e]]})
          db-user (db/pull (db/db conn) [:db/id] [:user/email (:user/email user)])

          ;; Prepare parameters for order creation
          stripe-chan (async/chan 1)
          order-params {:items    [db-sku]
                        :shipping {:shipping/address {:shipping.address/country "se"}}
                        :source   "payment-source"
                        :subtotal 0 :shipping-rate {:amount 0 :title "title" :description "desc"}}
          ;; Create new order
          new-order (api/create-order {:state  conn
                                       :system {:system/stripe (stripe-test-payment-failed stripe-chan)}
                                       :auth   {:user-id (:db/id db-user)}}
                                      (:db/id db-store)
                                      order-params)]

      (let [new-db-store (db/pull (db/db conn) [:db/id :order/_store] [:store/uuid (:store/uuid store)])
            new-db-user (db/pull (db/db conn) [:db/id :order/_user] [:user/email (:user/email user)])
            db-order (db/pull (db/db conn) [:db/id :order/status :order/uuid] (:db/id new-order))
            stripe-params (async/poll! stripe-chan)]

        ;; Verify
        (is (= (:order/status db-order) :order.status/created))
        (is (= (:source stripe-params) (:source order-params)))
        (is (= (get-in stripe-params [:metadata :order_uuid]) (:order/uuid db-order)))
        (is (= 1 (count (:order/_store new-db-store)) (count (:order/_user new-db-user)))))))) ;; Verify our user has one order

;; ############################ UPDATE ###################

(defn order-with-status [store status]
  {:db/id        (db/tempid :db.part/user)
   :order/uuid   (db/squuid)
   :order/status status
   :order/store  store})

(deftest update-transition-failed
  (testing "Updating the order with a transition not allowed, should throw error."
    (let [;; Prepare DB with products to order
          store (store-test)
          order-created (order-with-status store :order.status/created)
          order-paid (order-with-status store :order.status/paid)
          order-fulfilled (order-with-status store :order.status/fulfilled)
          order-returned (order-with-status store :order.status/returned)
          order-canceled (order-with-status store :order.status/canceled)

          conn (new-db [order-created order-paid order-fulfilled order-returned order-canceled])
          db-store (db/pull (db/db conn) [:db/id] [:store/uuid (:store/uuid store)])
          db-order-created (db/lookup-entity (db/db conn) [:order/uuid (:order/uuid order-created)])
          db-order-paid (db/lookup-entity (db/db conn) [:order/uuid (:order/uuid order-paid)])
          db-order-fulfilled (db/lookup-entity (db/db conn) [:order/uuid (:order/uuid order-fulfilled)])
          db-order-returned (db/lookup-entity (db/db conn) [:order/uuid (:order/uuid order-returned)])
          db-order-canceled (db/lookup-entity (db/db conn) [:order/uuid (:order/uuid order-canceled)])]

      ;; Verify transitions not allowed throw an exception.
      (are [o s] (thrown-with-msg? ExceptionInfo #"Order status transition not allowed"
                                   (api/update-order {:state conn} (:db/id db-store) (:db/id o) {:order-status s}))
                 db-order-created :order.status/fulfilled
                 db-order-created :order.status/returned

                 db-order-paid :order.status/created
                 db-order-paid :order.status/canceled

                 db-order-fulfilled :order.status/created
                 db-order-fulfilled :order.status/paid
                 db-order-fulfilled :order.status/canceled

                 db-order-returned :order.status/created
                 db-order-returned :order.status/paid
                 db-order-returned :order.status/fulfilled
                 db-order-returned :order.status/canceled

                 db-order-canceled :order.status/created
                 db-order-canceled :order.status/paid
                 db-order-canceled :order.status/fulfilled
                 db-order-canceled :order.status/returned))))

(defn order-charged-with-status [store status]
  (-> (order-with-status store status)
      (assoc :order/charge {:charge/id "charge"})))

(deftest update-order-return-cancel-did-create-refund
  (testing "Updating the order with a transition to returned or canceled, should create a reefund in Stripe."
    (let [;; Prepare DB with products to order
          store (store-test)
          order-paid (order-charged-with-status store :order.status/paid)
          order-fulfilled (order-charged-with-status store :order.status/fulfilled)

          conn (new-db [order-paid order-fulfilled])
          stripe-chan-paid (async/chan 1)
          stripe-chan-fulfilled (async/chan 1)
          db-store (db/pull (db/db conn) [:db/id] [:store/uuid (:store/uuid store)])
          db-order-paid (db/lookup-entity (db/db conn) [:order/uuid (:order/uuid order-paid)])
          db-order-fulfilled (db/lookup-entity (db/db conn) [:order/uuid (:order/uuid order-fulfilled)])]
      (api/update-order {:state  conn
                         :system {:system/stripe (stripe-test-payment-succeeded stripe-chan-paid)}}
                        (:db/id db-store)
                        (:db/id db-order-paid)
                        {:order/status :order.status/canceled})
      (api/update-order {:state  conn
                         :system {:system/stripe (stripe-test-payment-succeeded stripe-chan-fulfilled)}}
                        (:db/id db-store)
                        (:db/id db-order-fulfilled)
                        {:order/status :order.status/returned})

      ;; Verify transitions not allowed throw an exception.
      (is (= (:charge (async/poll! stripe-chan-paid)) "charge"))
      (is (= (:charge (async/poll! stripe-chan-fulfilled)) "charge")))))