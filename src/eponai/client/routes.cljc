(ns eponai.client.routes
  (:require
    #?(:cljs [pushy.core :as pushy])
    [eponai.common.routes :as routes]
    [clojure.set :as set]
    [om.next :as om]
    [eponai.common.database :as db]
    [taoensso.timbre :as timbre :refer [error debug warn]]
    [eponai.common.shared :as shared]
    [eponai.common :as c]))

;; "Takes a route and its route-params and returns an url"
(defn url
  ([route] (url route nil nil))
  ([route route-params] (url route route-params nil))
  ([route route-params query-params]
   (routes/path route route-params query-params)))

(defn map->url [{:keys [route route-params query-params]}]
  (url route route-params query-params))

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

(defn set-url-map!
  [component {:keys [route route-params query-params]}]
  (set-url! component route route-params query-params))

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

(defn merge-route
  "Merges the current route with a route map, containing :route, :route-params, :query-params

  Can take a 3rd parameter with a vector of keys to dissoc/disgard from the current route."
  ([x route-map]
   (merge-route x route-map [:route :route-params :query-params]))
  ([x {:keys [route] :as route-map} keys-to-keep-from-current]
   (let [current (into {}
                       (filter (comp (set keys-to-keep-from-current) key))
                       (current-route x))]
     (cond-> (merge-with merge current (dissoc route-map :route))
             (some? route)
             (assoc :route route)))))
