(ns eponai.mobile.parser.read
  (:require [datascript.core :as d]
            [eponai.common.database :as db]
            [eponai.common.parser :refer [client-read]]
            [eponai.client.parser.read]
            [eponai.client.auth :as auth]
            [taoensso.timbre :as timbre :refer-macros [debug]]))

(defmethod client-read :query/app
  [{:keys [db query]} _ _]
  {:value (db/pull db query [:ui/component :ui.component/app])})

(defmethod client-read :query/loading
  [{:keys [db query]} _ _]
  {:value (db/pull db query [:ui/component :ui.component/loading])})

(defmethod client-read :query/messages
  [{:keys [db query]} k {:keys [mutation-uuids]}]
  {:value (db/pull-many db query
                       (db/all-with db {:where   '[[?e :tx/mutation-uuid ?mutation-uuid]
                                                  [?e :tx/message _]]
                                       :symbols {'[?mutation-uuid ...] mutation-uuids}}))})
