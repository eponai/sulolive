(ns eponai.server.parser.read
  (:require
    [eponai.common.database :as db]
    [eponai.common.datascript :as eponai.datascript]
    [eponai.common.parser :as parser :refer [server-read read-basis-param-path]]
    [eponai.common.parser.read :as common.read]
    [eponai.server.datomic.query :as query]
    [environ.core :as env]
    [clojure.data.generators :as gen]
    [datomic.api :as d]
    [taoensso.timbre :refer [error debug trace warn]]
    [eponai.server.auth :as auth])
  (:import [java.util Random]))

(defn db-shuffle
  "Shuffle items consistently with the db, meaing
  with the same db basis-t you get the same items."
  [db items]
  (binding [gen/*rnd* (Random. (d/basis-t db))]
    (gen/shuffle items)))

(defmethod server-read :datascript/schema
  [{:keys [db db-history]} _ _]
  {:value (-> (query/schema db db-history)
              (eponai.datascript/schema-datomic->datascript))})

(defn read-cart-items [{:keys [db query]} items]
  (let [pattern [:db/id :item/price :item/img-src :item/name {:item/store [:db/id :store/name :store/rating :store/review-count :store/photo]}]]
    (debug "Signout cart: " (db/pull-many db pattern items))
    (db/pull-many db pattern items)))

(defn read-user-cart [{:keys [db db-history query]}]
  (let [cart (query/one db db-history query {:where '[[?e :cart/items]]})]
    (cond-> cart
            (nil? db-history)
            (-> (update :cart/items #(seq (map (comp (partial d/entity db) :db/id) %)))
                (common.read/compute-cart-price)))))

(defmethod server-read :query/cart
  [{:keys [db db-history query params auth] :as env} _ {:keys [items]}]
  {:value (cond
            (auth/is-logged-in? auth)
            (read-user-cart env)
            (not-empty items)
            (read-cart-items env items))})

(defn- env-params->store-id [env params]
  (or (get-in env [:params :store-id])
      (get-in params [:store-id])))

(defmethod read-basis-param-path :query/store [e _ p] [(env-params->store-id e p)])
(defmethod server-read :query/store
  [{:keys [db db-history query auth] :as env} _ p]
  (debug "AUTH: " auth)
  (let [store-id (env-params->store-id env p)]
    (when-let [store-id (cond-> store-id (string? store-id) (Long/parseLong))]
      {:value (cond-> (query/one db db-history query {:where   '[[?e]]
                                                      :symbols {'?e store-id}})
                      (nil? db-history)
                      (common.read/multiply-store-items))})))

(defmethod server-read :query/items
  [{:keys [db db-history query params]} _ p]
  {:value (let [category (or (:category params) (when (string? (:category p)) (:category p)))
                search (or (:search params) (when (string? (:search p)) (:search p)))
                pattern (if (or category search)
                          (cond-> {:where '[]}
                                  (not-empty category)
                                  (db/merge-query
                                    {:where   '[[?e :item/category ?c]]
                                     :symbols {'?c category}})
                                  (not-empty search)
                                  (db/merge-query
                                    {:where   '[[(str "(?i)" ?search) ?matcher]
                                                [(re-pattern ?matcher) ?regex]
                                                [(re-find ?regex ?aname)]
                                                [?e :item/name ?aname]]
                                     :symbols {'?search search}}))
                          {:where '[[?e :item/name]]})]
            (cond->>
              (db/pull-many db query (db/all-with db pattern))
              (nil? db-history)
              (sort-by :db/id)))})

(defmethod read-basis-param-path :query/item [{:keys [params]} _ _] [(:product-id params)])
(defmethod server-read :query/item
  [{:keys [db db-history query params]} _ p]
  (let [product-id (or (:product-id params) (:product-id p))]
    {:value (cond-> (query/one db db-history query
                               {:where   '[[?e :item/name]]
                                :symbols {'?e (Long/parseLong product-id)}})
                    (nil? db-history)
                    (assoc :item/details common.read/item-details))}))

(defmethod server-read :query/auth
  [{:keys [auth]} _ _]
  {:value (when (some? (:iss auth))
            auth)})

(defmethod server-read :query/streams
  [{:keys [db query]} _ _]
  {:value (db/pull-all-with db query
                            {:where '[[?e :stream/name]]})})

; #### FEATURED ### ;

(defmethod server-read :query/featured-streams
  [{:keys [db query]} _ _]
  {:value (sort-by :db/id
                   (into []
                         (map #(assoc % :stream/featured true))
                         (->> (db/all-with db {:where '[[?e :stream/store]]})
                              (db-shuffle db)
                              (db/pull-many db query))))})

(defmethod server-read :query/featured-items
  [{:keys [db query]} _ _]
  (let [items (->> (db/all-with db {:where '[[?e :item/id]]})
                   (db-shuffle db)
                   (take 4))]
    {:value (sort-by :db/id
                     (into []
                           (map #(assoc % :item/featured true))
                           (db/pull-many db query items)))}))

(defmethod server-read :query/featured-stores
  [{:keys [db db-history query]} _ _]
  ;; Only fetch featured-stores initially? i.e. (when (nil? db-history) ...)
  ;; TODO: Come up with a way to feature stores.
  (let [feautred-stores (->> (db/all-with db {:where '[[?e :store/name]]})
                             (db-shuffle db)
                             (take 4))
        photos-fn (fn [store]
                    (let [s (db/entity db store)
                          [img-1 img-2] (into [] (comp (take 2) (map :item/img-src)) (db-shuffle db (:item/_store s)))]
                      {:db/id store
                       :store/featured-img-src [img-1 (:store/photo s) img-2]}))]
    {:value (sort-by :db/id
                     (into [] (comp (map photos-fn)
                                    (map #(merge % (db/pull db query (:db/id %))))
                                    (map #(assoc % :store/featured true)))
                           feautred-stores))}))

(defmethod server-read :query/stream-config
  [{:keys [db query]} k _]
  (assert (= (every? {:db/id :stream-config/hostname} query))
          (str "server read: " k
               " only supports pull-pattern : " [:stream-config/hostname :db/id]
               " was: " query))
  {:value {:ui/singleton                        :ui.singleton/stream-config
           :ui.singleton.stream-config/hostname (or (env/env :red5pro-server-url) "localhost")}})
