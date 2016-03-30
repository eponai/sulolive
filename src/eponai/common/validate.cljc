(ns eponai.common.validate
  (:require
    #?(:clj [eponai.server.http :as h])
    #?(:clj
    [datomic.api :as d]
       :cljs [datascript.core :as d])
    [eponai.common.database.pull :as p]
    #?(:clj [taoensso.timbre :refer [info]]
       :cljs [taoensso.timbre :refer-macros [info]])))

(defn- do-validate [k p pred & [ex-data]]
  (if (pred)
    true
    (throw (ex-info (str "Input validation error: " {:key k :params p})
                    (merge {:key   k
                            :params p}
                           ex-data)))))


(defmulti validate
          "Multi method for validating input before making a mutation.
          If a mutation needs validation, implement this method for the same key as the mutation you want validated."
          (fn [_ k _] k))

(defmethod validate :default
  [_ k _]
  (info "Validator not implemented: ") {:key k})

(defmethod validate 'transaction/create
;; Validate input when creating a new transaction. Checks the following:
;; 1) The required fields are included in the input transaction.
;; 2) The user creating the transaction has access to the budget in :transaction/budget.
;; 3) All the tags in :transaction/tags are unique.
  [{:keys [state]} k {:keys [transaction user-uuid] :as p}]
  (let [required-fields #{:transaction/uuid
                          :transaction/title
                          :transaction/date
                          :transaction/amount
                          :transaction/currency
                          :transaction/created-at
                          :transaction/budget
                          :transaction/type}
        missing-keys (into [] (filter #(nil? (get transaction %))) required-fields)]
    ;; Verify that all required fields are included in the transaction.
    (do-validate k p #(empty? missing-keys)
                 {:message      "Required fields are missing."
                  :code         :missing-required-fields
                  :missing-keys missing-keys})

    (let [{:keys [transaction/tags]} transaction
          {budget-uuid :budget/uuid} (:transaction/budget transaction)
          db-budget (p/one-with (d/db state) {:where   '[[?u :user/uuid ?user-uuid]
                                                         [?e :budget/users ?u]
                                                         [?e :budget/uuid ?budget-uuid]]
                                              :symbols {'?user-uuid   user-uuid
                                                        '?budget-uuid budget-uuid}})]
      ;; Verify that that the transaction is added is accessible by the user adding the transaction.
      (do-validate k p #(some? db-budget)
                   {:message "You don't have access to modify the specified budget."
                    :code :budget-unaccessible})
      ;; Verify that the collection of tags does not include duplicate tag names.
      (do-validate k p #(= (frequencies (set tags)) (frequencies tags))
                   {:message "Illegal argument :transaction/tags. Each tag must be unique."
                    :code :contains-duplicates}))))