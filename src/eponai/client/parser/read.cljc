(ns eponai.client.parser.read
  (:require
    [clojure.set :as set]
    [eponai.common.parser :as parser :refer [client-read]]
    [eponai.common.parser.util :as parser.util]
    [eponai.common.database :as db]
    [eponai.common.business.budget :as business.budget]
    [eponai.common.parser.read :as common.read]
    [om.next.impl.parser :as om.parser]
    [eponai.client.routes :as client.routes]
    [eponai.common.ui.router :as router]
    [eponai.client.parser.message :as msg]
    [eponai.common :as c]
    [taoensso.timbre :refer [debug]]
    [eponai.client.auth :as auth]
    [eponai.common.api.products :as products]))

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
        store (db/pull db query store-id)]
    (if target
      {:remote (assoc-in ast [:params :store-id] store-id)}
      {:value (common.read/multiply-store-items store)})))

(defmethod client-read :query/store-items
  [{:keys [db query target ast route-params]} _ _]
  (let [store-id (c/parse-long (:store-id route-params))
        navigation (:navigation route-params)]
    (debug "REad store items with nav: " navigation " store- id : ")
    (if target
      {:remote (-> ast
                   (assoc-in [:params :store-id] store-id)
                   (assoc-in [:params :navigation] navigation))}
      {:value (let [params (if (not-empty navigation)
                             {:where   '[[?s :store/items ?e]
                                         [?e :store.item/navigation ?n]
                                         [?n :store.navigation/path ?p]]
                              :symbols {'?s store-id
                                        '?p navigation}}
                             {:where   '[[?s :store/items ?e]]
                              :symbols {'?s store-id}})]
                (db/pull-all-with db query params))})))

(defmethod client-read :query/orders
  [{:keys [db query target ast route-params] :as env} _ _]
  (let [{:keys [store-id user-id]} route-params
        store-id (when store-id (c/parse-long store-id))
        user-id (when user-id (c/parse-long user-id))]
    (if target
      {:remote (-> ast
                   (assoc-in [:params :store-id] store-id)
                   (assoc-in [:params :user-id] user-id))}
      {:value (cond (some? store-id)
                    (db/pull-all-with db '[*] {:where   '[[?e :order/store ?s]]
                                               :symbols {'?s store-id}})
                    (some? user-id)
                    (db/pull-all-with db '[* {:order/store [:store/name {:store/photo [:photo/path]}]}] {:where   '[[?e :order/user ?u]]
                                                                                                         :symbols {'?u user-id}}))})))

(defmethod client-read :query/inventory
  [{:keys [db query target ast route-params] :as env} _ _]
  (let [store-id (c/parse-long (:store-id route-params))]
    (if target
      {:remote (assoc-in ast [:params :store-id] store-id)}
      {:value (db/pull-all-with db '[*] {:where '[[?store :store/items ?e]]
                                         :symbols {'?store store-id}})})))

(defmethod client-read :query/order
  [{:keys [db query target ast route-params] :as env} _ _]
  (let [{:keys [order-id store-id user-id]} route-params
        store-id (when store-id (c/parse-long store-id))
        user-id (when user-id (c/parse-long user-id))]
    (if target
      {:remote (when order-id (-> ast
                                  (assoc-in [:params :store-id] store-id)
                                  (assoc-in [:params :user-id] user-id)
                                  (assoc-in [:params :order-id] (c/parse-long order-id))))}
      {:value (when order-id
                (db/pull db query (c/parse-long order-id)))})))

(defmethod client-read :query/my-store
  [{:keys [db query target ast route-params] :as env} _ _]
  (let [user-eid (auth/current-auth db)
        store (when user-eid
                (db/pull-one-with db query {:where   '[[?e :store/owners ?owner]
                                                       [?owner :store.owner/user ?user]]
                                            :symbols {'?user user-eid}}))]
    (if target
      {:remote true}
      {:value (common.read/multiply-store-items store)})))

(defmethod client-read :query/stripe-account
  [{:keys [route-params ast target db query]} _ _]
  (let [{:keys [store-id]} route-params
        store-id (when store-id (c/parse-long store-id))]
    (if target
      {:remote (assoc-in ast [:params :store-id] store-id)}
      {:value (when store-id
                (db/pull-one-with db query {:where   '[[?s :store/stripe ?e]]
                                            :symbols {'?s store-id}}))})))

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
  (let [user-id (c/parse-long (:user-id route-params))
        user (db/pull-one-with db query {:where   '[[?e]]
                                         :symbols {'?e user-id}})]
    (if target
      {:remote (assoc-in ast [:params :user-id] user-id)}
      {:value user})))

(defmethod client-read :query/route-params
  [{:keys [route-params]} _ _]
  {:value route-params})

(defmethod client-read :query/cart
  [{:keys [db query target ast]} _ _]
  (let [cart (db/pull-one-with db query {:where '[[?e :cart/items]]})]
    (if target
      {:remote true }
      {:value cart})))                                      ;(common.read/compute-cart-price cart)

(defmethod client-read :query/items
  [{:keys [db query target route-params ast] :as env} _ p]
  (let [{:keys [category search]} route-params]
    (if target
      {:remote (assoc-in ast [:params :category] category)}
      {:value (cond (some? category)
                    (db/pull-all-with db query (products/find-by-category category))
                    :else
                    (db/pull-all-with db query (products/find-all)))})))

(defmethod client-read :query/top-categories
  [{:keys [db target query route-params]} _ {:keys [category search] :as p}]
  (if target
    {:remote true}
    {:value (db/pull-all-with db query {:where '[[?e :category/level 0]]})}))

(defmethod client-read :query/category
  [{:keys [db query target route-params ast] :as env} _ p]
  (let [{:keys [category]} route-params]
    (if target
      {:remote (when (not-empty category) (assoc-in ast [:params :category] category))}
      {:value (db/pull-one-with db query {:where   '[[?e :category/path ?p]]
                                 :symbols {'?p category}})})))

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

(defmethod client-read :query/stream
  [{:keys [db query target ast route-params] :as env} _ _]
  (let [store-id (c/parse-long (:store-id route-params))]
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
                             {:where '[[?e :ui/singleton :ui.singleton/stream-config]]})})

(defmethod client-read :query/current-route
  [{:keys [db query-params]} k p]
  {:value (cond-> (client.routes/current-route db)
                  (some? query-params)
                  (assoc :query-params query-params))})

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

(defmethod client-read :query/business-model
  [e k p]
  {:value business.budget/world})

(defmethod client-read :query/messages
  [{:keys [db]} _ _]
  {:value (parser/get-messages db)})

(defmethod client-read :query/chat
  [{:keys [ast target db route-params query]} _ _]
  (let [store-id (c/parse-long (:store-id route-params))]
    (if (some? target)
      {:remote/chat (assoc-in ast [:params :store :db/id] store-id)}
      {:value (db/pull-one-with db query {:where   '[[?e :chat/store ?store-id]]
                                          :symbols {'?store-id store-id}})})))