(ns eponai.server.parser.response
  (:require [clojure.core.async :refer [go <!]]
            [datomic.api :as d]
            [eponai.server.datomic.transact :as t]
            [eponai.server.datomic.pull :as p]
            [eponai.server.middleware :as m]
            [taoensso.timbre :refer [debug error trace]]))

;; function to use with eponai.common.parser/post-process-parse
(defmulti response-handler (fn [_ k _] k))
(defmethod response-handler :default
  [_ k _]
  (trace "no response-handler for key:" k)
  (cond
    (= "proxy" (namespace k)) :call

    :else
    nil))

(defmethod response-handler 'transaction/create
  [{:keys [state ::m/currency-rates-fn]} _ response]
  (when-let [chan (get-in response [:result :currency-chan])]
    (go
      (let [date (<! chan)]
        (when-not (p/date-conversions (d/db state) date)
          (t/currency-rates state (currency-rates-fn date))))))
  (update response :result dissoc :currency-chan))

(defmethod response-handler 'signup/email
  [{:keys [::m/send-email-fn]} _ response]
  (when-let [chan (get-in response [:result :email-chan])]
    (go
      (send-email-fn (<! chan) (get-in response [:result :status]))))
  (-> response
      (update :result dissoc :email-chan)
      (update :result dissoc :status)))
