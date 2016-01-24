(ns eponai.client.parser.read
  (:require [eponai.common.database.pull :as p]
            [eponai.common.parser.read :refer [read]]))

;; -------- Readers for UI components

(defmethod read :query/modal
  [{:keys [db query]} _ _]
  {:value (first (p/pull-many db query (p/all-where db [['?e :ui/singleton :ui.singleton/modal]])))})

(defmethod read :query/menu
  [{:keys [db query]} _ _]
  {:value (first (p/pull-many db query (p/all-where db [['?e :ui/singleton :ui.singleton/menu]])))})