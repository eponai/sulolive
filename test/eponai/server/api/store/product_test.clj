(ns eponai.server.api.store.product-test
  (:require
    [clojure.test :refer :all]
    [eponai.common.database :as db]
    [clojure.core.async :as async]
    [eponai.server.api.store.test-util :refer [store-test s3-test cloudinary-test]]
    [eponai.server.test-util :refer [new-db]]
    [eponai.server.api.store :as store]
    [eponai.server.datomic.format :as f]))

(def photo-service-key :system/cloudinary)
(defn photo-service [chan]
  (cloudinary-test chan))
(defn photo-service-input [url]
  {:public_id     url
   :url           url
   :resource_type "image"})


;; ######################## CREATE #########################

(deftest create-product-no-image-test
  (testing "Create product with no photo should not add photo entity."
    (let [;; Prepare existing data, store to be updated
          new-store (store-test)
          conn (new-db [new-store])

          ;; Fetch existing data from test DB
          db-store (db/pull (db/db conn) [:db/id] [:store/uuid (:store/uuid new-store)])

          ;; Prepare data for creating new products
          params {:store.item/name "product" :store.item/price "10" :store.item/skus [{:store.item.sku/variation "variation"}]}
          s3-chan (async/chan 1)]
      (store/create-product {:state conn :system {photo-service-key (photo-service s3-chan)}} (:db/id db-store) params)
      (let [result-db (db/db conn)
            ;; Pull new data after creation
            new-db-store (db/pull result-db [:db/id {:store/items [:store.item/name :store.item/photos :store.item/skus]}] (:db/id db-store))
            db-product (first (get new-db-store :store/items))]

        ;; Verify
        (is (= 1 (count (:store/items new-db-store)) (count (:store.item/skus db-product)))) ;;Verify that our store has one product
        (is (= (:store.item/name db-product) (:store.item/name params))) ;;Verify that the store's item is the same as the newly created product
        (is (nil? (async/poll! s3-chan)))                   ;;Verify that S3 was called with the photo we wanted to upload
        (is (empty? (get db-product :store.item/photos))))))) ;; Verify that no photo entities were created for the product

(deftest create-product-with-image-test
  (testing "Create product with one photo should add one photo entity to that product."
    (let [;; Prepare existing data, store to be updated
          store (store-test)
          conn (new-db [store])

          ;; Fetch existing data from test DB
          db-store (db/pull (db/db conn) [:db/id] [:store/uuid (:store/uuid store)])

          ;; Prepare data for creating new products
          params {:store.item/name "product" :store.item/photos [(photo-service-input "someurl")] :store.item/price "10"}
          s3-chan (async/chan 1)]
      (store/create-product {:state conn :system {photo-service-key (photo-service s3-chan)}} (:db/id db-store) params)
      (let [result-db (db/db conn)
            ;; Pull new data after creation
            new-db-store (db/pull result-db [:db/id {:store/items [:store.item/name
                                                                   {:store.item/photos [{:store.item.photo/photo [:db/id :photo/path]}]}]}] (:db/id db-store))
            db-product (first (get new-db-store :store/items))
            item-photos (get db-product :store.item/photos)]

        ;; Verify
        (is (= 1 (count (:store/items new-db-store))))      ;;Verify that our store has one product
        (is (= (:store.item/name db-product) (:store.item/name params))) ;;Verify that the store's item is the same as the newly created product
        (is (= 1 (count item-photos)))                      ;; Verify that we now have a photo entity for the product in the DB
        (is (= (async/poll! s3-chan)
               (get-in (first item-photos) [:store.item.photo/photo :photo/path]))) ;;Verify that S3 was called with the photo we wanted to upload
        ))))

