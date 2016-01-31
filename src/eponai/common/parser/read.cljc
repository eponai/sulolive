(ns eponai.common.parser.read
  (:refer-clojure :exclude [read proxy])
  (:require [eponai.common.datascript :as eponai.datascript]
            [eponai.common.database.pull :as p]
            [eponai.common.parser.util :as parser.util]
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
                       server.pull/schema-with-inline-values
                       eponai.datascript/schema-datomic->datascript)}
     :cljs {:remote true}))

(defmethod read :query/all-currencies
  [{:keys [db query]} _ _]
  {:value  (p/pull-many db query (p/all-with db {:where '[[?e :currency/code]]}))
   :remote true})

(defmethod read :query/all-transactions
  [{:keys [db query auth]} _ {:keys [find-query values]}]
  (let [#?@(:clj  [eids (p/transactions db (:username auth))]
            :cljs [eids (p/all db find-query values)])]
    {:value (p/pull-many db query eids)
     :remote true}))

(defmethod read :query/one-budget
  [{:keys [ast db query auth target]} _ params]
  (let [#?@(:clj  [budget-uuid (:budget-uuid params)]
            :cljs [budget-uuid (let [{:keys [e a]} (:budget-uuid params)]
                                 (get (d/entity db e) a))])]
    (if target
      ;; Puts the budget-uuid in params for the remote.
      {target (assoc-in ast [:params :budget-uuid] budget-uuid)}
      (let [eid (if budget-uuid
                  (p/one-with db #?(:clj  (p/merge-query (p/budget-with-filter budget-uuid)
                                                         (p/budget-with-auth (:username auth)))
                                    :cljs (p/budget-with-filter budget-uuid)))
                  ;; No budget-uuid, grabbing the one with the smallest created-at
                  (some->> #?(:clj  (p/budget-with-auth (:username auth))
                              :cljs (p/budget))
                           (p/all-with db)
                           (map #(d/entity db %))
                           seq
                           (apply min-key :budget/created-at)
                           :db/id))]
        {:value (when eid (p/pull db query eid))}))))

(defmethod read :query/all-budgets
  [{:keys [db query auth]} _ _]
  {:value  (p/pull-many db query (p/all-with db #?(:clj (p/budget-with-auth (:username auth))
                                                   :cljs (p/budget))))
   :remote true})

(defmethod read :query/current-user
  [{:keys [db query auth]} _ _]
  (let [eids (p/all-with db {:where #?(:clj  [['?e :user/uuid (:username auth)]]
                                       :cljs '[[?e :user/uuid]])})]
    (println "Pulled user: " eids)
    {:value  (first (p/pull-many db query eids))
     :remote true}))

(defmethod read :query/user
  [{:keys [db query]} k {:keys [uuid]}]
  #?(:cljs {:value  (when (and (not (= uuid '?uuid))
                               (-> db :schema :verification/uuid))
                      (try
                        (p/pull db query [:user/uuid (f/str->uuid uuid)])
                        (catch :default e
                          (error "Error for parser's read key:" k "error:" e)
                          {:error {:cause :invalid-verification}})))
            :remote (not (= uuid '?uuid))}
     :clj  {:value (when (not (= uuid '?uuid))
                     (p/pull db query [:user/uuid (f/str->uuid uuid)]))}))

;; -------- Debug stuff

(defn debug-read [env k params]
  (debug "reading key:" k)
  (let [ret (read env k params)]
    (debug "read key:" k "returned:" ret)
    ret))
