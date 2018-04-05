(ns eponai.client.parser.read
  (:require
    [clojure.string :as str]
    [clojure.set :as set]
    [om.next.impl.parser :as om.parser]
    [eponai.common.parser :as parser :refer [client-read lajt-read]]
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

(defn one [m]
  (assoc m :find '[?e .]))

(defn all [m]
  (assoc m :find '[[?e ...]]))

;; ################ Local reads  ####################
;; Generic, client only local reads goes here.
(defmethod client-read :query/ui-state
  [{:keys [db query target]} _ _]
  (when (nil? target)
    {:value (db/pull-one-with db query {:where '[[?e :ui/singleton :ui.singleton/state]]})}))
(defmethod lajt-read :query/ui-state
  [_]
  {:query '{:find  [?e .]
            :where [[?e :ui/singleton :ui.singleton/state]]}})

(defmethod client-read :query/loading-bar
  [{:keys [db query target]} _ _]
  (when-not target
    {:value (do (debug "Query/loading bar: " query) (db/pull-one-with db query {:where '[[?e :ui/singleton :ui.singleton/loading-bar]]}))}))
(defmethod lajt-read :query/loading-bar
  [_]
  {:query '{:find [?e .]
            :where [[?e :ui/singleton :ui.singleton/loading-bar]]}
   :before (fn [{:keys [query]}]
             (debug "Query/loading bar: " query))})

(defmethod client-read :query/login-modal
  [{:keys [db query target]} _ _]
  (when-not target
    {:value (db/pull-one-with db query {:where '[[?e :ui/singleton :ui.singleton/login-modal]]})}))
(defmethod lajt-read :query/login-modal
  [_]
  {:query '{:find  [?e .]
            :where [[?e :ui/singleton :ui.singleton/login-modal]]}})

(defmethod client-read :query/notifications
  [{:keys [db query target]} _ _]
  (when-not target
    {:value (db/pull-all-with db query {:where '[[?n :ui/singleton :ui.singleton/notify]
                                                 [?n :ui.singleton.notify/notifications ?e]]})}))
(defmethod lajt-read :query/login-modal
  [_]
  {:query '{:find  [[?e ...]]
            :where [[?n :ui/singleton :ui.singleton/notify]
                    [?n :ui.singleton.notify/notifications ?e]]}})

;; ################ Remote reads ####################
;; Remote reads goes here. We share these reads
;; with all client platforms (web, ios, android).
;; Local reads should be defined in:
;;     eponai.<platform>.parser.read

;; ----------

;; ################# Browsing reads

(defmethod client-read :query/stores
  [{:keys [db query target]} _ {:keys [states]}]
  (if target
    {:remote true}
    {:value (db/pull-all-with db query {:where '[
                                                 [?st :status/type :status.type/open]
                                                 [?e :store/status ?st]]})}))
(defmethod lajt-read :query/stores
  [_]
  {:remote true
   :query  '{:find  [[?e ...]]
             :where [[?st :status/type :status.type/open]
                     [?e :store/status ?st]]}})

(defmethod client-read :query/store-has-streamed
  [{:keys [db query target]} _ {:keys [states]}]
  (if target
    {:remote true}
    {:value (db/pull db query [:ui/singleton :ui.singleton/state])}))
(defmethod lajt-read :query/store-has-streamed
  [_]
  ;; TODO lajt: implement lookup-ref
  {:remote true
   :lookup-ref [:ui/singleton :ui.singleton/state]})


(defmethod client-read :query/streams
  [{:keys [db query target]} _ _]
  (if target
    {:remote true}
    {:value (db/pull-all-with db query {:where   '[
                                                   [?st :status/type :status.type/open]
                                                   [?s :store/status ?st]
                                                   [?e :stream/store ?s]
                                                   [?e :stream/state :stream.state/live]]})}))
(defmethod lajt-read :query/streams
  [_]
  {:remote true
   :query  '{:find  [[?e ...]]
             :where [[?st :status/type :status.type/open]
                     [?s :store/status ?st]
                     [?e :stream/store ?s]
                     [?e :stream/state :stream.state/live]]}})

;; Deprecated
(defmethod client-read :query/browse-items
  [{:keys [db target query route-params query-params]} _ _]
  (if target
    {:remote true}
    {:value (let [{:keys [top-category sub-category]} route-params]
              (db/pull-all-with db query (cond
                                           (seq (:search query-params))
                                           (products/find-with-search (:search query-params))
                                           (or (some? sub-category) (some? top-category))
                                           (products/find-with-category-names route-params)
                                           :else
                                           (products/find-all))))}))

;; This could also be a union.
;; Since the query is the same for all of these, one could write a union like this:
'({:or/browse-items {:a [:a-single-read]
                     :b [:or-this-read]}}
   {:query [:a :b :c]})
;; Not great.
;; How could we do it in the read-map?
;; Maybe - since we either way need to check if the params are the same - make
;; it possible for the query to be a function that returns a query.
;; We could check that it's the same query (or a new query) when we're looking at the cache.
;; More complexity though.
;; hmm.
;; Is it weird for the UI component to describe the different states of the UI?
;; Maybe not?
;; Maybe this union query is better?


;; ----- Featured

(defn pull-featured [db query entity-query]
  (when-not (= '[?e ?featured] (:find entity-query))
    (warn "featured entity-query did not have '[?e ?featured] as :find. Was: " (:find entity-query)
          " entity-query: " entity-query))
  (->> (db/find-with db entity-query)
       (sort-by #(nth % 1) #(compare %2 %1))
       (map #(nth % 0))
       (db/pull-many db query)))

(defn query-featured [featured-key where-clauses]
  {:query {:find '[[?e ...]]
            :where where-clauses}
   :sort {:key-fn featured-key
          :order :decending}})

(defmethod client-read :query/top-streams
  [{:keys [db query]} _ _]
  {:remote true
   :value  (pull-featured db query {:find  '[?e ?featured]
                                    :where '[[?e :stream/featured ?featured]
                                             [?e :stream/store ?s]]})})
(defmethod lajt-read :query/top-streams
  [_]
  (merge
    {:remote true}
    (query-featured :stream/featured
                    '[[?e :stream/featured _]
                      [?e :stream/store ?s]])))

(defmethod client-read :query/featured-streams
  [{:keys [db query]} _ _]
  {:remote true
   :value  (pull-featured db query {:find    '[?e ?featured]
                                    :where   '[[?e :stream/featured ?featured]
                                               [?e :stream/store ?s]]})})
(defmethod lajt-read :query/featured-streams
  [_]
  (merge
    {:remote true}
    (query-featured :stream/featured
                    '[[?e :stream/featured _]
                      [?e :stream/store ?s]])))

(defmethod client-read :query/featured-items
  [{:keys [db query]} _ _]
  {:remote true
   :value  (pull-featured db query {:find    '[?e ?featured]
                                    :where   '[
                                               [?s :store/items ?e]
                                               [?e :store.item/featured ?featured]]})})

(defmethod lajt-read :query/featured-items
  [_]
  (merge
    {:remote true}
    (query-featured :store.item/featured
                    '[[_ :store/items ?e]
                      [?e :store.item/featured _]])))

(defn featured-category [category-map]
  (query-featured :store.item/featured
                  (-> (products/find-with-category-names category-map)
                      (db/merge-query {:where '[[?e :store.item/featured _]]})
                      (:where))))

(defmethod client-read :query/featured-women
  [{:keys [db query]} _ _]
  {:remote true
   :value  (pull-featured db query (-> (products/find-with-category-names {:sub-category "women"})
                                       (db/merge-query {:find '[?e ?featured]
                                                        :where '[[?e :store.item/featured ?featured]]})))})
(defmethod lajt-read :query/featured-women
  [_]
  (merge
    {:remote true}
    (featured-category {:sub-category "women"})))

(defmethod client-read :query/featured-men
  [{:keys [db query]} _ _]
  {:remote true
   :value  (pull-featured db query (-> (products/find-with-category-names {:sub-category "men"})
                                       (db/merge-query {:find '[?e ?featured]
                                                        :where '[[?e :store.item/featured ?featured]]})))})
(defmethod lajt-read :query/featured-men
  [_]
  (merge
    {:remote true}
    (featured-category {:sub-category "men"})))


(defmethod client-read :query/featured-home
  [{:keys [db query]} _ _]
  {:remote true
   :value  (pull-featured db query (-> (products/find-with-category-names {:top-category "home"})
                                       (db/merge-query {:find '[?e ?featured]
                                                        :where '[[?e :store.item/featured ?featured]]})))})
(defmethod lajt-read :query/featured-home
  [_]
  (merge
    {:remote true}
    (featured-category {:top-category "home"})))

(defmethod client-read :query/featured-art
  [{:keys [db query]} _ _]
  {:remote true
   :value  (pull-featured db query (-> (products/find-with-category-names {:top-category "art"})
                                       (db/merge-query {:find '[?e ?featured]
                                                        :where '[[?e :store.item/featured ?featured]]})))})
(defmethod lajt-read :query/featured-art
  [_]
  (merge
    {:remote true}
    (featured-category {:top-category "art"})))

(defmethod client-read :query/featured-stores
  [{:keys [db query]} _ _]
  ;; Only fetch featured-stores initially? i.e. (when (nil? db-history) ...)
  ;; TODO: Come up with a way to feature stores. DB SHUFFLE
  {:remote true
   :value  (pull-featured db query {:find    '[?e ?featured]
                                    :where   '[[?e :store/featured ?featured]]})})

(defmethod lajt-read :query/featured-stores
  [_]
  (merge
    {:remote true}
    (query-featured :store/featured '[[?e :store/featured _]])))

;################

(defmethod client-read :query/store
  [{:keys [db query target route-params] :as env} _ _]
  (when-let [store-id (:store-id route-params)]
    (if target
      {:remote true}
      {:value (db/pull db query store-id)})))
(defmethod lajt-read :query/store
  [_]
  {:remote true
   :query '{:find [?e .]}
   ;; What to do when the :store-id doesn't exist?
   ;; In this case the route param is optional. Well.. we shouldn't
   ;; execute the read if it doesn't exist.
   :params {'?e [:route-params :store-id]}})

(defmethod client-read :query/store-vods
  [{:keys [db query target route-params] :as env} _ _]
  (when-let [store-id (:store-id route-params)]
    (if target
      {:remote true}
      {:value (->> (db/pull-all-with db query {:where   '[[?e :vod/store ?store-id]]
                                               :symbols {'?store-id store-id}})
                   (sort-by :vod/timestamp #(compare %2 %1)))})))
(defmethod lajt-read :query/store-vods
  [_]
  {:remote true
   :query  '{:find  [[?e ...]]
             :where [[?e :vod/store ?store-id]]}
   :params {'?store-id [:route-params :store-id]}
   :sort   {:order :decending
            ;; TODO: We want to sort ?e by this key.
            ;; We're depending on that it exist, so we should
            ;; include it in the remote pull-pattern.
            ;; We could include it in the where clauses, but it
            ;; would just make query slower.
            :key-fn :vod/timestamp}})

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

(defmethod lajt-read :query/store-items
  [_]
  ;; TODO lajt: Support functions that return any kind of read-map?
  ;; It'd be nice to get as many use cases into the spec as possible.
  {:base {:remote true
          :query  {:find '[[?e ...]]}
          :params {'?s [:route-params :store-id]}}
   :case [{[[:route-params :navigation seq]]
           {:query  '{:where [[?s :store/items ?e]
                              [?e :store.item/section ?n]
                              [?n :store.section/path ?p]
                              [?e :store.item/name _]]}
            :params {'?p [:route-params :navigation]}}}

          {(constantly true)
           {:query '{:where [[?s :store/items ?e]
                             [?e :store.item/name _]]}}}]})

(defmethod client-read :query/store-item-count
  [{:keys [db target route-params] :as env} _ _]
  (when-let [store-id (:store-id route-params)]
    (if target
      {:remote (om.parser/expr->ast (list {:read/with-state [{:query/store [{:store/items [:store.item/name]}]}]}
                                          {:route-params {:store-id (:store-id (:raw-route-params env))}}))}
      {:value (db/find-with db {:find    '[(count ?e) .]
                                :where   '[[?store :store/items ?e]]
                                :symbols {'?store store-id}})})))
(defmethod lajt-read :query/store-item-count
  [_]
  {:remote     true
   :depends-on [:query/store-items]
   ;; TODO lajt: Needs to support arbitrary function calls in find-pattern.
   :query {:find '[(count ?e) .]}
   :params {'[?e ...] [:depends-on :query/store-items]}})

(.indexOf [1 2 3] 2)

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
;; Maybe this ^^ should be
;; Same pull pattern though.. hmm..
{:union/orders {:store {:query/store-orders []}
                :user  {:query/user-orders []}}}
;; For now we're going with the value as a function:
(defmethod lajt-read :query/orders
  [_]
  (fn [env]
    (when (and (not (get-in env [:route-params :store-id]))
               (nil? (get-in env [:auth :user-id])))
      (warn [:auth :user-id] " was not in env for " :query/orders))
    (merge-with merge
                {:remote true
                 :query  '{:find [[?e ...]]}}
                (if (get-in env [:route-params :store-id])
                  {:query  '{:where [[?e :order/store ?s]]}
                   :params {'?s [:route-params :store-id]}}
                  {:query  '{:where [[?e :order/user ?u]]}
                   ;; Asserts that the :user-id has been put in the env.
                   :params {'?s [:auth :user-id]}}))))

(defmethod client-read :query/inventory
  [{:keys [db query target route-params]} _ _]
  (when-let [store-id (:store-id route-params)]
    (if target
      {:remote true}
      {:value (db/pull-all-with db query {:where   '[[?store :store/items ?e]]
                                          :symbols {'?store store-id}})})))
(defmethod lajt-read :query/inventory
  [_]
  {:remote true
   :query '{:find [[?e ...]]
            :where [[?store :store/items ?e]]}
   :params {'?store [:route-params :store-id]}})

(defmethod client-read :query/order
  [{:keys [db query target route-params]} _ _]
  (when-let [order-id (:order-id route-params)]
    (if target
      {:remote true}
      {:value (db/pull db query order-id)})))
(defmethod lajt-read :query/order
  [_]
  {:remote true
   :query '{:find [?e .]}
   :params {'?e [:route-params :order-id]}})

(defmethod client-read :query/order-payment
  [{:keys [db query target route-params]} _ _]
  (when-let [order-id (:order-id route-params)]
    (if target
      {:remote true}
      {:value (db/pull-one-with db query {:where   '[[?o :order/charge ?e]]
                                          :symbols {'?o order-id}})})))
(defmethod lajt-read :query/order-payment
  [_]
  {:remote true
   :query '{:find [?e .]
            :where [[?o :order/charge ?e]]}
   :params {'?o [:route-params :order-id]}})

(def store-stripe-map
  {:remote true
   :query '{:find [?e .]
            :where [[?s :store/stripe ?e]]}
   :params {'?s [:route-params :store-id]}})

(defmethod client-read :query/stripe-account
  [{:keys [route-params target db query]} _ _]
  (when-let [store-id (:store-id route-params)]
    (if target
      {:remote true}
      {:value (db/pull-one-with db query {:where   '[[?s :store/stripe ?e]]
                                          :symbols {'?s store-id}})})))
(defmethod lajt-read :query/stripe-account
  [_]
  store-stripe-map)

(defmethod client-read :query/stripe-balance
  [{:keys [route-params target db query]} _ _]
  (when-let [store-id (:store-id route-params)]
    (if target
      {:remote true}
      {:value (db/pull-one-with db query {:where   '[[?s :store/stripe ?e]]
                                          :symbols {'?s store-id}})})))
(defmethod lajt-read :query/stripe-balance
  [_]
  store-stripe-map)

(defmethod client-read :query/stripe-payouts
  [{:keys [route-params target db query]} _ _]
  (when-let [store-id (:store-id route-params)]
    (if target
      {:remote true}
      {:value (db/pull-one-with db query {:where   '[[?s :store/stripe ?e]]
                                          :symbols {'?s store-id}})})))

(defmethod lajt-read :query/stripe-payouts
  [_]
  store-stripe-map)

(defmethod client-read :query/stripe-customer
  [{:keys [target db query]} _ _]
  (if target
    {:remote true}
    {:value (when-let [user-id (client.auth/current-auth db)]
              (db/pull-one-with db query {:where   '[[?u :user/stripe ?e]]
                                          :symbols {'?u user-id}}))}))

(defmethod lajt-read :query/stripe-customer
  [_]
  {:remote true
   :query '{:find [?e .]
            :where [[?u :user/stripe ?e]]}
   ;; TODO lajt: Again, using the :auth key.
   ;; We can warn whenever a param does not exist, which key it is and stuff.
   :params {'?u [:auth :user-id]}})

(defmethod client-read :query/stripe-country-spec
  [{:keys [route-params target db]} _ _]
  (if target
    {:remote true}
    {:value (db/lookup-entity db (db/one-with db {:where '[[?e :country-spec/id]]}))}))
(defmethod lajt-read :query/stripe-country-spec
  [_]
  ;; Calls remote without the pull pattern.
  ;; TODO lajt: Must be able to return queries on the :remote key.
  {:remote [:query/stripe-country-spec]
   :query  '{:find  [?e .]
             :where [[?e :country-spec/id]]}})

(defmethod client-read :query/stripe
  [{:keys [db query target route-params]} _ _]
  (if target
    {:remote true}
    {:value (db/pull-one-with db query {:where '[[?e :stripe/id]]})}))
(defmethod lajt-read :query/stripe
  [_]
  {:remote true
   :query  '{:find  [?e .]
             :where [[?e :stripe/id]]}})

(defmethod client-read :query/cart
  [{:keys [db query target ast]} _ _]
  (if target
    {:remote true}
    (let [user-id (client.auth/current-auth db)
          [_ cart] (client.cart/find-user-cart db user-id)]
      {:value (db/pull db query cart)})))
(defmethod lajt-read :query/cart
  [_]
  ;; It seems like we want different queries depending on existance of params.
  ;; TODO lajt: Implement these :base+case maps where the base is merged
  ;; with the matched :case.
  {:base {:remote true
          :query  '{:find  [?e .]
                    :where [[?user :user/cart ?e]]}}
   ;; The keys of :case are either functions or
   ;; these colls of <param-like> map vals.
   :case [{[[:route-params :user-id]]
           {:params {'?user [:route-params]}}}]})

(defmethod client-read :query/skus
  [{:keys [target]} _ _]
  (when target
    {:remote true}))
(defmethod lajt-read :query/skus
  [_]
  {:remote true})
;(common.read/compute-cart-price cart)

(defmethod client-read :query/checkout
  [{:keys [db query route-params target]} _ _]
  (when-let [store-id (:store-id route-params)]
    (if target
      {:remote true}
      {:value (do (debug "Query " query) (db/pull-all-with db query {:where   '[[?u :user/cart ?c]
                                                                                [?c :user.cart/items ?e]
                                                                                [?i :store.item/skus ?e]
                                                                                [?s :store/items ?i]
                                                                                [?s :store/status ?st]
                                                                                [?st :status/type :status.type/open]]
                                                                     :symbols {'?s store-id}}))})))
(defmethod lajt-read :query/checkout
  [_]
  {:remote true
   :query  '{:find  [[?e ...]]
             :where [[?u :user/cart ?c]
                     [?c :user.cart/items ?e]
                     [?i :store.item/skus ?e]
                     [?s :store/items ?i]
                     [?s :store/status ?st]
                     [?st :status/type :status.type/open]]}
   :params {'?s [:route-params :store-id]}})

(defmethod client-read :query/taxes
  [{:keys [db query route-params target]} _ {:keys [destination] :as p}]
  (when-let [store-id (:store-id route-params)]
    (debug "Read query/taxes: " p)
    (if target
      {:remote (when destination true)}
      {:value (db/pull-one-with db query {:where   '[[?e :taxes/id ?s]]
                                          :symbols {'?s store-id}})})))
(defmethod lajt-read :query/taxes
  [_]
  {:remote #(some? (get-in % [:params :destination]))
   :query '{:find [?e .]
            :where [[?e :taxes/id ?s]]}
   :params {'?s [:route-params :store-id]}})

(defmethod client-read :query/owned-store
  [{:keys [db target query]} _ _]
  (if target
    {:remote true}
    (when-let [user-id (client.auth/current-auth db)]
      {:value (db/pull-one-with db query {:where   '[[?owners :store.owner/user ?user]
                                                     [?e :store/owners ?owners]]
                                          :symbols {'?user user-id}})})))
(defmethod lajt-read :query/owned-store
  [_]
  {:remote true
   :query '{:find [?e .]
            :where [[?owners :store.owner/user ?user]
                    [?e :store/owners ?owners]]}
   :params {'?user [:auth :user-id]}})


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

(defmethod lajt-read :query.navigation/genders
  [_]
  {:query (db/merge-query
            (products/category-names-query {:sub-category ["women" "men" "unisex-kids"]})
            '{:find  [?sub-name ?parent ?sub-sub]
              :where [[?parent :category/children ?sub]
                      [?parent :category/path ?top-name]
                      [?sub :category/children ?sub-sub]]})
   :after (fn [{:keys [result query db db-fns]}]
            (let [query-wo-kids (into [:db/id]
                                      (parser.util/remove-query-key :category/children)
                                      query)]
              (->> result
                   (group-by (juxt first second))
                   (map (fn [[[sub-name parent] tripples]]
                          (let [sub-subs (map #(nth 2 %) tripples)
                                parent ((:pull db-fns) db query-wo-kids parent)]
                            [sub-name (assoc parent :category/children
                                                    ((:pull-many db-fns) db query-wo-kids sub-subs))])))
                   (group-by first)
                   (map (fn [[sub-name results]]
                          (prn "sub-name: " sub-name)
                          (prn "results: " results)
                          (let [parents (map second results)]
                            {:category/name     sub-name
                             :category/label    (str/capitalize sub-name)
                             :category/children parents})))
                   (map assoc-gender-hrefs))))})

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
    {:value (doto (nav-categories db query)
              (#(debug "query/NAVIGATION: " %)))}))

(defmethod lajt-read :query.navigation/categories
  [_]
  {:query (db/merge-query
            '{:find [[?top ...]]}
            (products/category-names-query {:top-category ["home" "art"]}))
   ;; TODO lajt: Introduce :after
   ;; A function that's called after pull on the result.
   ;; Can also be a vector as with route-params and other stuff.
   ;; Unsure what to do with caching here.
   ;; ... actually not sure how to do this query.
   ;; So much manual stuff to them.
   ;; Wait.. maybe it's just a concatenation of this query and the genders read.
   ;; How can one concatenate (or do stuff with) multiple reads?
   :after [:result (partial mapv assoc-category-hrefs)]})

(defmethod lajt-read :query/navigation
  [_]
  {:remote     true
   ;; TODO lajt: :depends-on can be a function
   ;; TODO lajt: :depends-on can pass params and pull-patterns/query.
   #_#_ :depends-on (fn [{:keys [query]}]
                 [{:query.navigation/genders query}
                  {:query.navigation/categories query}])
   ;; Should this be a thing?
   ;; What about caching?
   ;; TODO lajt: Introduce :custom ?
   ;; What's are the rules of :custom?
   ;; Spec is sooo needed for this stuff. So many options.
   :custom     (fn [env]
                 (nav-categories (:db env) (:query env))
                 #_(vec
                   (concat
                     (get-in env [:depends-on :query.navigation/genders])
                     (get-in env [:depends-on :query.navigation/categories]))))})

(defmethod client-read :query/categories
  [{:keys [db target query route-params]} _ _]
  (if target
    {:remote true}
    {:value (db/pull-all-with db query {:where '[[?e :category/path _]
                                                 [(missing? $ ?e :category/_children)]]})}))

(defmethod lajt-read :query/categories
  [_]
  {:remote true
   :query '{:find [[?e ...]]
            :where [[?e :category/path _]
                    ;; TODO lajt: Must be able to index where clauses
                    ;; with (at least some?) functions.
                    [(missing? $ ?e :category/_children)]]}})

(defmethod client-read :query/item
  [{:keys [db query target route-params]} _ _]
  (when-let [product-id (:product-id route-params)]
    (if target
      {:remote true}
      {:value (db/pull db query product-id)})))
(defmethod lajt-read :query/item
  [_]
  {:remote true
   :query '{:find [?e .]}
   :params {'?e [:route-params :product-id]}})

(defmethod client-read :query/auth
  [{:keys [target db query]} _ _]
  ;(debug "Read query/auth: ")
  (if target
    {:remote true}
    {:value (let [query (or query [:db/id])
                  user (client.auth/current-auth db)]
              (db/pull db query user))}))
(defmethod lajt-read :query/auth
  [_]
  {:remote true
   :query '{:find [?e .]}
   :params {'?e [:auth :user-id]}})

(defmethod client-read :query/auth0-info
  [{:keys [target db query]} _ _]
  (if target
    {:remote true}
    {:value (db/singleton-value db :ui.singleton.auth/auth0)}))
(defmethod lajt-read :query/auth0-info
  [_]
  {:remote true
   :query  (db/singleton-value-query :ui.singleton.auth/auth0)})

(defmethod client-read :query/firebase
  [{:keys [target db]} _ _]
  (if target
    {:remote true}
    {:value (db/singleton-value db :ui.singleton.firebase/token)}))
(defmethod lajt-read :query/firebase
  [_]
  {:remote true
   :query  (db/singleton-value-query :ui.singleton.firebase/token)})

(defmethod client-read :query/stream
  [{:keys [db query target route-params]} _ _]
  (when-let [store-id (:store-id route-params)]
    (if target
      {:remote true}
      {:value (db/pull-one-with db query {:where   '[[?e :stream/store ?store-id]]
                                          :symbols {'?store-id store-id}})})))
(defmethod lajt-read :query/stream
  [_]
  {:remote true
   :query {:where '[[?e :stream/store ?store-id]]}
   :params {'?store-id [:route-params :store-id]}})

(defmethod client-read :query/stream-config
  [{:keys [db query target]} k _]
  (if target
    {:remote true}
    {:value (db/pull-one-with db query
                              {:where '[[?e :ui/singleton :ui.singleton/stream-config]]})}))
(defmethod lajt-read :query/stream-config
  [_]
  {:remote true
   :lookup-ref (db/singleton-lookup-ref :ui.singleton/stream-config)})

(defn current-route [x]
  (let [db (db/to-db x)]
    (-> db
        (db/entity [:ui/singleton :ui.singleton/routes])
        (->> (into {}))
        (clojure.set/rename-keys {:ui.singleton.routes/current-route :route
                                  :ui.singleton.routes/route-params  :route-params
                                  :ui.singleton.routes/query-params  :query-params}))))
(defmethod client-read :query/current-route
  [{:keys [db]} _ _]
  {:value (client.routes/current-route db)})
(defmethod lajt-read :query/current-route
  [_]
  {:query '{:find  [(pull ?e [:ui.singleton.routes/current-route
                              :ui.singleton.routes/route-params
                              :ui.singleton.routes/query-params]) .]
            :where [[?e :ui/singleton :ui.singleton/routes]]}
   :after [:result #(clojure.set/rename-keys % {:ui.singleton.routes/current-route :route
                                                :ui.singleton.routes/route-params  :route-params
                                                :ui.singleton.routes/query-params  :query-params})]})

(defmethod client-read :routing/app-root
  [{:keys [db] :as env} k p]
  (let [current-route (client.routes/current-route db)]
    (debug "Reading app-root: " [k :route current-route])
    (if (:lajt.parser/calling-union-selector env)
      {:value (router/normalize-route (:route current-route))}
      (parser.util/read-union env k p (router/normalize-route (:route current-route))))))
;; lajt-reads for routing/union keys return the key to
;; select.
(defmethod lajt-read :routing/app-root
  [_]
  {:depends-on [:query/current-route]
   ;; TODO: YOU ARE HERE!
   ;; this one and routing/store-dashboard is doing :custom
   ;; stuff where the current-route is nil.

   ;; Keep doing
   ;; (start-reloading)
   ;; (require '[eponai.fullstack2.tests])
   ;; (clojure.test/run-tests 'eponai.fullstack2.tests)

   ;; Check the output and go from there...
   :custom     (fn [env]
                 (debug "depends-on: " (:depends-on env))
                 (debug "results: " (:results env))
                 (let [current-route (get-in env [:depends-on :query/current-route])]
                   (debug "CUSTOM routing/app-root current-route: " current-route)
                   (debug "CUSTOM returning: " (router/normalize-route (:route current-route)))
                   (router/normalize-route (:route current-route))))})

(defmethod client-read :routing/store-dashboard
  [{:keys [db] :as env} k p]
  (let [current-route (client.routes/current-route db)
        parse-route (fn [route]
                      (let [ns (namespace route)
                            subroute (name route)
                            path (clojure.string/split subroute #"#")]
                        (keyword ns (first path))))]
    (debug "Reading routing/store-dashboard: " [k :route current-route (parse-route (:route current-route))])
    (if (:lajt.parser/calling-union-selector env)
      {:value (parse-route (:route current-route))}
      (parser.util/read-union env k p (parse-route (:route current-route))))))
;; lajt-reads for routing should return the key to dispatch on.
(defmethod lajt-read :routing/store-dashboard
  [_]
  {:depends-on [:query/current-route]
   :custom     (fn [env]
                 (let [current-route (get-in env [:depends-on :query/current-route])
                       parse-route (fn [route]
                                     (let [ns (namespace route)
                                           subroute (name route)
                                           path (clojure.string/split subroute #"#")]
                                       (keyword ns (first path))))]
                   (debug "CUSTOM routing/app-root current-route: " current-route)
                   (debug "CUSTOM returning: " (parse-route (:route current-route)))
                   (parse-route (:route current-route))))})

(defmethod client-read :query/messages
  [{:keys [db target]} _ _]
  (when (nil? target)
    ;; TODO lajt: Make the implementation based on protocols.
    ;; It should be able for an implementation to take advantage
    ;; of the optimizations done in lajt.
    ;; Especially in this case where the implementation of get-messages
    ;; here is done with datascript queries.
    {:value (parser/get-messages db)}))
(defmethod lajt-read :query/messages
  [_]
  ;; TODO lajt: Make it possible to only include an :after (or :custom, or :only)
  ;; key to make a non-cachable custom read?
  ;; How would one indicate that this read should be re-read?
  ;; hmm.. Always re-read it?
  {:custom (fn [env]
             (parser/get-messages (:db env)))})

(defn read-chat [db query store-id]
  (when-let [chat-db (client.chat/get-chat-db db)]
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
                              messages)))))))

;; TODO lajt: here's another case where it would be useful
;; to have it be protocol based.
(defmethod client-read :query/chat
  [{:keys [target db route-params query]} _ _]
  (when-let [store-id (:store-id route-params)]
    (if (some? target)
      {:remote/chat true}
      {:value (read-chat db query store-id)})))
(defmethod lajt-read :query/chat
  [_]
  {:remote/chat true
   ;; TODO lajt: How is this cached?
   ;; How can it be re-written to be cached?
   :custom      #(when-let [store-id (:store-id (:route-params %))]
                   (read-chat (:db %) (:query %) store-id))})

(defmethod client-read :datascript/schema
  [{:keys [target]} _ _]
  (when target
    {:remote true}))
(defmethod lajt-read :datascript/schema
  [_]
  {:remote true
   :no-op true})

(defmethod client-read :query/countries
  [{:keys [target query db]} _ _]
  (if target
    {:remote true}
    {:value (db/pull-all-with db query {:where '[[?e :country/code _]]})}))
(defmethod lajt-read :query/countries
  [_]
  {:remote true
   :query '{:find [[?e ...]]
            :where [[?e :country/code _]]}})

(defmethod client-read :query/product-search
  [{:keys [target]} _ _]
  (when target
    {:remote true}))
(defmethod lajt-read :query/product-search
  [_]
  {:remote true})

(defmethod client-read :query/client-env
  [{:keys [target]} _ _]
  (when target
    {:remote true}))
(defmethod lajt-read :query/client-env
  [_]
  {:remote true})

(defmethod client-read :query/sulo-localities
  [{:keys [target db query]} _ _]
  (if target
    {:remote true}
    {:value (db/pull-all-with db query {:where '[[?e :sulo-locality/title _]]})}))
(defmethod lajt-read :query/sulo-localities
  [_]
  {:remote true
   :query '{:find [[?e ...]]
            :where [[?e :sulo-locality/title _]]}})

(defn extract-items-query [query]
  (if-some [q (first (sequence (comp (filter map?)
                                     (map :browse-result/items)
                                     (filter some?)
                                     (take 1))
                               query))]
    q
    query))

(defmethod client-read :query/browse-products-2
  [{:keys [target db query route-params query-params ast]} _ _]
  ;; Extracts the :browse-result/items query from the query if it's there.
  ;; We're only sending the :browse-result/items to the server, so this read
  ;; when executed on the server, will see that query.
  (let [query (extract-items-query query)]
    (if target
      {:remote (assoc ast :query query)}
      (let [browse-params (browse/make-browse-params route-params query-params)
            browse-result (some->> (browse/find-result db browse-params)
                                   (db/entity db))]
        (when (some? browse-result)
          (let [items-in-cat (into [] (browse/items-in-category db
                                                                browse-result
                                                                (:categories browse-params)))
                items (into [] (browse/page-items-xf (:page-range browse-params)) items-in-cat)
                _ (debug "Pulling with items: " items " with query: " query)
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
            {:value {:browse-result/meta  (into {:browse-result/items-in-category items-in-cat
                                                 :db/id                           (:db/id browse-result)}
                                                browse-result)
                     :browse-result/items pulled}}))))))
(defmethod lajt-read :query/browse-products-2
  [_]
  ;; TODO:
  ;; Figrue out if we can do this in plain lajt.
  ;; Or, skip this for now.
  {:remote true
   :after [#(client-read % :query/browse-products-2 {})]}
  )

(defmethod client-read :query/browse-product-items
  [{:keys [target]} _ _]
  ;; Only for fetching product items, nothing else.
  (when target
    {:remote true}))
(defmethod lajt-read :query/browse-product-items
  [_]
  {:remote true})

(defmethod client-read :query/notifications
  [{:keys [target db]} _ _]
  (when (nil? target)
    {:value (some->> (client.auth/current-auth db)
                     (db/entity db)
                     (:user/notifications))}))
(defmethod lajt-read :query/notifications
  [_]
  {:query  '{:find  [?e .]
             :where [[?user :user/notifications ?e]]}
   :params {'?user [:auth :user-id]}})

(defmethod client-read :query/notification-count
  [{:keys [target db]} _ _]
  (when (nil? target)
    {:value (some->> (client.auth/current-auth db)
                     (db/entity db)
                     (:user/chat-notifications)
                     (count))}))
(defmethod lajt-read :query/notification-count
  [_]
  ;; TODO lajt:
  ;; Make these counting (or custom reduce that depends on something
  ;; else) easier to do?
  {:depends-on [:query/notifications]
   :query '{:find [(count ?e) .]}
   :params {'?e [:depends-on :query/notifications]}})

(defmethod client-read :query/featured-vods
  [{:keys [target db query]} _ _]
  (if (some? target)
    {:remote true}
    {:value (->> (db/all-with db {:where '[[?e :vod/featured]]})
                 (map #(db/entity db %))
                 (sort-by :vod/featured #(compare %2 %1))
                 (map :db/id)
                 (db/pull-many db query))}))
(defmethod lajt-read :query/featured-vods
  [_]
  (merge-with merge
              {:remote true}
              (query-featured :vod/featured '[[?e :vod/featured]])))
