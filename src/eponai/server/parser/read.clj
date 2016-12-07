(ns eponai.server.parser.read
  (:require
    [eponai.common.test-data :as td]
    [eponai.common.datascript :as eponai.datascript]
    [eponai.common.parser :refer [server-read]]
    [eponai.server.datomic.query :as query]
    [taoensso.timbre :refer [error debug trace warn]]))

(def data td/mocked-data)
(def stores (:stores data))

(defmethod server-read :datascript/schema
  [{:keys [db db-history]} _ _]
  {:value (-> (query/schema db db-history)
              (eponai.datascript/schema-datomic->datascript))})

(defmethod server-read :query/cart
  [{:keys [query params]} _ _]
  (let [cart {:cart/price 103
              :cart/items (:store/goods (first stores))}]
    (prn "READING cart: " cart)
    {:value (select-keys cart query)}))

(defmethod server-read :query/store
  [{:keys [query params]} _ _]
  (let [{:keys [store-id]} params]
    (let [store (some #(when (= (Long/parseLong store-id) (:store/id %))
                        %) stores)]
      {:value (-> (select-keys store (conj (filter keyword? query) :store/goods))
                  (update :store/goods
                          #(apply concat (take 4 (repeat %)))))})))

(defmethod server-read :query/featured-stores
  [{:keys [query]} _ _]
  (let [featured-stores (take 4 (shuffle stores))
        photos-fn (fn [s]
                    (let [[img-1 img-2] (take 2 (shuffle (map :item/img-src (:store/goods s))))]
                      (assoc s :store/featured-img-src [img-1 (:store/photo s) img-2])))
        xf (comp (map photos-fn) (map #(select-keys % query)))]
    {:value (transduce xf conj [] featured-stores)}))

(defmethod server-read :query/all-items
  [{:keys [query]} _ _]
  (prn "query/all-items: " query)
  (let [goods (map #(select-keys % query) (mapcat :store/goods stores))
        xf (comp (mapcat :store/goods) (map #(select-keys % query)))]

    (prn "Got goods: " goods)
    {:value (transduce xf conj [] stores)}))

(defmethod server-read :query/featured-items
  [{:keys [query]} _ _]
  (prn "query/all-items: " query)
  (let [goods (map #(select-keys % query) (mapcat :store/goods stores))
        xf (comp (mapcat :store/goods) (map #(select-keys % query)))]

    (prn "Got goods: " goods)
    {:value (map #(select-keys % query) (take 4 (shuffle (transduce xf conj [] stores))))}))

(defmethod server-read :query/item
  [{:keys [query params]} _ _]
  (let [{:keys [product-id]} params]
    (prn "Read query/item: " product-id)
    (let [all-items (mapcat :store/goods stores)
          product (some #(when (= product-id (:item/id %)) %) all-items)
          store (some #(when (some #{product-id} (mapv :item/id (:store/goods %)))
                        %) stores)]
      {:value (select-keys (assoc product :item/store store) query)})))

(defmethod server-read :query/featured-streams
  [{:keys [query]} _ _]
  {:value (map #(select-keys % query)
               (shuffle (map (fn [s] (update s :stream/store #(get stores %))) (:streams data))))})