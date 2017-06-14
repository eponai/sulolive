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
    [eponai.client.chat :as client.chat]))

;; ################ Local reads  ####################
;; Generic, client only local reads goes here.
(defmethod client-read :query/ui-state
  [{:keys [db query target ast route-params] :as env} _ _]
  {:value (db/pull-one-with db query {:where '[[?e :ui/singleton :ui.singleton/state]]})})


;; ################ Remote reads ####################
;; Remote reads goes here. We share these reads
;; with all client platforms (web, ios, android).
;; Local reads should be defined in:
;;     eponai.<platform>.parser.read

;; ----------

(defmethod client-read :query/store
  [{:keys [db query target ast route-params] :as env} _ _]
  (when-let [store-id (c/parse-long-safe (:store-id route-params))]
    (if target
      {:remote (assoc-in ast [:params :store-id] store-id)}
      {:value (db/pull db query store-id)})))

(defmethod client-read :query/stores
  [{:keys [db query target]} _ _]
  ;(debug "Read query/auth: ")
  (if target
    {:remote true}
    {:value (db/pull-all-with db query {:where '[[?s :stream/state ?states]
                                                 [?s :stream/store ?e]]
                                        :symbols {'[?states ...] [:stream.state/online
                                                                 :stream.state/offline]}})}))

(defmethod client-read :query/store-items
  [{:keys [db query target ast route-params]} _ _]
  (when-let [store-id (c/parse-long-safe (:store-id route-params))]
    (let [navigation (:navigation route-params)]
      (debug "REad store items with nav: " navigation " store- id : ")
      (if target
        {:remote (-> ast
                     (assoc-in [:params :store-id] store-id)
                     (assoc-in [:params :navigation] navigation))}
        {:value (let [params (if (not-empty navigation)
                               {:where   '[[?s :store/items ?e]
                                           [?e :store.item/section ?n]
                                           [?n :store.section/path ?p]]
                                :symbols {'?s store-id
                                          '?p navigation}}
                               {:where   '[[?s :store/items ?e]]
                                :symbols {'?s store-id}})]
                  (db/pull-all-with db query params))}))))

(defmethod client-read :query/store-item-count
  [{:keys [db target route-params]} _ _]
  (when-let [store-id (c/parse-long-safe (:store-id route-params))]
    (if target
      {:remote (om.parser/expr->ast (list {:query/store [{:store/items [:store.item/name]}]} {:store-id store-id}))}
      {:value (db/find-with db {:find    '[(count ?e) .]
                                :where   '[[?store :store/items ?e]]
                                :symbols {'?store store-id}})})))

(defmethod client-read :query/orders
  [{:keys [db query target ast route-params] :as env} _ _]
  (let [{:keys [store-id]} route-params
        store-id (c/parse-long-safe store-id)]
    (if target
      {:remote (-> ast (assoc-in [:params :store-id] store-id))}
      {:value (if (some? store-id)
                (db/pull-all-with db query {:where   '[[?e :order/store ?s]]
                                            :symbols {'?s store-id}})
                (when-let [user-id (client.auth/current-auth db)]
                  (db/pull-all-with db query {:where   '[[?e :order/user ?u]]
                                              :symbols {'?u user-id}})))})))

(defmethod client-read :query/inventory
  [{:keys [db query target ast route-params] :as env} _ _]
  (when-let [store-id (c/parse-long-safe (:store-id route-params))]
    (if target
      {:remote (assoc-in ast [:params :store-id] store-id)}
      {:value (db/pull-all-with db '[*] {:where   '[[?store :store/items ?e]]
                                         :symbols {'?store store-id}})})))

(defn map-all-keys
  "returns nil when all keys couldn't be selected."
  [map-fn in-map ks]
  (let [res (into [] (comp
                       (map #(get in-map %))
                       (map map-fn)
                       (take-while some?))
                  ks)]
    (when (== (count res)
              (count ks))
      res)))

(defmethod client-read :query/order
  [{:keys [db query target ast route-params] :as env} _ _]
  (let [store-id (c/parse-long-safe (:store-id route-params))
        order-id (c/parse-long-safe (:order-id route-params))]
    (when (some? order-id)
      (if target
        {:remote (-> ast
                     (assoc-in [:params :store-id] store-id)
                     (assoc-in [:params :order-id] order-id))}
        {:value (db/pull db query order-id)}))))

(defmethod client-read :query/order-payment
  [{:keys [db query target ast route-params] :as env} _ _]
  (let [store-id (c/parse-long-safe (:store-id route-params))
        order-id (c/parse-long-safe (:order-id route-params))]
    (when (some? order-id)
      (if target
        {:remote (-> ast
                     (assoc-in [:params :store-id] store-id)
                     (assoc-in [:params :order-id] order-id))}
        {:value (db/pull-one-with db query {:where   '[[?o :order/charge ?e]]
                                            :symbols {'?o order-id}})}))))

(defmethod client-read :query/stripe-account
  [{:keys [route-params ast target db query]} _ _]
  (when-let [store-id (c/parse-long-safe (:store-id route-params))]
    (if target
      {:remote (assoc-in ast [:params :store-id] store-id)}
      {:value (db/pull-one-with db query {:where   '[[?s :store/stripe ?e]]
                                          :symbols {'?s store-id}})})))

(defmethod client-read :query/stripe-balance
  [{:keys [route-params ast target db query]} _ _]
  (when-let [store-id (c/parse-long-safe (:store-id route-params))]
    (if target
      {:remote (assoc-in ast [:params :store-id] store-id)}
      {:value (db/pull-one-with db query {:where   '[[?s :store/stripe ?e]]
                                          :symbols {'?s store-id}})})))

(defmethod client-read :query/stripe-customer
  [{:keys [ast target db query]} _ _]
  (if target
    {:remote true}
    {:value (when-let [user-id (client.auth/current-auth db)]
              (db/pull-one-with db query {:where   '[[?u :user/stripe ?e]]
                                          :symbols {'?u user-id}}))}))

(defmethod client-read :query/stripe-country-spec
  [{:keys [route-params ast target db]} _ _]
  (if target
    {:remote true}
    {:value (db/lookup-entity db (db/one-with db {:where '[[?e :country-spec/id]]}))}))

(defmethod client-read :query/stripe
  [{:keys [db query target ast route-params] :as env} _ _]
  (if target
    {:remote true}
    {:value (db/pull-one-with db query {:where '[[?e :stripe/id]]})}))

(defmethod client-read :query/user
  [{:keys [db query target ast route-params] :as env} _ _]
  (when-let [user-id (c/parse-long-safe (:user-id route-params))]
    (if target
      {:remote (assoc-in ast [:params :user-id] user-id)}
      {:value (db/pull-one-with db query {:where   '[[?e]]
                                          :symbols {'?e user-id}})})))

(defmethod client-read :query/route-params
  [{:keys [route-params]} _ _]
  {:value route-params})

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
  [{:keys [db query route-params target ast]} _ _]
  (when-let [store-id (c/parse-long-safe (:store-id route-params))]
    (if target
      {:remote (assoc-in ast [:params :store-id] store-id)}
      {:value (db/pull-all-with db query {:where   '[[?u :user/cart ?c]
                                                     [?c :user.cart/items ?e]
                                                     [?i :store.item/skus ?e]
                                                     [?s :store/items ?i]]
                                          :symbols {'?s store-id}})})))

(defmethod client-read :query/browse-items
  [{:keys [db target query route-params ast query-params]} _ _]
  (let [{:keys [top-category sub-category]} route-params]
    (if target
      {:remote (-> ast
                   (assoc-in [:params :route-params] route-params)
                   (assoc-in [:params :query-params] query-params))}
      {:value (db/pull-all-with db query (cond
                                           (seq (:search query-params))
                                           (products/find-with-search (:search query-params))
                                           (or (some? sub-category) (some? top-category))
                                           (products/find-with-category-names route-params)
                                           :else
                                           (products/find-all)))})))

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
                  (assoc :category/href (client.routes/url (params->route-handler params) params))
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
                             (map #(select-keys % query-without-children)))]
       #_(assert (every? keyword? query-without-children))
       (-> {:category/name     gender
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
                                                        (assoc category :category/children)))))))}
           (assoc-gender-hrefs))))))

(defn navigate-category [db query category-name]
  (some-> (db/pull-one-with db (into [{:category/children '...}]
                                     (parser.util/remove-query-key :category/children)
                                     query)
                            (db/merge-query (products/category-names-query {:top-category category-name})
                                            {:where '[[(identity ?top) ?e]]}))
          (assoc-category-hrefs)))

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
    {:value (memoized-nav-categories db query)}))

(defmethod client-read :query/item
  [{:keys [db query target route-params ast]} _ _]
  (when-let [product-id (c/parse-long-safe (:product-id route-params))]
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
                  user (client.auth/current-auth db)]
              (db/pull db query user))}))

(defmethod client-read :query/locations
  [{:keys [target db query]} _ _]
  (if target
    {:remote true}
    {:value (-> (db/lookup-entity db [:ui/singleton :ui.singleton/auth])
                :ui.singleton.auth/locations)})
  ;(debug "Read query/auth: ")

  ;#?(:cljs
  ;   {:value (let [cookie-string (js/decodeURIComponent (.-cookie js/document))
  ;                 key-vals (string/split cookie-string #";")
  ;                 locality-str (some #(when (string/starts-with? (string/trim %) "locality=") %) key-vals)
  ;                 locality (when locality-str
  ;                            (second (string/split locality-str #"=")))]
  ;             locality)})
  )

(defmethod client-read :query/stream
  [{:keys [db query target ast route-params] :as env} _ _]
  (when-let [store-id (c/parse-long-safe (:store-id route-params))]
    (if target
      {:remote (assoc-in ast [:params :store-id] store-id)}
      {:value (db/pull-one-with db query {:where   '[[?e :stream/store ?store-id]]
                                          :symbols {'?store-id store-id}})})))

(defmethod client-read :query/streams
  [{:keys [db query target]} _ _]
  ;(debug "Read query/auth: ")
  (if target
    {:remote true}
    {:value (db/pull-all-with db query {:where '[[?e :stream/state :stream.state/live]]})}))

; ### FEATURED ###  ;

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
             (sort-by :store.item/created-at > (db/pull-many db query items)))})

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
                        :store/featured-img-src [img-1 (:store.profile/photo (:store/profile s)) img-2]}))]
             (sort-by :store/created-at
                      >
                      (into [] (comp (map photos-fn)
                                     (map #(merge % (db/pull db query (:db/id %)))))
                            (db/all-with db {:where '[[?e :store/featured]]}))))})

(defmethod client-read :query/stream-config
  [{:keys [db query]} k _]
  {:remote true
   :value  (db/pull-one-with db query
                             {:where '[[?e :ui/singleton :ui.singleton/stream-config]]})})

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
  [{:keys [db]} _ _]
  {:value (parser/get-messages db)})

(defmethod client-read :query/chat
  [{:keys [ast target db route-params query]} _ _]
  (when-let [store-id (c/parse-long-safe (:store-id route-params))]
    (if (some? target)
      {:remote/chat (assoc-in ast [:params :store :db/id] store-id)}
      {:value (when-let [chat-db (db/singleton-value db :ui.singleton.chat-config/chat-db)]
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
                  (update chat-db-tx :chat/messages
                          (fn [messages]
                            (into []
                                  (map (fn [message]
                                         (update message :chat.message/user
                                                 (fn [{:keys [db/id]}]
                                                   (assoc (get users-by-id id) :db/id id)))))
                                  messages)))))})))

(defmethod client-read :query/loading-bar
  [{:keys [db query target]} _ _]
  (when-not target
    {:value (db/pull-one-with db query {:where '[[?e :ui/singleton :ui.singleton/loading-bar]]})}))

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