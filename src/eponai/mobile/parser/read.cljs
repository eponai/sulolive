(ns eponai.mobile.parser.read
  (:require [datascript.core :as d]
            [eponai.common.database.pull :as p]
            [eponai.common.parser :refer [read]]
            [taoensso.timbre :as timbre :refer-macros [debug]]
            [eponai.client.parser.read]))

(defmethod read :query/app
  [{:keys [db query]} _ _]
  {:value (p/pull db query [:ui/component :ui.component/app])})

(defmethod read :query/loading
  [{:keys [db query]} _ _]
  {:value (p/pull db query [:ui/component :ui.component/loading])})

(defmethod read :query/messages
  [{:keys [db query]} k {:keys [mutation-uuids]}]
  {:value (p/pull-many db query
                       (p/all-with db {:where   '[[?e :tx/mutation-uuid ?mutation-uuid]
                                                  [?e :tx/message _]]
                                       :symbols {'[?mutation-uuid ...] mutation-uuids}}))})
