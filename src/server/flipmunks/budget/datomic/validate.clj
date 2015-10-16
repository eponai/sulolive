(ns flipmunks.budget.datomic.validate)

(defn- validate [f & args]
  (if (apply f args)
    true
    (throw (ex-info "Validation failed" {:cause ::validation-error
                                         :data {:fn f
                                                :params args}}))))

(defn- valid-user-tx? [user-tx]
  (let [required-fields #{:transaction/uuid
                          :transaction/name
                          :transaction/date
                          :transaction/amount
                          :transaction/currency
                          :transaction/created-at}]
    (validate every? #(contains? user-tx %) required-fields)))


(defn valid-user-txs? [user-txs]
  (validate every? #(valid-user-tx? %) user-txs))
