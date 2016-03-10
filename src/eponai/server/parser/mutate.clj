(ns eponai.server.parser.mutate
  (:require
    [clojure.core.async :as async]
    [eponai.common.database.transact :as transact]
    [eponai.common.format :as format]
    [eponai.common.parser.mutate :refer [mutate]]))

(defmethod mutate 'transaction/create
  [{:keys [state mutation-uuid]} k params]
  {:action (fn []
             (let [transaction (format/transaction-create k params)
                   currency-chan (async/chan 1)
                   tx-report (transact/mutate-one state mutation-uuid transaction)]
               (async/go (async/>! currency-chan (:transaction/date transaction)))
               (assoc tx-report :currency-chan currency-chan)))})