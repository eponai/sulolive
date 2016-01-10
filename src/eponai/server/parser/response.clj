(ns eponai.server.parser.response
  (:require [clojure.core.async :refer [go <!]]
            [datomic.api :as d]
            [eponai.server.datomic.transact :as t]
            [eponai.server.datomic.pull :as p]
            [eponai.server.middleware :as m]))

;; function to use with eponai.common.parser/post-process-parse
(defmulti response-handler (fn [_ k _] k))
(defmethod response-handler :default
  [_ _ _]
  nil)

(defn post-currency-rates
  "Post currency rates for a specific date. Give a get-rates-fn that is used to retrieve the currency rates.

  get-rates-fn should return a map of the form:
  {:date \"2015-10-10\"
   :rates {:SEK 8.333
           :USD 1.0}})."
  [conn get-rates-fn date]
  (when-not (p/date-conversions (d/db conn) date)
    (t/currency-rates conn (get-rates-fn date))))

(defmethod response-handler 'transaction/create
  [{:keys [state ::m/currency-rates-fn]} _ response]
  (when-let [chan (get-in response [:result :currency-chan])]
    (go
      (post-currency-rates state currency-rates-fn (<! chan))))
  (update response :result dissoc :currency-chan))

(defmethod response-handler 'signup/email
  [{:keys [::m/send-email-fn]} _ response]
  (when-let [chan (get-in response [:result :email-chan])]
    (go
      (send-email-fn (<! chan))))
  (update response :result dissoc :email-chan))
