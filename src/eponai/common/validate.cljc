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
    (throw (ex-info (str "Input validation error in mutate: " k)
                    (merge {:type   ::validation-error
                            :mutate k
                            :params p}
                           ex-data)))))


(defmulti validate
          "Multi method for validating input before making a mutation.
          If a mutation needs validation, implement this method for the same key as the mutation you want validated."
          (fn [_ k _] k))

(defmethod validate :default
  [_ k _]
  (info "Validator not implemented: " {:key k}))

(defmethod validate 'transaction/create
;; Validate input when creating a new transaction. Checks the following:
;; 1) The required fields are included in the input transaction.
;; 2) The user creating the transaction has access to the project in :transaction/project.
;; 3) All the tags in :transaction/tags are unique.
  [{:keys [state]} k {:keys [transaction user-uuid] :as p}]
  (let [required-fields #{:transaction/uuid
                          :transaction/title
                          :transaction/date
                          :transaction/amount
                          :transaction/currency
                          :transaction/created-at
                          :transaction/project
                          :transaction/type}
        missing-keys (into [] (filter #(nil? (get transaction %))) required-fields)]
    ;; Verify that all required fields are included in the transaction.
    (do-validate k p #(empty? missing-keys)
                 {:message      "Required fields are missing."
                  :code         :missing-required-fields
                  :missing-keys missing-keys})

    (let [{:keys [transaction/tags]} transaction
          {project-uuid :project/uuid} (:transaction/project transaction)
          db-project (p/one-with (d/db state) {:where   '[[?u :user/uuid ?user-uuid]
                                                         [?e :project/users ?u]
                                                         [?e :project/uuid ?project-uuid]]
                                              :symbols {'?user-uuid   user-uuid
                                                        '?project-uuid project-uuid}})]
      ;; Verify that that the transaction is added is accessible by the user adding the transaction.
      (do-validate k p #(some? db-project)
                   {:message      "You don't have access to modify the specified project."
                    :code         :project-unaccessible
                    :project-uuid project-uuid
                    :user-uuid    user-uuid})
      ;; Verify that the collection of tags does not include duplicate tag names.
      (do-validate k p #(= (frequencies (set tags)) (frequencies tags))
                   {:message "Illegal argument :transaction/tags. Each tag must be unique."
                    :code    :duplicate-tags
                    :duplicates (filter #(< 1 (val %)) (frequencies tags))}))))

(defmethod validate 'widget/create
  [{:keys [state]} k {:keys [widget] :as p}]
  (let [required-fields #{:widget/uuid
                          :widget/index
                          :widget/width
                          :widget/height
                          :widget/report
                          :widget/graph}
        missing-keys (into [] (filter #(nil? (get widget %))) required-fields)]
    ;; Verify that all required fields are included in the transaction.
    (do-validate k p #(empty? missing-keys)
                 {:message      "Required fields are missing."
                  :code         :missing-required-fields
                  :missing-keys missing-keys})))