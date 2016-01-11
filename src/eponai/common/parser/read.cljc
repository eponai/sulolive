(ns eponai.common.parser.read
  (:refer-clojure :exclude [read])
  (:require [eponai.common.datascript :as eponai.datascript]
            [eponai.common.database.pull :as p]
            [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [debug error info warn]]
    #?(:clj
            [eponai.server.datomic.pull :as server.pull])
    #?(:clj
            [eponai.server.auth.facebook :as fb])
    #?(:clj
            [datomic.api :only [q pull-many] :as d]
       :cljs [datascript.core :as d])
    #?(:cljs [om.next :as om])
            [eponai.common.format :as f]))

(defmulti read (fn [_ k _] k))

(defn- do-pull [f db pattern eids]
  (try
    (f db pattern eids)
    (catch #?(:clj Exception :cljs :default) e
      (let [#?@(:clj  [msg (.getMessage e)]
                :cljs [msg (.-message e)])]
        (throw (ex-info msg
                        {:cause     ::pull-error
                         :data      {:pattern pattern
                                     :eids    eids}
                         :message   msg
                         :exception e
                         #?@(:clj [:status :eponai.server.http/service-unavailable])}))))))

(defn pull-all
  "takes the database, a pull query and where-clauses, where the where-clauses
  return some entity ?e."
  [db query where-clauses]
  (let [ents (d/q (vec (concat '[:find [?e ...]
                                 :in $
                                 :where]
                               where-clauses))
                  db)]
    (do-pull d/pull-many db query ents)))

(defn pull
  [db query entid]
  (do-pull d/pull db query entid))

;; -------- No matching dispatch

#?(:cljs
   (defn proxy [{:keys [parser query target] :as env} _ _]
     (let [ret (parser env query target)]
       (if (and target (seq ret))
         {target (om/query->ast ret)}
         {:value ret}))))

#?(:cljs
   (defmethod read :default
     [e k p]
     (cond
       (= "proxy" (namespace k))
       (proxy e k p)
       :else (warn "Returning nil for parser read key: " k))))

;; -------- Readers for UI components

#?(:cljs
   (defmethod read :query/header
     [{:keys [db query]} _ _]
     {:value (pull-all db query '[[?e :ui/singleton :budget/header]])}))

;; -------- Remote readers

(defmethod read :datascript/schema
  [{:keys [db]} _ _]
  #?(:clj  {:value (-> db
                       server.pull/schema-with-inline-values
                       eponai.datascript/schema-datomic->datascript)}
     :cljs {:remote true}))

(defmethod read :query/all-dates
  [{:keys [db query]} _ _]
  {:value (pull-all db query '[[?e :date/ymd]])
   :remote true})

(defmethod read :query/all-currencies
  [{:keys [db query]} _ _]
  {:value (pull-all db query '[[?e :currency/code]])
   :remote true})

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
