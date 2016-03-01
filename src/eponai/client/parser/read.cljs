(ns eponai.client.parser.read
  (:require [datascript.core :as d]
            [eponai.common.database.pull :as p]
            [eponai.common.parser.read :as r :refer [read]]
            [taoensso.timbre :refer-macros [debug]]))

;; -------- Readers for UI components

(defmethod read :query/modal
  [{:keys [db query]} _ _]
  {:value (first (p/pull-many db query (p/all-with db {:where [['?e :ui/singleton :ui.singleton/modal]]})))})

(defmethod read :query/menu
  [{:keys [db query]} _ _]
  {:value (first (p/pull-many db query (p/all-with db {:where [['?e :ui/singleton :ui.singleton/menu]]})))})

(defmethod read :query/loader
  [{:keys [db query]} _ _]
  {:value (first (p/pull-many db query (p/all-with db {:where [['?e :ui/singleton :ui.singleton/loader]]})))})

(defmethod read :query/budget
  [{:keys [db query]} _ _]
  {:value (p/pull db query [:ui/singleton :ui.singleton/budget])})

;TODO figure this shit out

(defn read-entity-by-key
  "Gets an entity by it's ref id. Returns the full component unless a pull pattern is supplied.

  Examples:
  om/IQuery
  (query [{[:ui/component :ui.component/transactions] [:db/id]}])
  om/IQuery
  (query [[:ui/singleton :ui.singleton/budget]]"
  [{:keys [db ast query]} _ _]
  (let [e (d/entity db (:key ast))]
    {:value (cond
              (nil? e) e
              query (p/pull db query (:db/id e))
              :else (d/touch e))}))

(defmethod read :ui/component
  [& args]
  (apply read-entity-by-key args))

(defmethod read :ui/singleton
  [& args]
  (apply read-entity-by-key args))
