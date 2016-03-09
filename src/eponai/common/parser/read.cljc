(ns eponai.common.parser.read
  (:refer-clojure :exclude [read proxy])
  (:require [eponai.common.datascript :as eponai.datascript]
            [eponai.common.database.pull :as common.pull]
            [eponai.common.parser.util :as parser.util]
            [eponai.common.report :as report]
            [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [debug error info warn]]
    #?(:clj
            [eponai.server.datomic.pull :as server.pull])
    #?(:clj
            [datomic.api :only [q pull-many] :as d]
       :cljs [datascript.core :as d])
    #?(:cljs [om.next :as om])
            [eponai.common.format :as f]))

(defmulti read (fn [_ k _] k))

;; -------- No matching dispatch

(defn proxy [{:keys [parser query target] :as env} k _]
  (let [ret (parser env query target)]
    #?(:clj  {:value ret}
       :cljs (if (and target (seq ret))
               ;; Trial and error led to this solution.
               ;; For some reason "proxy" keys get nested in one too many vectors
               ;; and we need to unwrap them here.
               {target (om/query->ast [{k (parser.util/unwrap-proxies ret)}])}
               {:value ret}))))

(defn return
  "Special read key (special like :proxy) that just returns
  whatever it is bound to.

  Example:
  om/IQuery
  (query [this] ['(:return/foo {:value 1 :remote true})])
  Will always return 1 and be remote true."
  [_ _ p]
  p)

(defmethod read :default
  [e k p]
  (cond
    (= "proxy" (namespace k))
    (proxy e k p)

    (= "return" (namespace k))
    (return e k p)

    :else (warn "Returning nil for parser read key: " k)))

;; -------- Remote readers

(defmethod read :datascript/schema
  [{:keys [db]} _ _]
  #?(:clj  {:value (-> db
                       server.pull/schema
                       eponai.datascript/schema-datomic->datascript)}
     :cljs {:remote true}))

(defmethod read :query/all-currencies
  [{:keys [db query]} _ _]
  {:value  (common.pull/pull-many db query (common.pull/all-with db {:where '[[?e :currency/code]]}))
   :remote true})

(def query-all-transactions
  (parser.util/cache-last-read
    (fn
      [{:keys [db query target ast auth]} _ {:keys [filter budget]}]
      (let [#?@(:cljs [budget (-> (d/entity db [:ui/component :ui.component/budget])
                                  :ui.component.budget/uuid)])]
        (if (= target :remote)
          {:remote (cond-> ast
                           (some? budget)
                           (assoc-in [:params :budget] budget))}

          (let [pull-params (cond-> {:where '[[?e :transaction/uuid]]}

                                    (some? filter)
                                    (common.pull/merge-query (common.pull/transactions filter))

                                    (some? budget)
                                    (common.pull/merge-query {:where '[[?e :transaction/budget ?b]
                                                             [?b :budget/uuid ?uuid]]
                                                    :symbols {'?uuid budget}}))
                ;; Include user filter on the server.
                #?@(:clj [pull-params (common.pull/merge-query pull-params
                                                      {:where   '[[?e :transaction/budget ?b]
                                                                  [?b :budget/created-by ?u]
                                                                  [?u :user/uuid ?uuid]]
                                                       :symbols {'?uuid (:username auth)}})])
                eids (common.pull/all-with db pull-params)]
            {:value #?(:clj  (server.pull/txs-with-conversions db query {:user/uuid (:username auth)
                                                                         :tx-ids eids})
                       :cljs (common.pull/pull-many db query eids))}))))))

(defmethod read :query/all-transactions
  [& args]
  (apply query-all-transactions args))

(defmethod read :query/dashboard
  [{:keys [db ast query target auth] :as env} _ {:keys [budget-uuid]}]
  (let [#?@(:cljs [budget-uuid (-> (d/entity db [:ui/component :ui.component/budget])
                                   :ui.component.budget/uuid)])]
    (if (= target :remote)
      {:remote (assoc-in ast [:params :budget-uuid] budget-uuid)}

      (let [eid (if budget-uuid
                  (common.pull/one-with db #?(:clj  (common.pull/merge-query (common.pull/budget-with-filter budget-uuid)
                                                         (common.pull/budget-with-auth (:username auth)))
                                    :cljs (common.pull/budget-with-filter budget-uuid)))
                  ;; No budget-uuid, grabbing the one with the smallest created-at
                  (some->> #?(:clj  (common.pull/budget-with-auth (:username auth))
                              :cljs (common.pull/budget))
                           (common.pull/all-with db)
                           (map #(d/entity db %))
                           seq
                           (apply min-key :budget/created-at)
                           :db/id))]
        {:value (when eid
                  (let [dashboard (common.pull/pull db query (common.pull/one-with db {:where [['?e :dashboard/budget eid]]}))]
                    #?(:clj (update dashboard :widget/_dashboard #(common.pull/widgets-with-data env %))
                       :cljs dashboard)))}))))

(defmethod read :query/all-dashboards
  [{:keys [db query auth]} _ _]
  {:value  (common.pull/pull-many db query (common.pull/all-with db #?(:clj {:where   '[[?e :dashboard/budget ?b]
                                                                    [?b :budget/created-by ?u]
                                                                    [?u :user/uuid ?user-uuid]]
                                                         :symbols {'?user-uuid (:username auth)}}
                                                   :cljs {:where '[[?e :dashboard/uuid]]})))
   :remote true})

(defmethod read :query/all-budgets
  [{:keys [db query auth]} _ _]
  {:value  (common.pull/pull-many db query (common.pull/all-with db #?(:clj (common.pull/budget-with-auth (:username auth))
                                                   :cljs (common.pull/budget))))
   :remote true})

(defmethod read :query/current-user
  [{:keys [db query auth]} _ _]
  (let [eids (common.pull/all-with db {:where #?(:clj  [['?e :user/uuid (:username auth)]]
                                       :cljs '[[?e :user/uuid]])})]
    {:value  (first (common.pull/pull-many db query eids))
     :remote true}))

(defmethod read :query/user
  [{:keys [db query]} k {:keys [uuid]}]
  #?(:cljs {:value  (when (and (not (= uuid '?uuid))
                               (-> db :schema :verification/uuid))
                      (try
                        (common.pull/pull db query [:user/uuid (f/str->uuid uuid)])
                        (catch :default e
                          (error "Error for parser's read key:" k "error:" e)
                          {:error {:cause :invalid-verification}})))
            :remote (not (= uuid '?uuid))}
     :clj  {:value (when (not (= uuid '?uuid))
                     (common.pull/pull db query [:user/uuid (f/str->uuid uuid)]))}))

;; -------- Debug stuff

(defn debug-read [env k params]
  (debug "reading key:" k)
  (let [ret (read env k params)]
    (debug "read key:" k "returned:" ret)
    ret))
