(ns eponai.common.browse
  (:require
    [eponai.common.database :as db]
    [eponai.common.database.rules :as db.rules]
    [eponai.common.api.products :as products]
    [medley.core :as medley]
    [eponai.common.format :as format]
    [taoensso.timbre :as timbre :refer [debug]]
    [clojure.string :as string]))

(def default-page-size 25)
(def category-order-values ["newest" "lowest-price" "highest-price"])
(def search-order-values (into ["relevance"] category-order-values))

(defn order-label [order]
  (-> order
      (string/capitalize)
      (string/split #"-")
      (->> (string/join " "))))

(defn order-values [query-params]
  (if (some? (:search query-params))
    search-order-values
    category-order-values))

(defn default-order [query-params]
  (first (order-values query-params)))

(defn- price-where-clause [{:keys [from-price to-price] :as price-range}]
  (condp = [(some? from-price) (some? to-price)]
    [true true] '[(< from-price ?price to-price)]
    [false true] '[(< ?price to-price)]
    [true false] '[(< from-price ?price)]
    nil))

(defn store-item-price-distribution [prices]
  (when-let [[price & prices] (seq prices)]
    (let [[min-price max-price]
          (reduce (fn [[mi ma :as v] price]
                    (-> v
                        (assoc! 0 (min mi price))
                        (assoc! 1 (max ma price))))
                  (transient [price price])
                  prices)]
      {:min min-price
       :max max-price})))

(defn- sort-items [sorting items]
  (let [eid-fn #(nth % 0)
        price-fn #(nth % 1)
        created-at-fn #(nth % 2)
        score-fn #(nth % 3 0)
        ascending compare
        decending #(compare %2 %1)
        [key-fn comparator]
        (condp = sorting
          "lowest-price" [price-fn ascending]
          "highest-price" [price-fn decending]
          "newest" [created-at-fn decending]
          "relevance" [score-fn decending]
          [eid-fn decending])]
    (->> (sort-by key-fn comparator items)
         (mapv eid-fn))))

(defn- namespace-key [namespace k]
  (keyword (cond-> namespace (keyword? namespace) (name))
           (name k)))

(defn- make-result [result {:keys [price-range categories] :as browse-params}]
  (let [{:keys [from-price to-price]} price-range
        {:keys [top-category sub-category sub-sub-category]} categories
        result-params (-> browse-params
                          (dissoc :price-range :page-range :categories)
                          (assoc :from-price from-price :to-price to-price)
                          (assoc :top-category top-category
                                 :sub-category sub-category
                                 :sub-sub-category sub-sub-category)
                          (update :locations select-keys [:db/id])
                          (->> (medley/map-keys #(namespace-key :browse-result %))))
        result (update result :browse-result/items not-empty)]
    (format/remove-nil-keys
      (merge result
             result-params))))


(defn category
  "Returns items and their prices based on selected category"
  [db {:keys [locations categories price-range order] :as browse-params}]
  (debug "Browsing category: " browse-params)
  (let [{:keys [top-category sub-category]} categories
        items-by-cat (cond (some? top-category)
                           (products/find-with-category-names locations (select-keys categories [:top-category]))
                           (some? sub-category)
                           (products/find-with-category-names locations (select-keys categories [:sub-category])))
        price-filter (price-where-clause price-range)
        item-query (cond-> items-by-cat
                           (some? price-filter)
                           (db/merge-query {:where ['[?e :store.item/price ?price]
                                                    price-filter]}))
        items-with-price (db/find-with
                           db
                           (db/merge-query
                             item-query
                             {:find  '[?e ?price ?created-at]
                              :where '[[?e :store.item/price ?price]
                                       [?e :store.item/created-at ?created-at]]}))]

    ;; Pagination can be implemented client side as long as we return all items in the correct order.
    (make-result
      {:browse-result/items  (sort-items order items-with-price)
       :browse-result/prices (store-item-price-distribution (map second items-with-price))}
      browse-params)))

(defn search
  "Remember that we have ?score here."
  [db {:keys [locations search categories price-range order] :as browse-params}]
  (debug "Browsing search: " browse-params)
  (let [items-by-search (products/find-with-search locations search)
        category-filter (when (some some? (vals categories))
                          (db/merge-query
                            (products/category-names-query categories)
                            {:where [(list 'category-or-child-category
                                           (products/smallest-category categories)
                                           '?item-category)
                                     '[?e :store.item/category ?item-category]]
                             :rules [db.rules/category-or-child-category]}))
        price-filter (price-where-clause price-range)
        item-query (cond->> items-by-search
                            (some? price-filter)
                            (db/merge-query {:where ['[?e :store.item/price ?price]
                                                     price-filter]})
                            (some? category-filter)
                            (db/merge-query category-filter))
        items-with-price (db/find-with
                           db
                           (db/merge-query
                             item-query
                             {:find  '[?e ?price ?created-at ?score]
                              :where '[[?e :store.item/price ?price]
                                       [?e :store.item/created-at ?created-at]]}))
        items (sort-items order items-with-price)
        count-by-cat (db/find-with
                       db
                       {:find    '[(clojure.core/frequencies ?c) .]
                        :where   '[[?e :store.item/category ?cat]
                                   (category-or-parent-category ?cat ?c)]
                        ;; Using datomic's :with to avoid it from deduping ?e
                        :with    '[?e]
                        :symbols {'[?e ...] items}
                        :rules   [db.rules/category-or-parent-category]})]
    (make-result
      {:browse-result/items             items
       :browse-result/prices            (store-item-price-distribution
                                          (map second items-with-price))
       :browse-result/count-by-category count-by-cat}
      browse-params)))

(defn find-items
  "Takes browse-params and finds items by their criteria.
  Returns store.item ids in the order decided by :order.
  Returns additional metadata based on how the items were found."
  [db browse-params]
  (if (some? (:search browse-params))
    (search db browse-params)
    (category db browse-params)))

(defn make-browse-params
  "Convenience function for creating params from common maps."
  [locations route-params query-params]
  (let [categories (select-keys route-params [:top-category
                                              :sub-category
                                              :sub-sub-category])
        price-range (select-keys query-params [:from-price :to-price])
        page-range (select-keys query-params [:page-start :page-size])
        {:keys [order search]} query-params]
    {:locations   locations
     :categories  categories
     :price-range price-range
     :search      search
     :order       (or order (default-order query-params))
     :page-range  (-> page-range
                      (update :page-start (fnil identity 0))
                      (update :page-size (fnil identity default-page-size)))}))

;; ----------
;; Datascript

(def browse-datascript-schema
  {:browse-result/locations        {:db/valueType :db.type/ref
                                    :db/index     true}
   :browse-result/top-category     {:db/index true}
   :browse-result/sub-category     {:db/index true}
   :browse-result/sub-sub-category {:db/index true}
   :browse-result/from-price       {:db/index true}
   :browse-result/to-price         {:db/index true}
   :browse-result/order            {:db/index true}
   :browse-result/search           {:db/index true}
   :browse-result/items            {}})

(defn find-result [db browse-params]
  (letfn [(attr-eq? [k v e]
            (= v (get e k)))]
    (let [{:keys [locations categories price-range order search]} browse-params
          {:keys [top-category sub-category sub-sub-category]} categories]
      (->> (db/datoms db :avet :browse-result/locations (:db/id locations))
           (sequence
             (comp
               (map :e)
               (map #(db/entity db %))
               (filter (partial attr-eq? :browse-result/order order))
               (filter (partial attr-eq? :browse-result/search search))
               (filter (partial attr-eq? :browse-result/top-category top-category))
               (filter (partial attr-eq? :browse-result/sub-category sub-category))
               (filter (partial attr-eq? :browse-result/sub-sub-category-category sub-sub-category))
               (filter (partial attr-eq? :browse-result/from-price (:from-price price-range)))
               (filter (partial attr-eq? :browse-result/to-price (:to-price price-range)))
               (take 1)))
           (first)
           (:db/id)))))


(comment
  ;; Can't implement find-result this way unfortunately, because we get stack
  ;; overflow. Wat.
  (letfn [(some-or-missing? [k v]
            (if (some? v)
              ['?e k v]
              [(list 'missing? '$ '?e k)]))]
    (let [{:keys [locations categories price-range order search]} browse-params
          {:keys [top-category sub-category sub-sub-category]} categories]
      (db/one-with
        db
        {:where   ['[?e :browse-result/locations ?location]
                   (some-or-missing? :browse-result/order order)
                   (some-or-missing? :browse-result/search search)
                   (some-or-missing? :browse-result/top-category top-category)
                   (some-or-missing? :browse-result/sub-category sub-category)
                   (some-or-missing? :browse-result/sub-sub-category-category sub-sub-category)
                   (some-or-missing? :browse-result/from-price (:from-price price-range))
                   (some-or-missing? :browse-result/to-price (:to-price price-range))
                   ]
         :symbols {'?location (:db/id locations)}}))))