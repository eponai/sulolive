(ns eponai.client.routes
  (:require
    [clojure.set :as set]
    [om.next :as om]
    [eponai.common.database :as db]
    [taoensso.timbre :as timbre :refer [debug]]))

(def root-route-key ::app-root)

(defn set-route!
  "Set's the app route given either a reconciler or a component, and a route.
   Also optinally takes
   :route-params - a map with route specific parameters.
   :queue? - re-renders from the root component, defaults to true
   :tx - an additional transaction to be transacted after the route has been set.
         Can be a mutation, a read-key or a vector of those things.

   Examples:
   - Set route to be a store:
     (set-route! this :store/by-id {:route-params 12345})
   - Set route to index and re-read :cart/price
     (set-route! this :index {:tx :cart/price})
     (set-route! this :index {:tx [:cart/price]})

   Heavily inspired by compassus/set-route! (github.com/compassus/compassus),
   which we doesn't use because it's to frameworky. We'd have to use it's parser
   for example, and I'm not sure it works with datascript(?)."
  ([x route]
   (set-route! x route nil))
  ([x route {:keys [queue? route-params tx] :or {queue? true}}]
   {:pre [(or (om/reconciler? x) (om/component? x))
          (keyword? route)]}
   (let [reconciler (cond-> x (om/component? x) (om/get-reconciler))
         tx (when-not (nil? tx)
              (cond->> tx (not (vector? tx)) (vector)))]
     (om/transact! reconciler (cond-> [(list 'routes/set-route! {:route route
                                                                 :route-params route-params})]
                                      :always (into tx)
                                      queue? (into (om/transform-reads reconciler [root-route-key])))))))

(defn- to-db
  "Transforms, components, reconcilers or connections to a database."
  [x]
  (reduce (fn [x [pred f]] (cond-> x (pred x) (f)))
          x
          [[om/component? om/get-reconciler]
           [om/reconciler? om/app-state]
           [db/connection? db/db]]))

(defn current-route [x]
  {:pre [(or (om/component? x) (om/reconciler x) (db/connection? x) (db/database? x))]}
  (-> (to-db x)
      (db/entity [:ui/singleton :ui.singleton/routes])
      (->> (into {}))
      (set/rename-keys {:ui.singleton.routes/current-route :route
                        :ui.singleton.routes/route-params  :route-params})))
