(ns eponai.server.parser.read
  (:require
    [eponai.common.database :as db]
    [eponai.common.datascript :as eponai.datascript]
    [eponai.common.parser :as parser]
    [eponai.server.datomic.query :as query]
    [environ.core :as env]
    [datomic.api :as d]
    [taoensso.timbre :refer [error debug trace warn]]
    [eponai.common.auth :as auth]
    [eponai.server.external.stripe :as stripe]
    [eponai.server.external.wowza :as wowza]
    [eponai.server.external.chat :as chat]
    [eponai.server.api.store :as store]
    [eponai.common.api.products :as products]
    [eponai.server.api.user :as user]))

(defmacro defread
  ""
  [read-sym args auth-and-basis-params mutate-body]
  (assert (and (map? auth-and-basis-params) (contains? auth-and-basis-params :auth))
          (str "defreads's auth and basis-params requires an :auth key"
               " was: " auth-and-basis-params))
  (let [read-key# (keyword (namespace read-sym) (name read-sym))
        basis-params-body# (when-let [bp# (:uniq-by auth-and-basis-params)]
                             `(defmethod parser/read-basis-params ~read-key# ~args ~bp#))]
    `(do
       ~basis-params-body#
       (defmethod parser/server-auth-role ~read-key# ~args ~(:auth auth-and-basis-params))
       (defmethod parser/server-read ~read-key# ~args ~mutate-body))))

(defread datascript/schema
  [{:keys [db db-history]} _ _]
  {:auth ::auth/public}
  {:value (-> (query/schema db db-history)
              (eponai.datascript/schema-datomic->datascript))})

(defread query/cart
  [{:keys [db db-history query auth]} _ {:keys [items]}]
  {:auth ::auth/any-user}
  {:value (query/one db db-history query {:where   '[[?u :user/cart ?e]]
                                          :symbols {'?u (:user-id auth)}})})

(defread query/checkout
  [{:keys [db db-history query auth]} _ {:keys [store-id]}]
  {:auth ::auth/any-user}
  {:value (db/pull-all-with db query {:where   '[[?u :user/cart ?c]
                                                 [?c :cart/items ?e]
                                                 [?i :store.item/skus ?e]
                                                 [?s :store/items ?i]]
                                      :symbols {'?s store-id}})})

(defread query/store
  [{:keys [db db-history query]} _ {:keys [store-id]}]
  {:auth    ::auth/public
   :uniq-by [[:store-id store-id]]}
  {:value (query/one db db-history query {:where   '[[?e :store/profile]]
                                          :symbols {'?e store-id}})})

(defread query/stores
  [{:keys [db db-history query]} _ _]
  {:auth ::auth/public}
  {:value (query/all db db-history query {:where '[[?s :stream/state ?states]
                                                   [?s :stream/store ?e]]
                                          :symbols {'[?states ...] [:stream.state/online
                                                                   :stream.state/offline]}})})

(defread query/store-items
  [{:keys [db db-history query]} _ {:keys [store-id navigation]}]
  {:auth    ::auth/public
   :uniq-by [[:store-id store-id] [:nav-path (hash navigation)]]}
  {:value (let [params (if (not-empty navigation)
                         {:where   '[[?s :store/items ?e]
                                     [?e :store.item/section ?n]
                                     [?n :store.section/path ?p]]
                          :symbols {'?s store-id
                                    '?p navigation}}

                         {:where   '[[?s :store/items ?e]]
                          :symbols {'?s store-id}}

                         )]
            (query/all db db-history query params))})

(defread query/orders
  [{:keys [db query]} _ {:keys [store-id user-id]}]
  {:auth    (cond
              (some? user-id)
              {::auth/exact-user user-id}
              (some? store-id)
              {::auth/store-owner store-id})
   :uniq-by [[:user-id user-id] [:store-id store-id]]}
  {:value (cond (some? store-id)
                (db/pull-all-with db query {:where   '[[?e :order/store ?s]]
                                            :symbols {'?s store-id}})
                (some? user-id)
                (db/pull-all-with db query {:where   '[[?e :order/user ?u]]
                                            :symbols {'?u user-id}}))})

(defread query/inventory
  [{:keys [query db]} _ {:keys [store-id]}]
  {:auth    {::auth/store-owner store-id}
   :uniq-by [[:store-id store-id]]}
  {:value (let [items (db/pull-all-with db query {:where   '[[?s :store/items ?e]]
                                                  :symbols {'s store-id}})]
            ;(debug "Found items: " (into [] items))
            items)})

(defread query/order
  [{:keys [state query db] :as env} _ {:keys [order-id store-id user-id]}]
  {:auth    {::auth/store-owner store-id}
   :uniq-by [[:user-id user-id] [:store-id store-id] [:order-id order-id]]}
  {:value (cond (some? store-id)
                (db/pull-one-with db query {:where   '[[?e :order/store ?s]]
                                            :symbols {'?e order-id
                                                      '?s store-id}})
                (some? user-id)
                (db/pull-one-with db query {:where   '[[?e :order/user ?u]]
                                            :symbols {'?e order-id
                                                      '?u user-id}}))})

(defread query/stripe-account
  [env _ {:keys [store-id]}]
  {:auth    {::auth/store-owner store-id}
   :uniq-by [[:store-id store-id]]}
  {:value (store/account env store-id)})

(defread query/stripe-country-spec
  [{:keys [system ast target db query]} _ _]
  {:auth ::auth/any-store-owner}
  {:value (stripe/get-country-spec (:system/stripe system) "CA")})

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

(defread query/user
  [{:keys [db db-history query]} _ {:keys [user-id]}]
  {:auth    ::auth/public
   :uniq-by [[:user-id user-id]]}
  {:value (query/one db db-history query {:where   '[[?e]]
                                          :symbols {'?e user-id}})})

(defread query/items
  [{:keys [db db-history query route-params]} _ {:keys [category search] :as p}]
  {:auth    ::auth/public
   :uniq-by [[:category (or category (:category route-params))]]}
  {:value (let [c (or category (:category route-params))]
            (cond (some? category)
                  (db/pull-all-with db query (products/find-by-category c))
                  ;(query/all db db-history query (products/find-by-category c))
                  :else
                  (query/all db db-history query (products/find-all))))})

(defread query/top-categories
  [{:keys [db db-history query route-params]} _ {:keys [category search] :as p}]
  {:auth    ::auth/public
   :uniq-by [[:category (or category (:category route-params))]]}
  {:value (db/pull-all-with db query {:where '[[?e :category/path]
                                               (not [_ :category/children ?e])]})})

(defread query/category
  [{:keys [db db-history query route-params]} _ {:keys [category search] :as p}]
  {:auth    ::auth/public
   :uniq-by [[:category (or category (:category route-params))]]}
  {:value (db/pull-one-with db query {:where   '[[?e :category/path ?p]]
                                      :symbols {'?p (or category (:category route-params))}})})

(defread query/item
  [{:keys [db db-history query]} _ {:keys [product-id]}]
  {:auth    ::auth/public
   :uniq-by [[:product-id product-id]]}
  {:value (query/one db db-history query
                     {:where   '[[?e :store.item/name]]
                      :symbols {'?e product-id}})})

(defread query/auth
  [{:keys [auth query db]} _ _]
  {:auth ::auth/any-user}
  {:value (let [query (or query [:db/id])]
            (db/pull db query (:user-id auth)))})

(defread query/stream
  [{:keys [db db-history query]} _ {:keys [store-id]}]
  {:auth    ::auth/public
   :uniq-by [[:store-id store-id]]}
  {:value (query/one db db-history query {:where   '[[?e :stream/store ?store-id]]
                                          :symbols {'?store-id store-id}})})

(defread query/streams
  [{:keys [db db-history query]} _ _]
  {:auth ::auth/public}
  {:value (query/all db db-history query {:where '[[?e :stream/state :stream.state/live]]})})

; #### FEATURED ### ;

(defn feature-all [db-history namespace coll]
  (cond->> coll
           (nil? db-history)
           (into [] (map #(assoc % (keyword (name namespace) "featured") true)))))

(defread query/featured-streams
  [{:keys [db db-history query]} _ _]
  {:auth ::auth/public}
  {:value (->> (query/all db db-history query {:where '[[?e :stream/store]]})
               (feature-all db-history :stream))})

(defread query/featured-items
  [{:keys [db db-history query]} _ _]
  {:auth ::auth/public}
  {:value (->> (query/all db db-history query {:where '[[?e :store.item/name]]})
               (take 5)
               (feature-all db-history :store.item))})

(defread query/featured-stores
  [{:keys [db db-history query]} _ _]
  {:auth ::auth/public}
  ;; TODO: Come up with a way to feature stores.
  {:value (->> (query/all db db-history query {:where '[[?e :store/profile]]})
               (take 4)
               (feature-all db-history :store))})

(defread query/stream-config
  [{:keys [db db-history query system]} k _]
  {:auth ::auth/public}
  ;; Only query once. User will have to refresh the page to get another stream config
  ;; (For now).
  (when (nil? db-history)
    (let [wowza (:system/wowza system)]
      {:value {:ui/singleton                              :ui.singleton/stream-config
               :ui.singleton.stream-config/subscriber-url (wowza/subscriber-url wowza)
               :ui.singleton.stream-config/publisher-url  (wowza/publisher-url wowza)}})))

(defread query/chat
  [{:keys         [db-history query system]
    ::parser/keys [read-basis-t-for-this-key chat-update-basis-t]} k {:keys [store] :as params}]
  {:auth    ::auth/public
   :uniq-by [[:store-id (:db/id store)]]}
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
