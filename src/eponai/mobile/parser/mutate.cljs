(ns eponai.mobile.parser.mutate
  (:require [eponai.common.parser :refer [mutate]]
            [eponai.common.database.transact :as t]
            [om.next :as om]
            [datascript.core :as d]
            [taoensso.timbre :refer-macros [debug]]))

(defn set-route-tx [route]
  {:ui/component           :ui.component/app
   :ui.component.app/route route})

(defmethod mutate 'app/set-route
  [{:keys [state]} _ {:keys [route]}]
  {:value  {:keys [:query/app]}
   :action #(t/transact state [(set-route-tx route)])})

(defmethod mutate 'login/verify
  [{:keys [state]} k {:keys [verify-uuid]}]
   (let [verify-endpoint (-> (d/entity (d/db state) [:ui/singleton :ui.singleton/configuration])
                             :ui.singleton.configuration.endpoints/verify)
         verify-endpoint (str verify-endpoint "/" verify-uuid)]
     (debug "Will verify verify-uuid: " verify-endpoint)
     ;; Special http remote which uses the query to call some endpoint.
     ;; Response can be merged with the parser.merge function.
     {:http/call (om/query->ast `[(http/get ~{:mutation 'login/verify
                                              :endpoint verify-endpoint})])}))
