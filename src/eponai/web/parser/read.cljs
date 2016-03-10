(ns eponai.web.parser.read
  (:require [eponai.client.parser.read :as read]
            [eponai.common.parser :refer [read]]
            [eponai.common.database.pull :as p]))

;; ################ Local reads ####################
;; Local reads goes here. These are specific to the
;; app running on this platform.
;; Remote reads should be defined in:
;;     eponai.client.parser.read

;; -------- Readers for UI components

(defmethod read :query/budget
  [{:keys [db query]} _ _]
  {:value (p/pull db query [:ui/singleton :ui.singleton/budget])})

(defmethod read :query/selected-transaction
  [{:keys [db query]} _ _]
  (read/read-entity-by-key db query [:ui/component :ui.component/transactions]))