(deftest create-product-with-multiple-images
  (testing "Create product with multiple photos should add all photo entities to that product."
    (let [;; Prepare existing data, store to be updated
          store (store-test)
          conn (new-db [store])

          ;; Fetch existing data from test DB
          db-store (db/pull (db/db conn) [:db/id] [:store/uuid (:store/uuid store)])

          ;; Prepare data for creating new products
          params {:store.item/name "product" :store.item/photos [(photo-service-input "first-photo")
                                                                 (photo-service-input "second-photo")] :store.item/price "10"}
          s3-chan (async/chan 1)]
      (store/create-product {:state conn :system {photo-service-key (photo-service s3-chan)}} (:db/id db-store) params)
      (let [result-db (db/db conn)
            ;; Pull new data after creation
            new-db-store (db/pull result-db [:db/id {:store/items [:store.item/name
                                                                   {:store.item/photos [{:store.item.photo/photo [:db/id :photo/path]}]}]}] (:db/id db-store))
            db-product (first (get new-db-store :store/items))
            item-photos (get db-product :store.item/photos)]

        ;; Verify
        (is (= 1 (count (:store/items new-db-store))))      ;;Verify that our store has one product
        (is (= (:store.item/name db-product) (:store.item/name params))) ;;Verify that the store's item is the same as the newly created product
        (is (= 2 (count item-photos)))                      ;; Verify that the new product has two photos
        (is (= (async/poll! s3-chan)
               (get-in (first item-photos) [:store.item.photo/photo :photo/path]))) ;;Verify that S3 was called with the photo we wanted to upload
        ))))

;; ######################### UPDATE ############################

