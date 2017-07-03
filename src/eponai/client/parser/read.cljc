(ns eponai.client.parser.read
  (:require
    [clojure.string :as str]
    [om.next.impl.parser :as om.parser]
    [eponai.common.parser :as parser :refer [client-read]]
    [eponai.common.parser.util :as parser.util]
    [eponai.common.database :as db]
    [eponai.common.parser.read :as common.read]
    [eponai.common.datascript :as datascript]
    [eponai.client.routes :as client.routes]
    [eponai.common.ui.router :as router]
    [eponai.common :as c]
    [taoensso.timbre :as timbre :refer [debug warn]]
    [eponai.client.auth :as client.auth]
    [eponai.common.api.products :as products]
    [medley.core :as medley]
    [eponai.client.cart :as client.cart]
    [eponai.client.chat :as client.chat]
    [eponai.common.browse :as browse]))



;; ################ Local reads  ####################
;; Generic, client only local reads goes here.
(defmethod client-read :query/ui-state
  [{:keys [db query target]} _ _]
  (when (nil? target)
    {:value (db/pull-one-with db query {:where '[[?e :ui/singleton :ui.singleton/state]]})}))

(defmethod client-read :query/loading-bar
  [{:keys [db query target]} _ _]
  (when-not target
    {:value (db/pull-one-with db query {:where '[[?e :ui/singleton :ui.singleton/loading-bar]]})}))

(defmethod client-read :query/login-modal
  [{:keys [db query target]} _ _]
  (when-not target
    {:value (db/pull-one-with db query {:where '[[?e :ui/singleton :ui.singleton/login-modal]]})}))


;; ################ Remote reads ####################
;; Remote reads goes here. We share these reads
;; with all client platforms (web, ios, android).
;; Local reads should be defined in:
;;     eponai.<platform>.parser.read

;; ----------

;; ################# Browsing reads

(defmethod client-read :query/stores
  [{:keys [db query target]} _ {:keys [states]}]
  (debug "Read query/stores: " states)
  (if target
    {:remote true}
    {:value (when-let [loc (client.auth/current-locality db)]
              (if (some? states)
                (db/pull-all-with db query {:where   '[[?e :store/locality ?l]
                                                       [?st :status/type :status.type/open]
                                                       [?e :store/status ?st]
                                                       [?s :stream/state ?states]
                                                       [?s :stream/store ?e]]
                                            :symbols {'?l (:db/id loc)
                                                      '[?states ...] states}})
                (db/pull-all-with db query {:where   '[[?e :store/locality ?l]
                                                       [?st :status/type :status.type/open]
                                                       [?e :store/status ?st]]
                                            :symbols {'?l (:db/id loc)}})))}))

(defmethod client-read :query/streams
  [{:keys [db query target]} _ _]
  ;(debug "Read query/auth: ")
  (if target
    {:remote true}
    {:value (when-let [loc (client.auth/current-locality db)]
              (db/pull-all-with db query {:where   '[[?s :store/locality ?l]
                                                     [?st :status/type :status.type/open]
                                                     [?s :store/status ?st]
                                                     [?e :stream/store ?s]
                                                     [?e :stream/state :stream.state/live]]
                                          :symbols {'?l (:db/id loc)}}))}))

(defmethod client-read :query/browse-items
  [{:keys [db target query route-params query-params]} _ _]
  (if target
    {:remote true}
    {:value (when-let [loc (client.auth/current-locality db)]
              (let [{:keys [top-category sub-category]} route-params]
                (db/pull-all-with db query (cond
                                             (seq (:search query-params))
                                             (products/find-with-search loc (:search query-params))
                                             (or (some? sub-category) (some? top-category))
                                             (products/find-with-category-names loc route-params)
                                             :else
                                             (products/find-all loc)))))}))

;; ----- Featured

(defmethod client-read :query/featured-streams
  [{:keys [db query]} _ _]
  {:remote true
   :value  (when-let [loc (client.auth/current-locality db)]
             (->> (db/all-with db {:where   '[[?e :stream/featured]
                                              [?e :stream/store ?s]
                                              [?s :store/locality ?l]]
                                   :symbols {'?l (:db/id loc)}})
                  (db/pull-many db query)
                  (sort-by :db/id)))})

(defmethod client-read :query/featured-items
  [{:keys [db query]} _ _]
  {:remote true
   :value  (when-let [loc (client.auth/current-locality db)]
             (let [items (db/all-with db {:where   '[[?s :store/locality ?l]
                                                     [?s :store/items ?e]
                                                     [?e :store.item/featured]]
                                          :symbols {'?l (:db/id loc)}})
                   pulled (db/pull-many db query items)]
               (sort #(let [c (compare (:store.item/created-at %2)
                                       (:store.item/created-at %1))]
                       (if (zero? c)
                         (compare (:store.item/name %1) (:store.item/name %2))
                         c))
                     pulled)))})

(defmethod client-read :query/featured-stores
  [{:keys [db query]} _ _]
  ;; Only fetch featured-stores initially? i.e. (when (nil? db-history) ...)
  ;; TODO: Come up with a way to feature stores. DB SHUFFLE
  {:remote true
   :value  (when-let [loc (client.auth/current-locality db)]
             (letfn [(photos-fn [store]
                       (let [s (db/entity db store)
                             [img-1 img-2] (->> (:store.item/_store s)
                                                (sort-by :db/id)
                                                (map :store.item/img-src)
                                                (take 2))]
                         {:db/id                  store
                          :store/featured-img-src [img-1 (:store.profile/photo (:store/profile s)) img-2]}))]
               (sort-by :store/created-at
                        #(compare %2 %1)
                        (into [] (comp (map photos-fn)
                                       (map #(merge % (db/pull db query (:db/id %)))))
                              (db/all-with db {:where   '[[?e :store/featured]
                                                          [?e :store/locality ?l]]
                                               :symbols {'?l (:db/id loc)}})))))})

;################

(defmethod client-read :query/store
  [{:keys [db query target route-params] :as env} _ _]
  (when-let [store-id (:store-id route-params)]
    (if target
      {:remote true}
      {:value (db/pull db query store-id)})))

(defmethod client-read :query/store-items
  [{:keys [db query target route-params]} _ _]
  (let [{:keys [store-id navigation]} route-params]
    (when (some? store-id)
      (if target
        {:remote true}
        {:value (do
                  (debug "Read store items with nav: " navigation " store-id: " store-id)
                  (db/pull-all-with db query (if (not-empty navigation)
                                               {:where   '[[?s :store/items ?e]
                                                           [?e :store.item/section ?n]
                                                           [?n :store.section/path ?p]]
                                                :symbols {'?s store-id
                                                          '?p navigation}}
                                               {:where   '[[?s :store/items ?e]]
                                                :symbols {'?s store-id}})))}))))

(defmethod client-read :query/store-item-count
  [{:keys [db target route-params] :as env} _ _]
  (when-let [store-id (:store-id route-params)]
    (if target
      {:remote (om.parser/expr->ast (list {:read/with-state [{:query/store [{:store/items [:store.item/name]}]}]}
                                          {:route-params {:store-id (:store-id (:raw-route-params env))}}))}
      {:value (db/find-with db {:find    '[(count ?e) .]
                                :where   '[[?store :store/items ?e]]
                                :symbols {'?store store-id}})})))

(defmethod client-read :query/orders
  [{:keys [db query target route-params]} _ _]
  (if target
    {:remote true}
    {:value (if-let [store-id (:store-id route-params)]
              (db/pull-all-with db query {:where   '[[?e :order/store ?s]]
                                          :symbols {'?s store-id}})
              (when-let [user-id (client.auth/current-auth db)]
                (db/pull-all-with db query {:where   '[[?e :order/user ?u]]
                                            :symbols {'?u user-id}})))}))

(defmethod client-read :query/inventory
  [{:keys [db query target route-params]} _ _]
  (when-let [store-id (:store-id route-params)]
    (if target
      {:remote true}
      {:value (db/pull-all-with db query {:where   '[[?store :store/items ?e]]
                                          :symbols {'?store store-id}})})))

(defmethod client-read :query/order
  [{:keys [db query target route-params]} _ _]
  (when-let [order-id (:order-id route-params)]
    (if target
      {:remote true}
      {:value (db/pull db query order-id)})))

(defmethod client-read :query/order-payment
  [{:keys [db query target route-params]} _ _]
  (when-let [order-id (:order-id route-params)]
    (if target
      {:remote true}
      {:value (db/pull-one-with db query {:where   '[[?o :order/charge ?e]]
                                          :symbols {'?o order-id}})})))

(defmethod client-read :query/stripe-account
  [{:keys [route-params target db query]} _ _]
  (when-let [store-id (:store-id route-params)]
    (if target
      {:remote true}
      {:value (db/pull-one-with db query {:where   '[[?s :store/stripe ?e]]
                                          :symbols {'?s store-id}})})))

(defmethod client-read :query/stripe-balance
  [{:keys [route-params target db query]} _ _]
  (when-let [store-id (:store-id route-params)]
    (if target
      {:remote true}
      {:value (db/pull-one-with db query {:where   '[[?s :store/stripe ?e]]
                                          :symbols {'?s store-id}})})))

(defmethod client-read :query/stripe-customer
  [{:keys [target db query]} _ _]
  (if target
    {:remote true}
    {:value (when-let [user-id (client.auth/current-auth db)]
              (db/pull-one-with db query {:where   '[[?u :user/stripe ?e]]
                                          :symbols {'?u user-id}}))}))

(defmethod client-read :query/stripe-country-spec
  [{:keys [route-params target db]} _ _]
  (if target
    {:remote true}
    {:value (db/lookup-entity db (db/one-with db {:where '[[?e :country-spec/id]]}))}))

(defmethod client-read :query/stripe
  [{:keys [db query target route-params]} _ _]
  (if target
    {:remote true}
    {:value (db/pull-one-with db query {:where '[[?e :stripe/id]]})}))

(defmethod client-read :query/cart
  [{:keys [db query target ast]} _ _]
  (if target
    {:remote true}
    (let [user-id (client.auth/current-auth db)
          [_ cart] (client.cart/find-user-cart db user-id)]
      {:value (db/pull db query cart)})))

(defmethod client-read :query/skus
  [{:keys [target]} _ _]
  (when target
    {:remote true}))
;(common.read/compute-cart-price cart)

(defmethod client-read :query/checkout
  [{:keys [db query route-params target]} _ _]
  (when-let [store-id (:store-id route-params)]
    (if target
      {:remote true}
      {:value (do (debug "Query " query) (db/pull-all-with db query {:where   '[[?u :user/cart ?c]
                                                         [?c :user.cart/items ?e]
                                                         [?i :store.item/skus ?e]
                                                         [?s :store/items ?i]]
                                              :symbols {'?s store-id}}))})))

(defmethod client-read :query/taxes
  [{:keys [db query route-params target]} _ {:keys [destination] :as p}]
  (when-let [store-id (:store-id route-params)]
    (debug "Read query/taxes: " p)
    (if target
      {:remote (when destination true)}
      {:value (db/pull-one-with db query {:where   '[[?e :taxes/id ?s]]
                                          :symbols {'?s store-id}})})))

(defmethod client-read :query/owned-store
  [{:keys [db target query]} _ _]
  (if target
    {:remote true}
    (when-let [user-id (client.auth/current-auth db)]
      {:value (db/pull-one-with db query {:where   '[[?owners :store.owner/user ?user]
                                                     [?e :store/owners ?owners]]
                                          :symbols {'?user user-id}})})))

(defn- add-all-hrefs [category params->route-handler param-order]
  (letfn [(with-hrefs [category parent-names]
            (let [names (conj parent-names (:category/name category))
                  params (zipmap param-order names)]
              (-> category
                  (assoc :category/route-map {:route (params->route-handler params) :route-params params})
                  (update :category/children (fn [children]
                                               (into (empty children) (map #(with-hrefs % names)) children))))))]
    (with-hrefs category [])))

(defn assoc-gender-hrefs [category]
  (add-all-hrefs category
                 (fn [{:keys [top-category sub-category sub-sub-category]}]
                   (or (when sub-sub-category :browse/gender+top+sub-sub)
                       (when top-category :browse/gender+top)
                       (when sub-category :browse/gender)
                       :browse/all-items))
                 [:sub-category :top-category :sub-sub-category]))

(defn assoc-category-hrefs [category]
  (add-all-hrefs category
                 (fn [{:keys [top-category sub-category sub-sub-category]}]
                   (or (when sub-sub-category :browse/category+sub+sub-sub)
                       (when sub-category :browse/category+sub)
                       (when top-category :browse/category)
                       :browse/all-items))
                 [:top-category :sub-category :sub-sub-category]))

(defn navigate-gender [db query gender]
  (let [gender-query (products/category-names-query {:sub-category gender})]
    (when (db/one-with db (db/merge-query gender-query {:where '[[(identity ?sub) ?e]]}))
      (let [query-without-children (into [:db/id] (parser.util/remove-query-key :category/children) query)
            ;; Since our query is flat, it's faster to just select keys from the entity.
            entity-pull (comp (map #(db/entity db %))
                              (map #(select-keys % query-without-children)))

            genders {:category/name     gender
                     :category/label    (str/capitalize gender)
                     :category/children (->> (db/all-with db (db/merge-query gender-query {:where '[[?e :category/children ?sub]]}))
                                             (into []
                                                   (comp
                                                     entity-pull
                                                     (map (fn [category]
                                                            (->> (db/merge-query (products/category-names-query
                                                                                   {:top-category (:category/name category)
                                                                                    :sub-category gender})
                                                                                 {:where '[[?sub :category/children ?e]]})
                                                                 (db/all-with db)
                                                                 (into [] entity-pull)
                                                                 (assoc category :category/children)))))))}]
        #_(assert (every? keyword? query-without-children))
        (assoc-gender-hrefs genders)))))

(defn navigate-category [db query category-name]
  (let [categories (db/pull-one-with db (into [{:category/children '...}]
                                              (parser.util/remove-query-key :category/children)
                                              query)
                                     (db/merge-query (products/category-names-query {:top-category category-name})
                                                     {:where '[[(identity ?top) ?e]]}))]
    (when (some? categories)
      (assoc-category-hrefs categories))))

(defn nav-categories [db query]
  (letfn [(distinct-by-name [category]
            (cond-> category
                    (contains? category :category/children)
                    (update :category/children
                            (fn [children]
                              (into [] (comp (medley/distinct-by :category/name)
                                             (map distinct-by-name))
                                    children)))))]
    (into [] (comp (filter some?) (map distinct-by-name))
          [(navigate-gender db query "women")
           (navigate-gender db query "men")
           (navigate-gender db query "unisex-kids")
           (navigate-category db query "home")
           (navigate-category db query "art")])))

(def memoized-nav-categories
  ;; Don't memoize on jvm clients.
  #?(:clj  nav-categories
     :cljs (let [cache (atom nil)]
             (fn [db query]
               (let [{:keys [last-db last-val last-query] :as c} @cache]
                 (assert (or (nil? last-query) (= query last-query))
                         (str "Query for :query/navigation must always be the same for us to be able to cache it."
                              " Queries weren't: " query " and " (:query c)))
                 (if (and (some? last-val)
                          (or (identical? db last-db)
                              (and (datascript/attr-equal? db last-db :category/name)
                                   (datascript/attr-equal? db last-db :category/label)
                                   (datascript/attr-equal? db last-db :category/path)
                                   (datascript/attr-equal? db last-db :category/children))))
                   last-val
                   (let [ret (nav-categories db query)]
                     (reset! cache {:last-db db :last-val ret :last-query query})
                     ret)))))))

(defmethod client-read :query/navigation
  [{:keys [db target query route-params]} _ _]
  (if target
    {:remote true}
    {:value (nav-categories db query)
     ;(memoized-nav-categories db query)
     }))

(defmethod client-read :query/categories
  [{:keys [db target query route-params]} _ _]
  (if target
    {:remote true}
    {:value (db/pull-all-with db query {:where '[[?e :category/path _]
                                                 [(missing? $ ?e :category/_children)]]})}))

(defmethod client-read :query/item
  [{:keys [db query target route-params]} _ _]
  (when-let [product-id (:product-id route-params)]
    (if target
      {:remote true}
      {:value (db/pull db query product-id)})))

(defmethod client-read :query/auth
  [{:keys [target db query]} _ _]
  ;(debug "Read query/auth: ")
  (if target
    {:remote true}
    {:value (let [query (or query [:db/id])
                  user (client.auth/current-auth db)]
              (db/pull db query user))}))

(defmethod client-read :query/auth0-info
  [{:keys [target db query]} _ _]
  (if target
    {:remote true}
    {:value (db/singleton-value db :ui.singleton.auth/auth0)}))

(defmethod client-read :query/locations
  [{:keys [target db query]} _ _]
  (if target
    {:remote true}
    {:value (client.auth/current-locality db)}))

(defmethod client-read :query/stream
  [{:keys [db query target route-params]} _ _]
  (when-let [store-id (:store-id route-params)]
    (if target
      {:remote true}
      {:value (db/pull-one-with db query {:where   '[[?e :stream/store ?store-id]]
                                          :symbols {'?store-id store-id}})})))

(defmethod client-read :query/stream-config
  [{:keys [db query target]} k _]
  (if target
    {:remote true}
    {:value (db/pull-one-with db query
                              {:where '[[?e :ui/singleton :ui.singleton/stream-config]]})}))

(defmethod client-read :query/current-route
  [{:keys [db]} _ _]
  {:value (client.routes/current-route db)})

(defmethod client-read :routing/app-root
  [{:keys [db] :as env} k p]
  (let [current-route (client.routes/current-route db)]
    (debug "Reading app-root: " [k :route current-route])
    (parser.util/read-union env k p (router/normalize-route (:route current-route)))))

(defmethod client-read :routing/store-dashboard
  [{:keys [db] :as env} k p]
  (let [current-route (client.routes/current-route db)
        parse-route (fn [route]
                      (let [ns (namespace route)
                            subroute (name route)
                            path (clojure.string/split subroute #"#")]
                        (keyword ns (first path))))]
    (debug "Reading routing/store-dashboard: " [k :route current-route (parse-route (:route current-route))])
    (parser.util/read-union env k p (parse-route (:route current-route)))))

(defmethod client-read :query/messages
  [{:keys [db target]} _ _]
  (when (nil? target)
    {:value (parser/get-messages db)}))

(defmethod client-read :query/chat
  [{:keys [target db route-params query]} _ _]
  (when-let [store-id (:store-id route-params)]
    (if (some? target)
      {:remote/chat true}
      {:value (when-let [chat-db (client.chat/get-chat-db db)]
                (let [{:keys [sulo-db-tx chat-db-tx]}
                      (client.chat/read-chat chat-db
                                             db
                                             query
                                             {:db/id store-id}
                                             nil)
                      _ (when (seq sulo-db-tx)
                          (assert (every? #(contains? % :db/id) sulo-db-tx)
                                  (str "sulo-db-tx (users) did not have :db/id's in them. Was: " sulo-db-tx)))
                      users-by-id (into {} (map (juxt :db/id identity)) sulo-db-tx)]
                  ;; This would be a perfect time for specter
                  ;;  (comp (mapcat :chat/messages)
                  ;; (map :chat.message/user)
                  ;; (map :db/id))
                  (cond-> chat-db-tx
                          (contains? chat-db-tx :chat/messages)
                          (update :chat/messages
                                  (fn [messages]
                                    (into []
                                          (map (fn [message]
                                                 (update message :chat.message/user
                                                         (fn [{:keys [db/id]}]
                                                           (assoc (get users-by-id id) :db/id id)))))
                                          messages))))))})))

(defmethod client-read :datascript/schema
  [{:keys [target]} _ _]
  (when target
    {:remote true}))

(defmethod client-read :query/countries
  [{:keys [target query db]} _ _]
  (if target
    {:remote true}
    {:value (db/pull-all-with db query {:where '[[?e :country/code _]]})}))

(defmethod client-read :query/product-search
  [{:keys [target]} _ _]
  (when target
    {:remote true}))

(defmethod client-read :query/client-env
  [{:keys [target]} _ _]
  (when target
    {:remote true}))

(defmethod client-read :query/sulo-localities
  [{:keys [target db query]} _ _]
  (if target
    {:remote true}
    {:value (db/pull-all-with db query {:where '[[?e :sulo-locality/title _]]})}))

(defmethod client-read :query/browse-products-2
  [{:keys [target db query route-params query-params]} _ _]
  (if target
    {:remote true}
    (let [locality (client.auth/current-locality db)
          browse-params (browse/make-browse-params locality route-params query-params)
          browse-result (some->> (browse/find-result db browse-params)
                                 (db/entity db))]
      (when (some? browse-result)
        (let [items (into [] (browse/page-items browse-result (:page-range browse-params)))
              pulled (db/pull-many db query items)
              pulled (if (== (count pulled) (count items))
                       pulled
                       (let [pulled-by-id (into {} (map (juxt :db/id identity)) pulled)]
                         (into [] (map (fn [item-id]
                                         (or (get pulled-by-id item-id)
                                             {:store.item/photos []
                                              :store.item/name   "loading..."
                                              :store.item/price  nil})))
                               items)))]
          (debug "browse-result!: " (into {:db/id (:db/id browse-result)} browse-result))
          {:value {:browse-result (into {:db/id (:db/id browse-result)} browse-result)
                   :items         pulled}})))))

(defmethod client-read :query/browse-product-items
  [{:keys [target]} _ _]
  ;; Only for fetching product items, nothing else.
  (when target
    {:remote true}))