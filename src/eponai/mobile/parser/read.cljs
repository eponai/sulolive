(ns eponai.mobile.parser.read
  (:require [datascript.core :as d]
            [eponai.common.database.pull :as p]
            [eponai.common.parser :refer [read]]
            [taoensso.timbre :refer-macros [debug]]
            [eponai.client.parser.read :as client.read]))

(defmethod read :query/app
  [{:keys [db query]} _ _]
  {:value (p/pull db query [:ui/component :ui.component/app])})

(defmethod read :query/loading
  [{:keys [db query]} _ _]
  {:value (p/pull db query [:ui/component :ui.component/loading])})
