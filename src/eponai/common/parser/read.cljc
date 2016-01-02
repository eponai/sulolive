(ns eponai.common.parser.read
  (:refer-clojure :exclude [read])
  (:require [eponai.common.datascript :as eponai.datascript]

    #?(:clj [eponai.server.datomic.pull :as server.pull])
    #?(:clj  [datomic.api :only [db q pull-many] :as d]
       :cljs [datascript.core :as d])
    #?(:cljs [om.next :as om])))

(defmulti read (fn [_ k _] k))

(defn- do-pull-all [db pattern eids]
  (try
    (d/pull-many db pattern eids)
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
  [conn query where-clauses]
  (let [db (d/db conn)
        ents (d/q (vec (concat '[:find [?e ...]
                                 :in $
                                 :where]
                               where-clauses))
                  db)]
    (do-pull-all db query ents)))

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
       :else (prn "WARN: Returning nil for read key: " k))))

;; -------- Readers for UI components

#?(:cljs
   (defmethod read :query/header
     [{:keys [state query]} _ _]
     {:value (pull-all state query '[[?e :ui/singleton :budget/header]])}))

;; -------- Remote readers

(defmethod read :datascript/schema
  [{:keys [state]} _ _]
  #?(:clj  {:value (-> (d/db state)
                       server.pull/schema-with-inline-values
                       eponai.datascript/schema-datomic->datascript)}
     :cljs {:remote true}))

(defmethod read :query/all-dates
  [{:keys [state query]} _ _]
  {:value (pull-all state query '[[?e :date/ymd]])
   :remote true})

(defmethod read :query/all-currencies
  [{:keys [state query]} _ _]
  {:value (pull-all state query '[[?e :currency/code]])
   :remote true})

;; -------- Debug stuff

(defn debug-read [{:keys [target] :as env} k params]
  (prn "Reading: " {:key k :target target})
  (let [ret (read env k params)]
    (prn {:read k :ret ret})
    ret))
