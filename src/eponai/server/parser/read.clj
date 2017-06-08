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
    [eponai.server.api.user :as user]
    [taoensso.timbre :as timbre]
    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [eponai.common.format.date :as date]))

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
                                                 [?c :user.cart/items ?e]
                                                 [?i :store.item/skus ?e]
                                                 [?s :store/items ?i]]
                                      :symbols {'?s store-id
                                                '?u (:user-id auth)}})})

(defread query/store
  [{:keys [db db-history query]} _ {:keys [store-id]}]
  {:auth    ::auth/public
   :uniq-by [[:store-id store-id]]}
  {:value (query/one db db-history query {:where   '[[?e :store/profile]]
                                          :symbols {'?e store-id}})})

(defread query/stores
  [{:keys [db db-history query]} _ _]
  {:auth ::auth/public}
  {:value (query/all db db-history query {:where   '[[?s :stream/state ?states]
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
  [{:keys [db query auth]} _ {:keys [store-id]}]
  {:auth    (if (some? store-id)
              {::auth/store-owner store-id}
              ::auth/any-user)
   :uniq-by [[:val (if (some? store-id)
                     [:store-id store-id]
                     [:user-id (hash (:email auth))])]]}
  {:value (if (some? store-id)
            (db/pull-all-with db query {:where   '[[?e :order/store ?s]]
                                        :symbols {'?s store-id}})
            (db/pull-all-with db query {:where   '[[?e :order/user ?u]]
                                        :symbols {'?u (:user-id auth)}}))})

(defread query/inventory
  [{:keys [query db]} _ {:keys [store-id]}]
  {:auth    {::auth/store-owner store-id}
   :uniq-by [[:store-id store-id]]}
  {:value (let [items (db/pull-all-with db query {:where   '[[?s :store/items ?e]]
                                                  :symbols {'?s store-id}})]
            ;(debug "Found items: " (into [] items))
            items)})

(defread query/order
  [{:keys [state query db auth] :as env} _ {:keys [order-id store-id]}]
  {:auth    ::auth/any-user
   :uniq-by [[:val (if (some? store-id)
                     [:store-id store-id]
                     [:user-id (hash (:email auth))])] [:order-id order-id]]}
  {:value (if (some? store-id)
            (db/pull-one-with db query {:where   '[[?e :order/store ?s]]
                                        :symbols {'?e order-id
                                                  '?s store-id}})
            (db/pull-one-with db query {:where   '[[?e :order/user ?u]]
                                        :symbols {'?e order-id
                                                  '?u (:user-id auth)}}))})

(defread query/order-payment
  [{:keys [state query db auth system] :as env} _ {:keys [order-id store-id]}]
  {:auth    ::auth/any-user
   :uniq-by [[:val (if (some? store-id)
                     [:store-id store-id]
                     [:user-id (hash (:email auth))])] [:order-id order-id]]}
  {:value (when-let [charge (if (some? store-id)
                              (db/pull-one-with db [:charge/id] {:where   '[[?o :order/store ?s]
                                                                            [?o :order/charge ?e]]
                                                                 :symbols {'?o order-id
                                                                           '?s store-id}})
                              (db/pull-one-with db [:charge/id] {:where   '[[?o :order/user ?u]
                                                                            [?o :order/charge ?e]]
                                                                 :symbols {'?o order-id
                                                                           '?u (:user-id auth)}}))]
            (stripe/get-charge (:system/stripe system) (:charge/id charge)))})

(defread query/stripe-account
  [env _ {:keys [store-id]}]
  {:auth    {::auth/store-owner store-id}
   :uniq-by [[:store-id store-id]]}
  {:value (store/account env store-id)})

(defread query/stripe-customer
  [env _ _]
  {:auth ::auth/any-user}
  {:value (let [c (user/customer env)]
            (debug "Query/stripe-customer " c)
            c)})


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
  {:auth ::auth/any-user}
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
  {:value (letfn [(add-time-to-all [time items]
                    (map #(if (nil? (:store.item/created-at %))
                           (assoc % :store.item/created-at time)
                           %)
                         items))]
            (->> (query/all db db-history query {:where '[[?e :store.item/name]]})
                 (add-time-to-all (date/current-millis))
                 (sort-by :store.item/created-at <)
                 (take 10)
                 (feature-all db-history :store.item)))})

(defread query/featured-stores
  [{:keys [db db-history query]} _ _]
  {:auth ::auth/public}
  ;; TODO: Come up with a way to feature stores.
  {:value (letfn [(add-time-to-all [time items]
                    (map #(if (nil? (:store/created-at %))
                           (assoc % :store/created-at time)
                           %)
                         items))]
            (->> (query/all db db-history query {:where '[[?e :store/profile]]})
                 (add-time-to-all (date/current-millis))
                 (sort-by :store/created-at <)
                 (take 4)
                 (feature-all db-history :store)))})

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

(defread query/browse-items
  [{:keys [db db-history query]} _ {{:keys [top-category sub-category] :as categories} :route-params
                                    {:keys [search]}                                   :query-params}]
  {:auth    ::auth/public
   :uniq-by [[:filter (cond
                        (seq search)
                        [:search search]
                        (some? top-category)
                        [:tc top-category]
                        (some? sub-category)
                        [:sc sub-category])]]}
  {:value (query/all db db-history query (cond
                                           (seq search)
                                           (products/find-with-search search)
                                           (some? top-category)
                                           (products/find-with-category-names (select-keys categories [:top-category]))
                                           (some? sub-category)
                                           (products/find-with-category-names (select-keys categories [:sub-category]))
                                           :else
                                           (products/find-all)))})

;; Get all categories.
(defread query/navigation
  [{:keys [db db-history query]} _ _]
  {:auth ::auth/public}
  {:value (let [children-keys #{:category/children :category/_children}]
            (query/all db db-history
                       (into [{:category/children [:db/id]}]
                             (comp (remove children-keys)
                                   (remove #(and (map? %) (some children-keys (keys %)))))
                             query)
                       {:where '[[?e :category/path]]}))})

(defread query/owned-store
  [{:keys [db db-history auth query]} _ _]
  {:auth ::auth/any-user}
  {:value (query/one db db-history query
                     {:where   '[[?owners :store.owner/user ?user]
                                 [?e :store/owners ?owners]]
                      :symbols {'?user (:user-id auth)}})})

(defread query/countries
  [{:keys [db db-history auth query]} _ _]
  {:auth ::auth/any-user}
  {:value (let [country-data (json/read-str (slurp (io/resource "private/country-data.json")) :key-fn keyword)
                continents (:continents country-data)]
            (debug "Countries: " (:continents country-data))
            (map (fn [[code country]]
                   {:country/code      (name code)
                    :country/name      (:name country)
                    :country/continent {:continent/code (:continent country)
                                        :continent/name (get continents (keyword (:continent country)))}})
                 (:countries country-data)))})