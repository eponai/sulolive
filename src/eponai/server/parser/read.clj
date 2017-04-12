(ns eponai.server.parser.read
  (:require
    [eponai.common.database :as db]
    [eponai.common.datascript :as eponai.datascript]
    [eponai.common.parser :as parser :refer [server-read read-basis-params]]
    [eponai.server.datomic.query :as query]
    [environ.core :as env]
    [datomic.api :as d]
    [taoensso.timbre :refer [error debug trace warn]]
    [eponai.server.auth :as auth]
    [eponai.server.external.stripe :as stripe]
    [eponai.server.external.wowza :as wowza]
    [eponai.server.external.chat :as chat]
    [eponai.server.api.store :as store]
    [eponai.common.api.products :as products]
    [eponai.server.api.user :as user]))

(defmethod server-read :datascript/schema
  [{:keys [db db-history]} _ _]
  {:value (-> (query/schema db db-history)
              (eponai.datascript/schema-datomic->datascript))})

(defmethod server-read :query/cart
  [{:keys [db db-history query auth]} _ {:keys [items]}]
  {:value (when (:email auth)
            (query/one db db-history query {:where   '[[?u :user/email ?email]
                                                       [?u :user/cart ?e]]
                                            :symbols {'?email (:email auth)}}))})

(defmethod read-basis-params :query/store [_ _ {:keys [store-id]}]
  [[:store-id store-id]])
(defmethod server-read :query/store
  [{:keys [db db-history query]} _ {:keys [store-id]}]
  {:value (query/one db db-history query {:where   '[[?e :store/name]]
                                          :symbols {'?e store-id}})})

(defmethod server-read :query/store-items
  [{:keys [db db-history query]} _ {:keys [store-id navigation]}]
  {:value (let [params (if (not-empty navigation)
                         {:where   '[[?s :store/items ?e]
                                     [?e :store.item/navigation ?n]
                                     [?n :store.navigation/path ?p]]
                          :symbols {'?s store-id
                                    '?p navigation}}

                         {:where   '[[?s :store/items ?e]]
                          :symbols {'?s store-id}}

                         )]
            (query/all db db-history query params))})

(defmethod read-basis-params :query/orders [_ _ {:keys [store-id user-id]}]
  [[:user-id user-id] [:store-id store-id]])
(defmethod server-read :query/orders
  [env _ {:keys [store-id user-id]}]
  {:value (cond (some? store-id)
                (store/list-orders env store-id)
                (some? user-id)
                (let [os (user/list-orders env user-id)]
                  (debug "Got user orders: " os)
                  os))})

(defmethod read-basis-params :query/inventory [_ _ {:keys [store-id]}]
  [[:store-id store-id]])
(defmethod server-read :query/inventory
  [env _ {:keys [store-id]}]
  {:value (let [items (store/list-products env store-id)]
            ;(debug "Found items: " (into [] items))
            items)})

(defmethod read-basis-params :query/order [_ _ {:keys [order-id store-id user-id]}]
  [[:user-id user-id] [:store-id store-id] [:order-id order-id]])
(defmethod server-read :query/order
  [env _ {:keys [order-id store-id user-id]}]
  {:value (cond (some? store-id)
                (store/get-order env store-id order-id)
                (some? user-id)
                (user/get-order env user-id order-id))})

(defmethod server-read :query/my-store
  [{:keys [db db-history query auth]} _ _]
  {:value (when-let [email (:email auth)]
            (debug "AUTH: " auth)
            (query/one db db-history query {:where   '[[?u :user/email ?email]
                                                       [?owner :store.owner/user ?u]
                                                       [?e :store/owners ?owner]]
                                            :symbols {'?email email}}))})

(defmethod server-read :query/stripe-account
  [env _ {:keys [store-id]}]
  {:value (store/account env store-id)})

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

(defmethod read-basis-params :query/user [_ _ {:keys [user-id]}]
  [[:user-id user-id]])
(defmethod server-read :query/user
  [{:keys [db db-history query]} _ {:keys [user-id]}]
  {:value (query/one db db-history query {:where   '[[?e]]
                                          :symbols {'?e user-id}})})

(defmethod read-basis-params :query/items [{:keys [route-params]} _ {:keys [category]}]
  [[:category (or category (:category route-params))]])
(defmethod server-read :query/items
  [{:keys [db db-history query route-params]} _ {:keys [category search] :as p}]
  {:value (let [c (or category (:category route-params))]
            (cond (some? category)
                  (db/pull-all-with db query (products/find-by-category c))
                  ;(query/all db db-history query (products/find-by-category c))
                  :else
                  (query/all db db-history query (products/find-all))))})

(defmethod server-read :query/top-categories
  [{:keys [db db-history query route-params]} _ {:keys [category search] :as p}]
  {:value (db/pull-all-with db query {:where '[[?e :category/level 0]]})})

(defmethod read-basis-params :query/category [{:keys [route-params]} _ {:keys [category]}]
  [[:category (or category (:category route-params))]])
(defmethod server-read :query/category
  [{:keys [db db-history query route-params]} _ {:keys [category search] :as p}]
  {:value (let [c (or category (:category route-params))]
            (db/pull-one-with db query {:where   '[[?e :category/path ?p]]
                                        :symbols {'?p c}}))})

(defmethod read-basis-params :query/item [_ _ {:keys [product-id]}]
  [[:product-id product-id]])
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

(defmethod read-basis-params :query/stream [_ _ {:keys [store-id]}]
  [[:store-id store-id]])
(defmethod server-read :query/stream
  [{:keys [db db-history query]} _ {:keys [store-id]}]
  {:value (query/one db db-history query {:where   '[[?e :stream/store ?store-id]]
                                          :symbols {'?store-id store-id}})})

(defmethod server-read :query/streams
  [{:keys [db db-history query]} _ _]
  {:value (query/all db db-history query {:where '[[?e :stream/state :stream.state/live]]})})

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


(defmethod read-basis-params :query/chat [_ _ params]
  [[:store-id (get-in params [:store :db/id])]])
(defmethod server-read :query/chat
  [{:keys [db-history query system]
    ::parser/keys [read-basis-t-for-this-key chat-update-basis-t]} k {:keys [store] :as params}]
  (let [chat (:system/chat system)]
    (when chat-update-basis-t
      (chat/sync-up-to! chat chat-update-basis-t))
    (if (nil? (:db/id store))
      (do (warn "No store :db/id passed in params for read: " k " with params: " params)
          {:value (parser/value-with-basis-t {} read-basis-t-for-this-key)})
      {:value (-> (if (nil? db-history)
                    (chat/initial-read chat store query)
                    (chat/read-messages chat store query read-basis-t-for-this-key))
                  (parser/value-with-basis-t (chat/last-read chat)))})))
