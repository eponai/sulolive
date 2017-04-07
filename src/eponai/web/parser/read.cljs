(ns eponai.web.parser.read
  (:require
    [eponai.common.parser :refer [client-read]]
    [eponai.common.ui.router :as router]
    [taoensso.timbre :refer [debug]]
    [eponai.client.routes :as client.routes]
    [eponai.common.parser.util :as parser.util]))

;; ### Usage ################
;; Put reads which need to reference web ui components in here.

(defmethod client-read :routing/app-root
  [{:keys [db] :as env} k p]
  (let [current-route (client.routes/current-route db)]
    (debug "Reading app-root: " [k :route current-route])
    (parser.util/read-union env k p (router/normalize-route (:route current-route)))))
