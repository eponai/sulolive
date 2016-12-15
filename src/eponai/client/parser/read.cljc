(ns eponai.client.parser.read
  (:require
    [eponai.common.parser :as parser :refer [client-read]]
    [eponai.common.parser.read :as common.read :refer [get-param]]
    [eponai.common.database :as db]
    [om.next.impl.parser :as om.parser]
    [eponai.common :as c]
    #?(:cljs
       [cljs.reader])
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
  [{:keys [db query target] :as env} _ p]
  (let [store (db/pull-one-with db query {:where   '[[?e]]
                                          :symbols {'?e (c/parse-long (get-param env p :store-id))}})]
    (if target
      {:remote true}
      {:value (common.read/multiply-store-items store)})))

(defn local-cart []
  #?(:cljs
    (when-let [stored (.getItem js/localStorage "cart")]
      (cljs.reader/read-string stored))))

(defn read-local-cart [db cart]
  (-> (or cart {:cart/items []})
      (update :cart/items #(db/pull-many db [:db/id :item/price :item/img-src :item/name {:item/store [:store/photo :store/name :store/rating :store/review-count]}] %))
      (common.read/compute-cart-price)))

(defmethod client-read :query/cart
  [{:keys [db query target ast]} _ _]
  (let [cart (if (auth/is-logged-in?)
               (db/pull-one-with db query {:where '[[?e :cart/items]]})
               (local-cart))]
    (if target
      {:remote (if (auth/is-logged-in?)
                 true
                 (assoc-in ast [:params :items] (:cart/items cart)))}
      {:value (do
                (if (auth/is-logged-in?)
                  (common.read/compute-cart-price cart)
                  (read-local-cart db cart)))})))

(defmethod client-read :query/items
  [{:keys [db query target] :as env} _ p]
  (if target
    {:remote true}
    {:value (let [category (get-param env p :category)
                  search (get-param env p :search)
                  pattern {:where '[[?e :item/name]]}]
              (assert (some #{:db/id} query)
                      (str "Query to :query/all-tiems must contain :db/id, was: " query))

              (cond->> (db/pull-all-with db query pattern)
                       (or search category)
                       (filter #(cond (not-empty search)
                                      (not-empty (re-find (re-pattern search) (.toLowerCase (:item/name %))))
                                      (some? category)
                                      (= category (:item/category %))
                                      :else
                                      true))
                       :always
                       (sort-by :db/id)))}))

(defmethod client-read :query/item
  [{:keys [db query target] :as env} _ p]
  (if target
    {:remote true}
    {:value (db/pull-one-with db query
                              {:where   '[[?e :item/name]]
                               :symbols {'?e (c/parse-long (get-param env p :product-id))}})}))

(defmethod client-read :query/auth
  [{:keys [target]} _ _]
  ;(debug "Read query/auth: ")
  (if target
    {:remote true}
    {:value #?(:cljs (auth/logged-in-user)
               :clj  nil)}))

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
   :value  (let [items (db/all-with db {:where '[[?e :item/featured]]})]
             (sort-by :db/id
                      (db/pull-many db query items)))})

(defmethod client-read :query/featured-stores
  [{:keys [db db-history query]} _ _]
  ;; Only fetch featured-stores initially? i.e. (when (nil? db-history) ...)
  ;; TODO: Come up with a way to feature stores. DB SHUFFLE
  (let [photos-fn (fn [store]
                    (let [s (db/entity db store)
                          [img-1 img-2] (into [] (comp (take 2) (map :item/img-src)) (:item/_store s))]
                      {:db/id                  store
                       :store/featured-img-src [img-1 (:store/photo s) img-2]}))]
    {:remote true
     :value  (let [featured-stores (db/all-with db {:where '[[?e :store/featured]]})]
               (sort-by :db/id
                        (into [] (comp (map photos-fn)
                                       (map #(merge % (db/pull db query (:db/id %)))))
                              featured-stores)))}))

(defmethod client-read :query/stream-config
  [{:keys [db query]} k _]
  {:remote true
   :value  (db/pull-one-with db query
                             {:where '[[?e :ui.singleton.stream-config/hostname]
                                       [?e :ui/singleton :ui.singleton/stream-config]]})})