(ns eponai.server.parser.read
  (:require
    [eponai.common.database :as db]
    [eponai.common.datascript :as eponai.datascript]
    [eponai.common.parser :as parser :refer [server-read read-basis-param-path]]
    [eponai.common.parser.read :as common.read]
    [eponai.server.datomic.query :as query]
    [clojure.data.generators :as gen]
    [datomic.api :as d]
    [taoensso.timbre :refer [error debug trace warn]])
  (:import [java.util Random]))

(defn db-shuffle
  "Shuffle items consistently with the db, meaing
  with the same db basis-t you get the same items."
  [db items]
  (binding [gen/*rnd* (Random. (d/basis-t db))]
    (gen/shuffle items)))

(defn is-authenticated? [auth]
  (boolean (not-empty auth)))

(defmethod server-read :datascript/schema
  [{:keys [db db-history]} _ _]
  {:value (-> (query/schema db db-history)
              (eponai.datascript/schema-datomic->datascript))})

(defmethod server-read :query/cart
  [{:keys [db db-history query params auth]} _ _]
  (let [cart (query/one db db-history query {:where '[[?e :cart/items]]})]
    (debug "Read query/cart: " auth)
    {:value (when (is-authenticated? auth)
              (cond-> cart
                      (nil? db-history)
                      (-> (update :cart/items #(seq (map (comp (partial d/entity db) :db/id) %)))
                          (common.read/compute-cart-price))))}))

(defmethod read-basis-param-path :query/store [{:keys [params]} _ _] [(:store-id params)])
(defmethod server-read :query/store
  [{:keys [db db-history query params auth]} _ p]
  (debug "AUTH: " auth)
  (let [store-id (or (:store-id params) (:store-id p))]
    (when-let [store-id (cond-> store-id (string? store-id) (Long/parseLong))]
      {:value (cond-> (query/one db db-history query {:where   '[[?e]]
                                                      :symbols {'?e store-id}})
                      (nil? db-history)
                      (common.read/multiply-store-items))})))

(defmethod server-read :query/featured-stores
  [{:keys [db db-history query]} _ _]
  ;; Only fetch featured-stores initially? i.e. (when (nil? db-history) ...)
  ;; TODO: Come up with a way to feature stores.
  (let [feautred-stores (->> (db/all-with db {:where '[[?e :store/name]]})
                             (db-shuffle db)
                             (take 4))
        photos-fn (fn [store]
                    (let [s (d/entity db store)
                          [img-1 img-2] (into [] (comp (take 2) (map :item/img-src)) (db-shuffle db (:item/_store s)))]
                      {:db/id store
                       :store/featured-img-src [img-1 (:store/photo s) img-2]}))]
    {:value (into [] (comp (map photos-fn)
                           (map #(merge % (db/pull db query (:db/id %)))))
                  feautred-stores)}))

(defmethod server-read :query/all-items
  [{:keys [db db-history query]} _ _]
  {:value (query/all db db-history query {:where '[[?e :item/id]]})})

(defmethod server-read :query/featured-items
  [{:keys [db query]} _ _]
  (let [items (->> (db/all-with db {:where '[[?e :item/id]]})
                   (db-shuffle db)
                   (take 4))]
    {:value (db/pull-many db query items)}))

(defmethod read-basis-param-path :query/store [{:keys [params]} _ _] [(:product-id params)])
(defmethod server-read :query/item
  [{:keys [db db-history query params]} _ _]
  (let [{:keys [product-id]} params]
    {:value (query/one db db-history query
                       {:where   '[[?e :item/id ?item-id]]
                        :symbols {'?item-id product-id}})}))

(defmethod server-read :query/featured-streams
  [{:keys [db query]} _ _]
  {:value (->> (db/all-with db {:where '[[?e :stream/store]]})
               (db-shuffle db)
               (db/pull-many db query))})

(defmethod server-read :query/auth
  [{:keys [auth]} _ _]
  (debug "Read query/auth: " auth)
  {:value auth})
