(ns eponai.server.parser.response
  (:require
    [clojure.core.async :refer [go <!]]
    [datomic.api :as d]
    [eponai.common.database.pull :as p]
    [eponai.common.database.transact :as t]
    [eponai.server.datomic.format :as f]
    [eponai.server.datomic.pull :as server.pull]
    [eponai.server.email :as email]
    [eponai.server.middleware :as m]
    [taoensso.timbre :refer [debug error trace info warn]]))

(defn empty-coll? [c]
  (or
    (and (map? c) (every? #(empty-coll? (val %)) c))
    (and (coll? c) (empty? c))))

;; function to use with eponai.common.parser/post-process-parse
(defmulti response-handler (fn [_ k _] k))
(defmethod response-handler :default
  [_ k v]
  (trace "no response-handler for key:" k)
  (cond
    (#{"proxy" "routing"} (namespace k)) :call
    ;; Check for empty responses
    (keyword? k) (when (empty-coll? v) :dissoc)

    :else nil))

(defmethod response-handler 'transaction/create
  [{:keys [state ::m/currency-rates-fn ::m/currencies-fn]} _ response]
  (when-let [chan (get-in response [:result :currency-chan])]
    (go
      (let [date (<! chan)]
        (when-not (p/pull (d/db state) '[:conversion/_date] [:date/ymd (:date/ymd date)])
          (let [rates (f/currency-rates (currency-rates-fn (:date/ymd date)))
                new-currencies (server.pull/new-currencies (d/db state) rates)]
            (t/transact state rates)
            (info "Currency rates transacted for date: " (:date/ymd date))

            (when (seq new-currencies)
              (info "Found currencies not in DB. Pulling and transacting currencies.")
              (t/transact state (f/currencies (currencies-fn)))))))))
  (update response :result dissoc :currency-chan))

(defmethod response-handler 'session.signin/email
  [{:keys [::email/send-verification-fn state]} _ response]
  (when-let [chan (get-in response [:result :email-chan])]
    (go
      (let [verification (<! chan)]
        (send-verification-fn (p/lookup-entity (d/db state) [:verification/uuid (:verification/uuid verification)])
                              {:user-status (get-in response [:result :status])
                               :device      (get-in response [:result :device])}))))
  ;; TODO: What's the plan here? Do we really just want to return nil?
  (update response :result dissoc :email-chan :status :device))

(defmethod response-handler 'project/share
  [{:keys [::email/send-invitation-fn state]} _ response]
  (when-let [chan (get-in response [:result :email-chan])]
    (go
      (let [user-status (get-in response [:result :status])
            inviter (get-in response [:result :inviter])
            verification (<! chan)]
        (send-invitation-fn (p/lookup-entity (d/db state) [:verification/uuid (:verification/uuid verification)])
                            {:inviter inviter
                             :user-status user-status}))))
  (-> response
      (update :result dissoc :email-chan)
      (update :result dissoc :status)))