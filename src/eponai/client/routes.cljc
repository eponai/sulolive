(ns eponai.client.routes
  (:require
    #?(:cljs [pushy.core :as pushy])
    [eponai.common.routes :as routes]
    [clojure.set :as set]
    [om.next :as om]
    [eponai.common.database :as db]
    [taoensso.timbre :as timbre :refer [error debug warn]]
    [cemerick.url :as url]
    [eponai.common.shared :as shared]
    [eponai.common.ui.router :as router]
    [eponai.common :as c]))

(def root-route-key :routing/app-root)

(defn transact-route!
  "Warning: Probably not what you want to use. See: (set-route!) instead.

  Set's the app route given either a reconciler or a component, and a route.
   Also optinally takes
   :route-params - a map with route specific parameters.
   :queue? - re-renders from the root component, defaults to true
   :tx - an additional transaction to be transacted after the route has been set.
         Can be a mutation, a read-key or a vector of those things.

   Examples:
   - Set route to be a store:
     (set-route! this :store {:route-params {:store-id 12345}})
   - Set route to index and re-read :cart/price
     (set-route! this :index {:tx :cart/price})
     (set-route! this :index {:tx [:cart/price]})

   Heavily inspired by compassus/set-route! (github.com/compassus/compassus),
   which we doesn't use because it's too frameworky (We'd have to use its parser
   for example, and I'm not sure it works with datascript(?))."
  ([x route]
   (transact-route! x route nil))
  ([x route {:keys [delayed-queue queue? route-params query-params tx] :or {queue? true}}]
   {:pre [(or (om/reconciler? x) (om/component? x))
          (keyword? route)]}
   (let [reconciler (cond-> x (om/component? x) (om/get-reconciler))
         tx (when-not (nil? tx)
              (cond->> tx (not (vector? tx)) (vector)))
         reads-fn (fn []
                    [:query/current-route
                     {root-route-key {(router/normalize-route route)
                                      (om/get-query
                                        (:component
                                          (router/route->component route)))}}])
         tx (cond-> [(list 'routes/set-route! {:route route
                                               :route-params route-params
                                               :query-params query-params})]
                    :always (into tx)
                    queue? (into (reads-fn)))]
     (debug "Transacting tx: " tx)
     (om/transact! reconciler tx)
     (when (fn? delayed-queue)
       (delayed-queue #(om/transact! reconciler (reads-fn)))))))

;; "Takes a route and its route-params and returns an url"
(defn url
  ([route] (url route nil))
  ([route route-params] (url route route-params nil))
  ([route route-params query-params]
   (routes/path route route-params query-params)))

(defn store-url
  ([store route]
    (store-url store route nil))
  ([store route route-params]
    (store-url store route route-params nil))
  ([store route route-params query-params]
   (when-let [store-id (or (:store/username store)
                      (:db/id store))]
     (url route (assoc route-params :store-id store-id) query-params))))

(defn- do-set-url!
  [component url]
  {:pre [(or (nil? url) (string? url))]}
  #?(;; There's no URL to set in clj land, so do nothing.
     :clj  nil
     :cljs (if-let [history (shared/by-key component :shared/browser-history)]
             (pushy/set-token! history url)
             (warn "No history found in shared for component: " component
                   ". Make sure :history was passed to the reconciler."))))

(defn set-url!
  "Sets the URL which will propagate the route changes, reads and everything else.

  Example:
  (set-url! this :store-dashboard/product {:store-id 1 :dashboard-option products :product-id 2})"
  ([component route] (set-url! component route nil))
  ([component route route-params] (set-url! component route route-params nil))
  ([component route route-params query-params]
   {:pre [(om/component? component)]}
   (if-let [bidi-url (url route route-params query-params)]
     (do
       (debug "Will set url: " bidi-url " created with " {:route        route
                                                          :route-params route-params
                                                          :query-params query-params})
       (do-set-url! component bidi-url))
     (warn "Unable to create a url with route: " route " route-params: " route-params))))

(def route-param-normalizers
  {:order-id   (fn [_ order-id] (c/parse-long-safe order-id))
   :user-id    (fn [_ user-id] (c/parse-long-safe user-id))
   :product-id (fn [_ product-id] (c/parse-long-safe product-id))
   :store-id   (fn [db store-id] (cond->> store-id
                                          (not (number? store-id))
                                          (db/store-id->dbid db)))})

(defn normalize-route-params [route-params db]
  (->> (keys route-params)
       (filter #(contains? route-param-normalizers %))
       (reduce
         (fn [m k]
           (let [normalizer (get route-param-normalizers k)]
             (update m k #(normalizer db %))))
         route-params)))

(defn current-route [x]
  (let [db (db/to-db x)]
    (-> db
        (db/entity [:ui/singleton :ui.singleton/routes])
        (->> (into {}))
        (set/rename-keys {:ui.singleton.routes/current-route :route
                          :ui.singleton.routes/route-params  :route-params
                          :ui.singleton.routes/query-params  :query-params}))))
