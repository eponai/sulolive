(ns eponai.client.parser.read
  (:require [eponai.common.database.pull :as p]
            [eponai.common.parser.read :refer [read]]))

;; -------- Readers for UI components

(defmethod read :query/ui-singleton
  [{:keys [db query]} _ {:keys [singleton]}]
  {:value (first (p/pull-many db query (p/all-where db [['?e :ui/singleton singleton]])))})