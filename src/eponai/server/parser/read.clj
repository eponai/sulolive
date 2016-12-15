(ns eponai.server.parser.read
  (:require
    [eponai.common.database :as db]
    [eponai.common.datascript :as eponai.datascript]
    [eponai.common.parser :as parser :refer [server-read read-basis-param-path]]
    [eponai.common.parser.read :as common.read :refer [get-param]]
    [eponai.server.datomic.query :as query]
    [environ.core :as env]
    [clojure.data.generators :as gen]
    [datomic.api :as d]
    [taoensso.timbre :refer [error debug trace warn]]
    [eponai.server.auth :as auth])
  (:import [java.util Random]))

(defn db-shuffle
  "Shuffle items consistently with the db, meaing
  with the same db basis-t you get the same items."
  [db items]
  (binding [gen/*rnd* (Random. (d/basis-t db))]
    (gen/shuffle items)))

(defmethod server-read :datascript/schema
  [{:keys [db db-history]} _ _]
  {:value (-> (query/schema db db-history)
              (eponai.datascript/schema-datomic->datascript))})

(defmethod server-read :query/cart
  [{:keys [db db-history query]} _ {:keys [items]}]
  {:value (query/one db db-history query (cond-> {:where '[[?e :cart/items]]}
                                                 (seq items)
                                                 (db/merge-query {:symbols {'[?e ...] items}})))})

(defmethod read-basis-param-path :query/store [e _ p] [(get-param e p :store-id)])
(defmethod server-read :query/store
  [{:keys [db db-history query auth] :as env} _ p]
  (let [store-id (get-param env p :store-id)]
    (when-let [store-id (cond-> store-id (string? store-id) (Long/parseLong))]
      {:value (query/one db db-history query {:where   '[[?e]]
                                              :symbols {'?e store-id}})})))

(defmethod server-read :query/items
  [{:keys [db db-history query params] :as env} _ p]
  {:value (let [when-string #(when (string? %) %)
                category (when-string (get-param env p :category))
                search (when-string (get-param env p :search))
                entity-query (if (or category search)
                               (cond-> {:where []}
                                       (not-empty category)
                                       (db/merge-query
                                         {:where   '[[?e :item/category ?c]]
                                          :symbols {'?c category}})
                                       (not-empty search)
                                       (db/merge-query
                                         {:where   '[[(str "(?i)" ?search) ?matcher]
                                                     [(re-pattern ?matcher) ?regex]
                                                     [(re-find ?regex ?aname)]
                                                     [?e :item/name ?aname]]
                                          :symbols {'?search search}}))
                               {:where '[[?e :item/name]]})]
            (query/all db db-history query entity-query))})

(defmethod read-basis-param-path :query/item [env _ p] [(get-param env p :product-id)])
(defmethod server-read :query/item
  [{:keys [db db-history query params] :as env} _ p]
  (let [product-id (get-param env p :product-id)]
    {:value (query/one db db-history query
                       {:where   '[[?e :item/name]]
                        :symbols {'?e (Long/parseLong product-id)}})}))

(defmethod server-read :query/auth
  [{:keys [auth]} _ _]
  {:value (when (some? (:iss auth))
            auth)})

(defmethod server-read :query/streams
  [{:keys [db query]} _ _]
  {:value (db/pull-all-with db query {:where '[[?e :stream/name]]})})

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
  {:value (->> (query/all db db-history query {:where '[[?e :item/id]]})
               (take 4)
               (feature-all db-history :item))})

(defmethod server-read :query/featured-stores
  [{:keys [db db-history query]} _ _]
  ;; TODO: Come up with a way to feature stores.
  {:value (->> (query/all db db-history query {:where '[[?e :store/name]]})
               (take 4)
               (feature-all db-history :store))})

(defmethod server-read :query/stream-config
  [{:keys [db db-history query]} k _]
  ;; Only query once. User will have to refresh the page to get another stream config
  ;; (For now).
  (when (nil? db-history)
    (assert (= (every? {:db/id :stream-config/hostname} query))
            (str "server read: " k
                 " only supports pull-pattern : " [:stream-config/hostname :db/id]
                 " was: " query))
    {:value {:ui/singleton                        :ui.singleton/stream-config
             :ui.singleton.stream-config/hostname (or (env/env :red5pro-server-url) "localhost")}}))