(deftest update-product-with-skus-add-more-skus
  (testing "Update a a product with an SKU and add more SKUs, an sku should be created and added to the product."
    (let [;; Prepare existing data to be updated
          old-sku (f/sku {:store.item.sku/variation "variation"})
          old-product (assoc (f/product {:store.item/name "product" :store.item/uuid (db/squuid)}) :store.item/skus [old-sku])

          ;; Fetch existing data from our test DB
          conn (new-db [(assoc (store-test) :store/items [old-product])])
          db-product (db/pull (db/db conn) [:db/id {:store.item/skus [:db/id]}] [:store.item/uuid (:store.item/uuid old-product)])

          ;; Prepare our update parameters
          new-params {:store.item/name "product-updated" :store.item/skus [old-sku
                                                                           {:store.item.sku/variation "variation-2"}]}]
      (store/update-product {:state conn} (:db/id db-product) new-params)
      (let [result-db (db/db conn)
            ;; Pull new data after update
            new-db-product (db/pull result-db [:db/id :store.item/name :store.item/skus] (:db/id db-product))
            all-skus (db/all-with result-db {:where '[[?e :store.item.sku/variation]]})]

        ;; Verify
        (is (= (:store.item/name new-db-product) (:store.item/name new-params))) ;; Verify that the name of the updated entity matches our parameters
        (is (= 2 (count (get new-db-product :store.item/skus)))) ;; Verify that no new photo entities were created for product
        (is (= 2 (count all-skus))))                        ;; Verify that we replaced the old photo entities and not created new ones.
      )))

(deftest update-product-with-skus-remove-skus
  (testing "Update a a product with an SKU and remove some SKUs, they should be retracted from DB and removed from product."
    (let [;; Prepare existing data to be updated
          old-skus [(f/sku {:store.item.sku/variation "variation-1"})
                    (f/sku {:store.item.sku/variation "variation-2"})]
          old-product (assoc (f/product {:store.item/name "product" :store.item/uuid (db/squuid)}) :store.item/skus old-skus)
          store (assoc (store-test) :store/items [old-product])

          ;; Fetch existing data from our test DB
          conn (new-db [store])
          db-product (db/pull (db/db conn) [:db/id {:store.item/skus [:db/id :store.item.sku/variation]}] [:store.item/uuid (:store.item/uuid old-product)])
          db-sku (first (:store.item/skus db-product))

          ;; Prepare our update parameters, add only one SKU (i.e. the other one should be removed)
          new-params {:store.item/name "product-updated" :store.item/skus [db-sku]}]
      (store/update-product {:state conn} (:db/id db-product) new-params)
      (let [result-db (db/db conn)
            ;; Pull new data after update
            new-db-product (db/pull result-db [:db/id :store.item/name :store.item/skus] (:db/id db-product))
            all-skus (db/all-with result-db {:where '[[?e :store.item.sku/variation]]})]

        ;; Verify
        (is (= (:store.item/name new-db-product) (:store.item/name new-params))) ;; Verify that the name of the updated entity matches our parameters
        (is (= 1 (count (get new-db-product :store.item/skus)))) ;; Verify that our product has only one SKU left
        (is (= 1 (count all-skus))))                        ;; Verify that we removed one SKU entity from the DB entirely.
      )))


;; ################### Photos ##################
(deftest update-product-with-one-image-replace-that-image
  (testing "Update a product that has an image with one new image, that image should be replaced."
    (let [;; Prepare existing data to be updated
          old-photo (f/item-photo {:photo/path "old-photo"
                                   :photo/id "old-photo"} 0)
          old-product (assoc (f/product {:store.item/name "product" :store.item/uuid (db/squuid)}) :store.item/photos [old-photo])
          store (assoc (store-test) :store/items [old-product])

          ;; Fetch existing data from our test DB
          conn (new-db [store])
          db-product (db/pull (db/db conn) [:db/id :store.item/photos] [:store.item/uuid (:store.item/uuid old-product)])

          ;; Prepare our update parameters
          new-params {:store.item/name "product-updated" :store.item/photos [(photo-service-input "someurl")]}
          s3-chan (async/chan 1)]
      (store/update-product {:state conn :system {photo-service-key (photo-service s3-chan)}} (:db/id db-product) new-params)
      (let [result-db (db/db conn)
            ;; Pull new data after update
            new-db-product (db/pull result-db [:db/id :store.item/name {:store.item/photos [:db/id {:store.item.photo/photo [:db/id :photo/path]}]}] (:db/id db-product))
            new-db-photo (first (get new-db-product :store.item/photos))
            all-photos (db/all-with result-db {:where '[[?e :photo/path]]})
            all-product-photos (db/all-with result-db {:where '[[?e :store.item.photo/photo]]})]

        ;; Verify
        (is new-db-photo)                                   ;;Verify photo entity exists
        (is (= (async/poll! s3-chan) (get-in new-db-photo [:store.item.photo/photo :photo/path]))) ;; Verify that S3 was called with updated photo
        (is (= 1
               (count (get new-db-product :store.item/photos))
               (count (get db-product :store.item/photos)))) ;; Verify that no new photo entities were created for product
        (is (= 1 (count all-photos) (count all-product-photos)))) ;; Verify that we replaced the old photo entity, and not created new ones in the DB.
      )))

(deftest update-product-with-image-replace-multiple-images
  (testing "Update a product that has an image with multiple images, the one should be replaced and the other added."
    (let [;; Prepare existing data to be updated
          old-photo (f/item-photo {:photo/path "old-photo"
                                   :photo/id "old-photo"} 0)
          old-product (assoc (f/product {:store.item/name "product" :store.item/uuid (db/squuid)}) :store.item/photos [old-photo])
          store (assoc (store-test) :store/items [old-product])

          ;; Fetch existing data from our test DB
          conn (new-db [store])
          db-product (db/pull (db/db conn) [:db/id {:store.item/photos [:db/id :photo/path]}] [:store.item/uuid (:store.item/uuid old-product)])

          ;; Prepare our update parameters
          new-params {:store.item/name "product-updated" :store.item/photos [(photo-service-input "newfirst") ; Should replace the old photo
                                                                             (photo-service-input "newsecond")]}
          s3-chan (async/chan 2)]
      (store/update-product {:state conn :system {photo-service-key (photo-service s3-chan)}} (:db/id db-product) new-params)
      (let [result-db (db/db conn)
            ;; Pull new data after update
            new-db-product (db/pull result-db [:db/id :store.item/name {:store.item/photos [:db/id {:store.item.photo/photo [:db/id :photo/path]} :store.item.photo/index]}] (:db/id db-product))
            new-db-photos (get new-db-product :store.item/photos)
            all-photos (db/all-with result-db {:where '[[?e :photo/path]]})
            all-product-photos (db/all-with result-db {:where '[[?e :store.item.photo/photo]]})
            sorted-new-photos (sort-by :store.item.photo/index new-db-photos)]

        ;; Verify
        (is (= (get-in (first sorted-new-photos) [:store.item.photo/photo :photo/path]) "newfirst")) ; Verify the first photo is now replaced
        (is (= (get-in (second sorted-new-photos) [:store.item.photo/photo :photo/path]) "newsecond")) ; Verify the second photo exists
        (is (= (async/poll! s3-chan) (get-in (first sorted-new-photos) [:store.item.photo/photo :photo/path]))) ; Verify the first new photo was passed to S3
        (is (= (async/poll! s3-chan) (get-in (second sorted-new-photos) [:store.item.photo/photo :photo/path]))) ; Verify the second new photo was passed to S3
        (is (= (:store.item/name new-db-product) (:store.item/name new-params))) ;; Verify that the name of the updated entity matches our parameters
        (is (= 2 (count new-db-photos) (count all-photos) (count all-product-photos)))) ;; Verify that we replaced the old photo entity and created a new one.
      )))

(deftest update-product-with-image-add-new-images
  (testing "Update a product that has an image with a new image, the existing photo should remain the same, and the new one should be created."
    (let [;; Prepare existing data to be updated
          old-photo (f/item-photo {:photo/path "old-photo"
                                   :photo/id "old-photo"} 0)
          old-product (assoc (f/product {:store.item/name "product" :store.item/uuid (db/squuid)}) :store.item/photos [old-photo])
          store (assoc (store-test) :store/items [old-product])

          ;; Fetch existing data from our test DB
          conn (new-db [store])
          db-product (db/pull (db/db conn) [:db/id {:store.item/photos [{:store.item.photo/photo [:db/id :photo/path]}
                                                                        :store.item.photo/index
                                                                        :db/id]}] [:store.item/uuid (:store.item/uuid old-product)])
          db-photo (first (:store.item/photos db-product))

          ;; Prepare our update parameters
          new-params {:store.item/name "product-updated" :store.item/photos [db-photo ;Pass in the old photo again, should not be uploaded to S3
                                                                             (photo-service-input "newsecond")]}
          s3-chan (async/chan 2)]
      (store/update-product {:state conn :system {photo-service-key (photo-service s3-chan)}} (:db/id db-product) new-params)
      (let [result-db (db/db conn)
            ;; Pull new data after update
            new-db-product (db/pull result-db [:db/id :store.item/name {:store.item/photos [:db/id {:store.item.photo/photo [:db/id :photo/path]} :store.item.photo/index]}] (:db/id db-product))
            new-db-photos (get new-db-product :store.item/photos)
            all-photos (db/all-with result-db {:where '[[?e :photo/path]]})
            all-product-photos (db/all-with result-db {:where '[[?e :store.item.photo/photo]]})
            sorted-new-photos (sort-by :store.item.photo/index new-db-photos)]

        ;; Verify
        (is (= (get-in (first sorted-new-photos) [:store.item.photo/photo :photo/path]) "old-photo"))
        (is (= (get-in (second sorted-new-photos) [:store.item.photo/photo :photo/path]) "newsecond")) ; Verify the order is correct with the photos
        (is (= (async/poll! s3-chan) "newsecond"))          ; Verify that our first upload is the new photo (the existing photo doesn't need to be uploaded)
        (is (= 2 (count new-db-photos) (count all-photos) (count all-product-photos)))) ;; Verify that we have 2 photos on this item, and in the entire DB.
      )))

(deftest update-product-with-images-remove-images
  (testing "Update a product that has an image with fewer images, thos not added again should be removed from DB and the product."
    (let [;; Prepare existing data to be updated
          old-photos [(f/item-photo {:photo/path "old-photo"
                                     :photo/id "old-photo"} 0) (f/item-photo {:photo/path "old-two"
                                                                                                    :photo/id "old-two"} 1)]
          old-product (assoc (f/product {:store.item/name "product" :store.item/uuid (db/squuid)}) :store.item/photos old-photos)
          store (assoc (store-test) :store/items [old-product])

          ;; Fetch existing data from our test DB
          conn (new-db [store])
          db-product (db/pull (db/db conn) [:db/id {:store.item/photos [{:store.item.photo/photo [:photo/path]}
                                                                        :store.item.photo/index
                                                                        :db/id]}] [:store.item/uuid (:store.item/uuid old-product)])

          ;; Prepare our update parameters
          new-params {:store.item/name "product-updated" :store.item/photos [(photo-service-input "new-one")]}
          s3-chan (async/chan 2)]
      (store/update-product {:state conn :system {photo-service-key (photo-service s3-chan)}} (:db/id db-product) new-params)
      (let [result-db (db/db conn)
            ;; Pull new data after update
            new-db-product (db/pull result-db [:db/id :store.item/name {:store.item/photos [:db/id {:store.item.photo/photo [:db/id :photo/path]} :store.item.photo/index]}] (:db/id db-product))
            new-db-photos (get new-db-product :store.item/photos)
            all-photos (db/all-with result-db {:where '[[?e :photo/path]]})
            all-product-photos (db/all-with result-db {:where '[[?e :store.item.photo/photo]]})
            sorted-new-photos (sort-by :store.item.photo/index new-db-photos)]

        ;; Verify
        (is (= 2 (count (:store.item/photos db-product))))
        (is (= (get-in (first sorted-new-photos) [:store.item.photo/photo :photo/path]) "new-one"))
        (is (= (async/poll! s3-chan) (get-in (first sorted-new-photos) [:store.item.photo/photo :photo/path])))
        (is (= (:store.item/name new-db-product) (:store.item/name new-params))) ;; Verify that the name of the updated entity matches our parameters
        (is (= 1 (count new-db-photos) (count all-photos) (count all-product-photos)))) ;; Verify that we replaced the old photo entities and not created new ones.
      )))

(deftest update-product-with-image-remove-all-images
  (testing "Update a product that has an image with a new image, photo entity and other attributes should be updated."
    (let [;; Prepare existing data to be updated
          old-photos [(f/item-photo {:photo/path "old-photo"
                                     :photo/id "old-photo"} 0) (f/item-photo {:photo/path "old-photo"
                                                                                                    :photo/id "old-photo"} 1)]
          old-product (assoc (f/product {:store.item/name "product" :store.item/uuid (db/squuid)}) :store.item/photos old-photos)
          store (assoc (store-test) :store/items [old-product])

          ;; Fetch existing data from our test DB
          conn (new-db [store])
          db-product (db/pull (db/db conn) [:db/id {:store.item/photos [{:store.item.photo/photo [:photo/path]}
                                                                        :store.item.photo/index
                                                                        :db/id]}] [:store.item/uuid (:store.item/uuid old-product)])
          ;; Prepare our update parameters
          new-params {:store.item/name "product-updated" :store.item/photos []}]
      (store/update-product {:state conn} (:db/id db-product) new-params)
      (let [result-db (db/db conn)
            ;; Pull new data after update
            new-db-product (db/pull result-db [:db/id :store.item/name {:store.item/photos [:db/id {:store.item.photo/photo [:db/id :photo/path]} :store.item.photo/index]}] (:db/id db-product))
            new-db-photos (get new-db-product :store.item/photos)
            all-photos (db/all-with result-db {:where '[[?e :photo/path]]})
            all-product-photos (db/all-with result-db {:where '[[?e :store.item.photo/photo]]})
            sorted-new-photos (sort-by :store.item.photo/index new-db-photos)]

        ;; Verify there's no photos in the DB or at the product anymore
        (is (= 0 (count new-db-photos) (count all-photos) (count all-product-photos)))))))

(deftest update-product-with-image-no-new-image
  (testing "Update a product that has an image with only the old images, nothing should be uploaded, photo entities should stay the same."
    (let [
          ;; Prepare existing data to be updated
          old-photo (f/item-photo {:photo/path "old-photo"
                                   :photo/id "old-photo"} 0)
          old-product (assoc (f/product {:store.item/name "product" :store.item/uuid (db/squuid)}) :store.item/photos [old-photo])
          store (assoc (store-test) :store/items [old-product])

          ;; Fetch existing data from our test DB
          conn (new-db [store])
          db-product (db/pull (db/db conn) [:db/id {:store.item/photos [{:store.item.photo/photo [:db/id :photo/path]}
                                                                        :store.item.photo/index
                                                                        :db/id]}] [:store.item/uuid (:store.item/uuid old-product)])
          db-photo (first (get db-product :store.item/photos))

          ;; Prepare our update parameters
          new-params {:store.item/name "product-updated" :store.item/photos [db-photo]}
          s3-chan (async/chan 1)]
      (store/update-product {:state conn :system {photo-service-key (photo-service s3-chan)}} (:db/id db-product) new-params)
      (let [result-db (db/db conn)
            ;; Pull new data after update
            new-db-product (db/pull result-db [:db/id :store.item/name {:store.item/photos [:db/id :photo/path]}] (:db/id db-product))
            new-db-photo (first (get new-db-product :store.item/photos))]

        ;; Verify
        (is (nil? (async/poll! s3-chan)))                   ;; Verify that S3 was not called since we didn't have a photo
        (is (= (:db/id db-photo) (:db/id new-db-photo)))    ;; Verify that we updated an existing photo and not created a new one
        (is (= (:photo/path db-photo) (:photo/path new-db-photo))) ;; Verify that the path is still the same in photo entity
        (is (= 1
               (count (get new-db-product :store.item/photos))
               (count (get db-product :store.item/photos))))) ;; Verify that no new photo entities were created for product
      )))

;; ################################################# DELETE ####################################################
(deftest delete-product
  (testing "Delete product, entity should be retracted."
    (let [
          ;; Prepare existing data to be deleted
          old-product (f/product {:store.item/name "product" :store.item/uuid (db/squuid)})
          store (assoc (store-test) :store/items [old-product])

          ;; Pull existing data from our test DB
          conn (new-db [store])
          db-store (db/pull (db/db conn) [:db/id :store/items] [:store/uuid (:store/uuid store)])
          db-product (db/pull (db/db conn) [:db/id] [:store.item/uuid (:store.item/uuid old-product)])

          ;; Prepare our delete
          result (store/delete-product {:state conn}
                                       (:db/id db-product))

          ;; Pull new data after delete
          new-db-store (db/pull (:db-after result) [:store/items] (:db/id db-store))
          new-db-product (db/pull (:db-after result) [:db/id] [:store.item/uuid (:store.item/uuid old-product)])]

      ;; Verify
      (is (= 1 (count (:store/items db-store))))
      (is (:store.item/uuid old-product))
      (is (nil? new-db-product))
      (is (empty? (:store/items new-db-store)))             ;; Verify that our store has no products anymore
      )))

(deftest delete-product-with-image
  (testing "Delete product, entity should be retracted."
    (let [
          ;; Prepare existing data to be deleted
          old-photo (f/photo "old-photo")
          old-product (assoc (f/product {:store.item/name "product" :store.item/uuid (db/squuid)}) :store.item/photos [old-photo])
          store (assoc (store-test) :store/items [old-product])

          ;; Pull existing data from our test DB
          conn (new-db [store])
          db-store (db/pull (db/db conn) [:db/id :store/items] [:store/uuid (:store/uuid store)])
          db-product (db/pull (db/db conn) [:db/id] [:store.item/uuid (:store.item/uuid old-product)])
          db-photo (db/one-with (db/db conn) {:where   '[[?e :photo/path ?p]]
                                              :symbols {'?p "old-photo"}})

          ;; Prepare our delete
          result (store/delete-product {:state conn}
                                       (:db/id db-product))

          ;; Pull new data after delete
          new-db-store (db/pull (:db-after result) [:store/items] (:db/id db-store))
          new-db-product (db/pull (:db-after result) [:db/id] [:store.item/uuid (:store.item/uuid old-product)])
          new-db-photo (db/one-with (:db-after result) {:where   '[[?e :photo/path ?p]]
                                                        :symbols {'?p "old-photo"}})]

      ;; Verify
      (is db-photo)
      (is (= 1 (count (:store/items db-store))))            ;;Verify our store started with one product
      (is (nil? new-db-product))                            ;;Verify that product was retracted from DB
      (is (nil? new-db-photo))                              ;;Verify that photo entity was retracted from DB
      (is (empty? (:store/items new-db-store)))             ;; Verify that our store has no products anymore
      )))
