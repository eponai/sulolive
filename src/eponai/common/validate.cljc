(ns eponai.common.validate
  (:require
    #?(:clj [eponai.server.http :as h])
    #?(:clj [datomic.api :as d]
       :cljs [datascript.core :as d])
    [eponai.common.database :as db]
    #?(:clj [taoensso.timbre :refer [info debug]]
       :cljs [taoensso.timbre :refer-macros [info debug]])))

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


(defn edit [{:keys [db user-uuid]} k {:keys [old new] :as p}]
  (letfn [(validate-entity [e label]
            (let [required-keys #{:db/id}]
              (do-validate
                k p #(every? (partial contains? e) required-keys)
                {:message       (str "Entity " e " in " [k label]
                                     " did not have all required keys: " required-keys)
                 :code          :missing-required-fields
                 :required-keys required-keys
                 :user          user-uuid})))]

    (validate-entity old "old")
    (validate-entity new "new")

    (let [old-id (:db/id old)
          new-id (:db/id new)]
      (do-validate k p #(= old-id new-id)
                   {:message (str "edit " k " did not have equal :db/id")
                    :old-id  old-id
                    :new-id  new-id
                    :user    user-uuid})

      (do-validate k p #(some? (db/lookup-entity db old-id))
                   {:message (str "Could not lookup entity: " old-id
                                  " for user: " user-uuid)
                    :user    user-uuid}))))
