(ns eponai.server.parser.read
  (:require
    [eponai.common.database :as db]
    [eponai.common.datascript :as eponai.datascript]
    [eponai.common.parser :as parser :refer [server-read read-basis-param-path]]
    [eponai.server.datomic.query :as query]
    [environ.core :as env]
    [datomic.api :as d]
    [taoensso.timbre :refer [error debug trace warn]]
    [eponai.server.auth :as auth]
    [eponai.server.external.stripe :as stripe]
    [eponai.server.external.wowza :as wowza]
    [eponai.server.api.store :as store]))

(defmethod server-read :datascript/schema
  [{:keys [db db-history]} _ _]
  {:value (-> (query/schema db db-history)
              (eponai.datascript/schema-datomic->datascript))})

(defmethod server-read :query/cart
  [{:keys [db db-history query]} _ {:keys [items]}]
  {:value (query/one db db-history query (cond-> {:where '[[?e :cart/items]]}
                                                 (seq items)
                                                 (db/merge-query {:symbols {'[?e ...] items}})))})

(defmethod read-basis-param-path :query/store [_ _ {:keys [store-id]}]
  [store-id])
(defmethod server-read :query/store
  [{:keys [db db-history query]} _ {:keys [store-id]}]
  {:value (query/one db db-history query {:where   '[[?e]]
                                          :symbols {'?e store-id}})})

(defmethod server-read :query/orders
  [env _ {:keys [store-id]}]
  {:value (map (fn [o]
                 (reduce-kv (fn [m k v] (assoc m (keyword "order" (name k)) v)) {} o))
               (store/list-orders env store-id))})

(defmethod server-read :query/order
  [env _ {:keys [order-id store-id]}]
  {:value (store/get-order env store-id order-id)})

(defmethod server-read :query/my-store
  [{:keys [db db-history query auth]} _ _]
  {:value (when-let [email (:email auth)]
            (debug "AUTH: " auth)
            (query/one db db-history query {:where   '[[?u :user/email ?email]
                                                       [?owner :store.owner/user ?u]
                                                       [?e :store/owners ?owner]]
                                            :symbols {'?email email}}))})

;(defmethod server-read :query/stripe
;  [{:keys [db db-history query system auth]} _ _]
;  (debug "SYSTEM: " system)
;  {:value (let [{:stripe/keys [id secret]}
;                (db/pull-one-with db [:stripe/id :stripe/secret] {:where   '[[?u :user/email ?email]
;                                                                             [?owner :store.owner/user ?u]
;                                                                             [?s :store/owners ?owner]
;                                                                             [?s :store/stripe ?e]]
;                                                                  :symbols {'?email (:email auth)}})
;                products (stripe/get-products (:system/stripe system) secret)
;                stripe-account (stripe/get-account (:system/stripe system) id)]
;            (debug "Got stripe account: " {:account  stripe-account
;                                           :products products})
;            {:stripe/id       id
;             :stripe/account  stripe-account
;             :stripe/products products})})

(defmethod server-read :query/user
  [{:keys [db db-history query]} _ {:keys [user-id]}]
  {:value (query/one db db-history query {:where   '[[?e]]
                                          :symbols {'?e user-id}})})

(defmethod server-read :query/items
  [{:keys [db db-history query]} _ {:keys [category search]}]
  {:value (query/all db db-history query {:where '[[?e :store.item/name]]})})

(defmethod read-basis-param-path :query/item [_ _ {:keys [product-id]}]
  [product-id])

(defmethod server-read :query/item
  [{:keys [db db-history query]} _ {:keys [product-id]}]
  {:value (query/one db db-history query
                     {:where   '[[?e :store.item/name]]
                      :symbols {'?e product-id}})})

(defmethod server-read :query/auth
  [{:keys [auth query db]} _ _]
  (debug "READING AUTH: " query)
  {:value (when (some? (:iss auth))
            (let [query (or query [:db/id])]
              (db/pull-one-with db query {:where   '[[?e :user/email ?email]]
                                          :symbols {'?email (:email auth)}})))})

(defmethod server-read :query/streams
  [{:keys [db query]} _ _]
  {:value (db/pull-all-with db query {:where '[[?e :stream/name]]})})

; #### FEATURED ### ;

(defn feature-all [db-history namespace coll]
  (cond->> coll
           (nil? db-history)
           (into [] (map #(assoc % (keyword (name namespace) "featured") true)))))

(defmethod server-read :query/featured-streams
  [{:keys [db db-history query]} _ _]
  {:value (->> (query/all db db-history query {:where '[[?e :stream/store]]})
               (feature-all db-history :stream))})

(defmethod server-read :query/featured-items
  [{:keys [db db-history query]} _ _]
  {:value (->> (query/all db db-history query {:where '[[?e :store.item/name]]})
               (take 5)
               (feature-all db-history :store.item))})

(defmethod server-read :query/featured-stores
  [{:keys [db db-history query]} _ _]
  ;; TODO: Come up with a way to feature stores.
  {:value (->> (query/all db db-history query {:where '[[?e :store/name]]})
               (take 4)
               (feature-all db-history :store))})

(defmethod server-read :query/stream-config
  [{:keys [db db-history query system]} k _]
  ;; Only query once. User will have to refresh the page to get another stream config
  ;; (For now).
  (when (nil? db-history)
    (let [wowza (:system/wowza system)]
      {:value {:ui/singleton                              :ui.singleton/stream-config
               :ui.singleton.stream-config/subscriber-url (wowza/subscriber-url wowza)
               :ui.singleton.stream-config/publisher-url  (wowza/publisher-url wowza)}})))
