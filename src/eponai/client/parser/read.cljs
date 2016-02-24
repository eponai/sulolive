(ns eponai.client.parser.read
  (:require [datascript.core :as d]
            [eponai.common.database.pull :as p]
            [eponai.common.parser.read :as r :refer [read]]))

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
(defmethod read :ui/component
  [{:keys [db ast]} _ _]
  {:value (d/entity db (:key ast))})

(defmethod read :ui/singleton
  [{:keys [db ast]} _ _]
  {:value (d/entity db (:key ast))})
