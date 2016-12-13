(ns eponai.client.parser.read
  (:require
    [eponai.common.parser :as parser :refer [client-read]]
    [eponai.common.parser.read :as common.read]
    [eponai.common.database :as db]
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
  [{:keys [db query target]} _ {:keys [store-id]}]
  (let [store (db/pull-one-with db query {:where '[[?e]]
                                          :symbols {'?e (c/parse-long store-id)}})]
    (if target
      {:remote true}
      {:value (common.read/multiply-store-items store)})))

(defmethod client-read :query/cart
  [{:keys [db query target]} _ _]
  (debug "REad wuery/cart: " (auth/logged-in-user))
  (if target
    {:remote/user (or true (auth/is-logged-in?))}
    {:value (let [cart (if (or true (auth/is-logged-in?))
                         (db/pull-one-with db query {:where '[[?e :cart/items]]})
                         (db/pull-one-with db query {:where '[[?e :ui.component.cart/items]]}))]
              (debug "Got cart: " cart " active user: " (auth/logged-in-user))
              (common.read/compute-cart-price cart))}))

(defmethod client-read :query/items
  [{:keys [db query target]} _ {:keys [category]}]
  (if target
    {:remote true}
    {:value (let [pattern (if category
                            {:where '[[?e :item/category ?c]]
                             :symbols {'?c category}}
                            {:where '[[?e :item/id]]})]
              (debug "Read query/items: " category)

              (assert (some #{:db/id} query)
                      (str "Query to :query/all-tiems must contain :db/id, was: " query))
              (sort-by :db/id (db/pull-all-with db query pattern)))}))

(defmethod client-read :query/auth
  [{:keys [target]} _ _]
  (debug "Read query/auth: ")
  (if target
    {:remote true}
    {:value #?(:cljs (auth/logged-in-user)
               :clj nil)}))

; ### FEATURED ### ;

(defmethod client-read :query/featured-streams
  [{:keys [db query]} _ _]
  {:remote true
   :value (->> (db/all-with db {:where '[[?e :stream/featured]]})
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
                      {:db/id store
                       :store/featured-img-src [img-1 (:store/photo s) img-2]}))]
    {:remote true
     :value  (let [featured-stores (db/all-with db {:where '[[?e :store/featured]]})]
               (sort-by :db/id
                        (into [] (comp (map photos-fn)
                                       (map #(merge % (db/pull db query (:db/id %)))))
                              featured-stores)))}))