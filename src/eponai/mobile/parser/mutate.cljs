(ns eponai.mobile.parser.mutate
  (:require [eponai.common.parser :refer [mutate]]
            [eponai.common.database.transact :as t]))

(defmethod mutate 'app/set-route
  [{:keys [state]} _ {:keys [route]}]
  {:value  {:keys [:query/app]}
   :action #(t/transact state [{:ui/component           :ui.component/app
                                :ui.component.app/route route}])})
