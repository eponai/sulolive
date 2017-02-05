(ns eponai.client.parser.read
  (:require
    [clojure.set :as set]
    [eponai.common.parser :as parser :refer [client-read]]
    [eponai.common.parser.util :as parser.util]
    [eponai.common.database :as db]
    [eponai.common.business.budget :as business.budget]
    [eponai.common.parser.read :as common.read]
    [om.next.impl.parser :as om.parser]
    [eponai.client.routes :as routes]
    [eponai.client.parser.message :as msg]
    [eponai.common :as c]
    [taoensso.timbre :refer [debug]]
    [eponai.client.auth :as auth]))

;; ################ Local reads  ####################
;; Generic, client only local reads goes here.

;; ################ Remote reads ####################
;; Remote reads goes here. We share these reads
;; with all client platforms (web, ios, android).
;; Local reads should be defined in:
;;     eponai.<platform>.parser.read

;; ----------

(defmethod client-read :query/store
  [{:keys [db query target ast route-params] :as env} _ _]
  (let [store-id (c/parse-long (:store-id route-params))
        store (db/pull-one-with db query {:where   '[[?e]]
                                          :symbols {'?e store-id}})]
    (if target
      {:remote (assoc-in ast [:params :store-id] store-id)}
      {:value (common.read/multiply-store-items store)})))

(defmethod client-read :query/user
  [{:keys [db query target ast route-params] :as env} _ _]
  (debug "query/user: " (c/parse-long (:user-id route-params)))
  (let [user-id (c/parse-long (:user-id route-params))
        user (db/pull-one-with db query {:where   '[[?e]]
                                          :symbols {'?e user-id}})]
    (if target
      {:remote (assoc-in ast [:params :user-id] user-id)}
      {:value user})))

(defmethod client-read :query/cart
  [{:keys [db query target ast]} _ _]
  (let [cart (db/pull-one-with db query {:where '[[?e :cart/items]]})]
    (if target
      {:remote true }
      {:value (common.read/compute-cart-price cart)})))

(defmethod client-read :query/items
  [{:keys [db query target route-params ast] :as env} _ p]
  (let [{:keys [category search]} route-params]
    (if target
      {:remote (cond-> ast
                       (some? category)
                       (assoc-in [:params :category] category)
                       (some? search)
                       (assoc-in [:params :search] search))}
      {:value (let [pattern {:where '[[?e :store.item/name]]}]
                (assert (some #{:db/id} query)
                        (str "Query to :query/all-tiems must contain :db/id, was: " query))
                (cond->> (db/pull-all-with db query pattern)
                         (or search category)
                         (filter #(cond (not-empty search)
                                        (not-empty (re-find (re-pattern search) (.toLowerCase (:store.item/name %))))
                                        (some? category)
                                        (= category (:store.item/category %))
                                        :else
                                        true))
                         :always
                         (sort-by :db/id)))})))

(defmethod client-read :query/item
  [{:keys [db query target route-params ast]} _ _]
  (let [product-id (c/parse-long (:product-id route-params))]
    (if target
      {:remote (assoc-in ast [:params :product-id] product-id)}
      {:value (db/pull-one-with db query
                                {:where   '[[?e :store.item/name]]
                                 :symbols {'?e product-id}})})))

(defmethod client-read :query/auth
  [{:keys [target db query]} _ _]
  ;(debug "Read query/auth: ")
  (if target
    {:remote true}
    {:value (let [query (or query [:db/id])
                  user (auth/current-auth db)]
              (db/pull db query user))}))

(defmethod client-read :query/streams
  [{:keys [db query target]} _ _]
  ;(debug "Read query/auth: ")
  (if target
    {:remote true}
    {:value (db/pull-all-with db query {:where '[[?e :stream/name]]})}))

; ### FEATURED ### ;

(defmethod client-read :query/featured-streams
  [{:keys [db query]} _ _]
  {:remote true
   :value  (->> (db/all-with db {:where '[[?e :stream/featured]]})
                (db/pull-many db query)
                (sort-by :db/id))})

(defmethod client-read :query/featured-items
  [{:keys [db query]} _ _]
  {:remote true
   :value  (let [items (db/all-with db {:where '[[?e :store.item/featured]]})]
             (sort-by :db/id
                      (db/pull-many db query items)))})

(defmethod client-read :query/featured-stores
  [{:keys [db query]} _ _]
  ;; Only fetch featured-stores initially? i.e. (when (nil? db-history) ...)
  ;; TODO: Come up with a way to feature stores. DB SHUFFLE
  {:remote true
   :value  (letfn [(photos-fn [store]
                     (let [s (db/entity db store)
                           [img-1 img-2] (->> (:store.item/_store s)
                                              (sort-by :db/id)
                                              (map :store.item/img-src)
                                              (take 2))]
                       {:db/id                  store
                        :store/featured-img-src [img-1 (:store/photo s) img-2]}))]
             (sort-by :db/id
                      (into [] (comp (map photos-fn)
                                     (map #(merge % (db/pull db query (:db/id %)))))
                            (db/all-with db {:where '[[?e :store/featured]]}))))})

(defmethod client-read :query/stream-config
  [{:keys [db query]} k _]
  {:remote true
   :value  (db/pull-one-with db query
                             {:where '[[?e :ui.singleton.stream-config/hostname]
                                       [?e :ui/singleton :ui.singleton/stream-config]]})})

(defmethod client-read :query/current-route
  [{:keys [db]} k p]
  {:value (routes/current-route db)})

(defmethod client-read :routing/app-root
  [{:keys [db] :as env} k p]
  (let [current-route (routes/current-route db)]
    (debug "Reading app-root: " [k :route current-route])
    (parser.util/read-union env k p (:route current-route))))

(defmethod client-read :query/business-model
  [e k p]
  {:value business.budget/world})

(defmethod client-read :query/messages
  [{:keys [db]} _ _]
  {:value (parser/get-messages db)})
