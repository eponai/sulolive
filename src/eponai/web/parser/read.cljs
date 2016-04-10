(ns eponai.web.parser.read
  (:require [eponai.client.parser.read :as read]
            [eponai.common.parser :refer [read]]
            [eponai.common.database.pull :as p]
            [taoensso.timbre :refer-macros [debug]]))

;; ################ Local reads ####################
;; Local reads goes here. These are specific to the
;; app running on this platform.
;; Remote reads should be defined in:
;;     eponai.client.parser.read

;; -------- Readers for UI components

(defmethod read :query/active-project
  [{:keys [db query]} _ _]
  (debug "Pulling project query:" query)
  {:value (p/pull db query [:ui/component :ui.component/project])})

(defmethod read :query/selected-transaction
  [{:keys [db query]} _ _]
  (read/read-entity-by-key db query [:ui/component :ui.component/transactions]))

(defmethod read :query/active-widget-open
  [{:keys [db query]} _ _]
  {:value (p/pull db query [:ui/component :ui.component/widget])})

(defmethod read :query/active-widget
  [{:keys [db query]} _ _]
  (debug "Query" query)
  (let [{:keys [ui.component.widget/id]} (p/lookup-entity db [:ui/component :ui.component/widget])
        _ (debug "Did find id: " id)]
    {:value (when (number? id)
              (p/pull db query id))}))