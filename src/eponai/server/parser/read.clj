(ns eponai.server.parser.read
  (:refer-clojure :exclude [read])
  (:require
    [eponai.common.database.pull :as common.pull :refer [merge-query one-with min-by pull]]
    [clojure.set :as set]
    [datomic.api :as d]
    [eponai.common.datascript :as eponai.datascript]
    [eponai.common.format :as f]
    [eponai.common.parser :refer [read]]
    [eponai.server.datomic.pull :as server.pull]
    [eponai.server.external.facebook :as facebook]
    [eponai.server.external.stripe :as stripe]
    [taoensso.timbre :refer [debug]]
    [eponai.common.database.pull :as pull]))

(defmethod read :datascript/schema
  [{:keys [db db-since]} _ _]
  {:value (-> (server.pull/schema db db-since)
              (eponai.datascript/schema-datomic->datascript))})

(defmethod read :user/current
  [{:keys [db db-since auth]} _ _]
  {:value (when (:username auth)
            (server.pull/pull-one-since db db-since [:db/id :user/uuid]
                                        {:where   '[[?e :user/uuid ?user-uuid]]
                                         :symbols {'?user-uuid (:username auth)}}))})

;; ############## App ################

(defn entity-map->shallow-map [e]
  (persistent!
    (reduce (fn [m [k v]]
                 (let [id (:db/id v)
                       v (cond
                           (some? id) {:db/id id}
                           (coll? v) (mapv (fn [x] {:db/id (:db/id x)}) v)
                           :else v)]
                   (assoc! m k v)))
            (transient {:db/id (:db/id e)})
            e)))

(defmethod read :query/transactions
  [{:keys [db db-since query auth]} _ {:keys [project-uuid filter]}]
  (let [filters? (and (map? filter) (some #(val %) filter))
        entity-query (common.pull/transaction-entity-query
                       {:project-uuid project-uuid
                        :filter       filter
                        :user-uuid    (:username auth)
                        :query-params {:where   '[[?e :transaction/project ?b]
                                                  [?b :project/users ?u]
                                                  [?u :user/uuid ?user-uuid]]
                                       :symbols {'?user-uuid (:username auth)}}})
        tx-ids (server.pull/all-since db db-since query entity-query)
        tx-entities (into [] (-> (map #(d/entity db %))
                                 (common.pull/xf-with-tag-filters filter))
                          tx-ids)
        conversions (pull/transaction-conversions db (:username auth) tx-entities)

        conv-ids (into #{} (mapcat (fn [[_ v]]
                                     {:pre [(:transaction-conversion-id v) (:user-conversion-id v)]}
                                     (vector (:user-conversion-id v)
                                             (:transaction-conversion-id v))))
                       conversions)
        ref-ids (set/union
                  (server.pull/all-entities db query tx-ids)
                  (server.pull/all-entities db pull/conversion-query conv-ids))
        pull-xf (map #(d/pull db '[*] %))
        ]
    {:value (cond-> {:transactions (into [] (comp (map #(entity-map->shallow-map %))
                                                  (map #(update % :transaction/type (fn [t] {:db/ident t})))
                                                  (map #(if-let [tx-conv (get conversions (:db/id %))]
                                                         ;; TODO: Do not transfer the whole conversion entity?
                                                         (assoc % :transaction/conversion tx-conv)
                                                         %)))
                                         tx-entities)
                     :conversions  (into [] pull-xf conv-ids)
                     :refs         (into [] pull-xf ref-ids)

                     ;; This breaks tests. Do something about it.
                     ;;:transactions (pull/pull-many db query tx-ids)
                     ;;:conversions  (pull/conversions db tx-ids (:username auth))
                     }
                    ;; We cannot set read-basis-t when we have filters enabled.
                    ;; To prevent read-basis-t from being set by setting it to nil.
                    filters?
                    (with-meta {:eponai.common.parser/read-basis-t nil}))}))

(defmethod read :query/dashboard
  [{:keys [db db-since auth query] :as env} _ {:keys [project-uuid] :as params}]
  ;; TODO: Read-basis-t when using params:
  ;; https://app.asana.com/0/109022987372058/113539551736534
  (let [db-since db
        user-uuid (:username auth)
        project-with-auth (common.pull/project-with-auth user-uuid)
        project-eid (if project-uuid
              (one-with db (merge-query
                             (common.pull/project-with-uuid project-uuid)
                             project-with-auth))

              ;; No project-uuid, grabbing the one with the smallest created-at
              (min-by db :project/created-at project-with-auth))]
    {:value (when project-eid
              (let [t-p-query (common.pull/transaction-query)
                    t-e-query {:where   '[[?e :transaction/project ?project]]
                               :symbols {'?project project-eid}}
                    dashboard-entity-q (-> {:where   '[[?e :dashboard/project ?p]
                                                       [?p :project/users ?u]]
                                            :symbols {'?p project-eid}}
                                           (common.pull/with-auth user-uuid))
                    updated-transactions? (server.pull/one-since db db-since t-p-query t-e-query)
                    new-widgets? (server.pull/one-since db db-since [{:widget/_dashboard [:widget/uuid]}]
                                                        dashboard-entity-q)
                    dashboard (if updated-transactions?
                                ;; There are new transactions, so we have to update all widget's data.
                                ;; We can make this update incrementally in the future?
                                ;; Can we maybe just do this on the client?
                                ;; We'll probably need this for the playground on the client side anyway.
                                (common.pull/pull db query (common.pull/one-with db dashboard-entity-q))
                                (server.pull/pull-one-since db db-since query dashboard-entity-q))
                    ;; Delayed because we might not need them.
                    transactions (delay (->> (common.pull/find-transactions db (assoc params :user-uuid user-uuid
                                                                                             :project-uuid (:project/uuid (d/entity db project-eid))))
                                             (common.pull/transactions-with-conversions db (:username auth))))]
                (cond-> dashboard
                        ;; When there are widgets and there's either updated-transactions or widgets,
                        ;; only then should we add data to the widgets.
                        (and (contains? dashboard :widget/_dashboard)
                             (or updated-transactions? new-widgets?))
                        (update :widget/_dashboard (fn [widgets]
                                                     (mapv #(common.pull/widget-with-data db @transactions %) widgets))))))}))

(defmethod read :query/all-projects
  [{:keys [db db-since query auth]} _ _]
  {:value (server.pull/pull-all-since db db-since query
                                      {:where   '[[?e :project/users ?u]
                                                  [?u :user/uuid ?user-uuid]]
                                       :symbols {'?user-uuid (:username auth)}})})

(defmethod read :query/all-currencies
  [{:keys [db db-since query]} _ _]
  {:value (server.pull/pull-all-since db db-since query
                                      {:where '[[?e :currency/code]]})})

(defmethod read :query/current-user
  [{:keys [db db-since query auth]} _ _]
  {:value (server.pull/pull-one-since db db-since query {:where [['?e :user/uuid (:username auth)]]})})

(defmethod read :query/stripe
  [{:keys [db db-since query auth]} _ _]
  {:value (let [customer-id (one-with db {:where   '[[?u :user/uuid ?user-uuid]
                                                     [?c :stripe/user ?u]
                                                     [?c :stripe/customer ?e]]
                                          :symbols {'?user-uuid (:username auth)}})
                ;_ (debug "Found customer id: " customer-id)
                ;TODO: uncomment this when doing settings
                customer {}                                 ;(stripe/customer customer-id)
                ]
            (assoc
              (common.pull/pull db query [:stripe/customer customer-id])
              :stripe/info
              customer)
            ;(server.pull/pull-all-since db db-since query
            ;                            {:where ['[?e :stripe/user ?u]
            ;                                     ['?u :user/uuid (:username auth)]]})
            )})

;; ############### Signup page reader #################

(defmethod read :query/user
  [{:keys [db query]} _ {:keys [uuid]}]
  {:value (when (not (= uuid '?uuid))
            (pull db query [:user/uuid (f/str->uuid uuid)]))})

(defmethod read :query/fb-user
  [{:keys [db db-since query auth]} _ _]
  (let [eid (server.pull/one-since db db-since query
                                   (-> {:where   '[[?e :fb-user/user ?u]
                                                   [?u :user/uuid ?uuid]]
                                        :symbols {'?uuid (:username auth)}}))]
    {:value (when eid
              (let [{:keys [fb-user/token
                            fb-user/id]} (pull db [:fb-user/token :fb-user/id] eid)
                    {:keys [name picture]} (facebook/user-info id token)]
                (merge (pull db query eid)
                       {:fb-user/name    name
                        :fb-user/picture (:url (:data picture))})))}))
