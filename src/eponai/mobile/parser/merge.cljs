(ns eponai.mobile.parser.merge
  (:require [eponai.client.parser.merge :as m]
            [eponai.mobile.parser.mutate :as mutate]
            [taoensso.timbre :refer-macros [debug error]]
            [datascript.core :as d]))

(defmulti mobile-merge (fn [_ k _] k))
(defmethod mobile-merge :default
  [_ _ _]
  nil)

(defn sync-optimistic-with-message [messages db k {:keys [om.next/error result] :as params}]
  {:pre [(map? messages)]}
  (let [{:keys [error-message message]} messages
        db (d/db-with db [{:tx/mutation-uuid (:mutation-uuid result)
                           :tx/message       (if error error-message message)
                           :tx/status        (if error :tx.status/error
                                                       :tx.status/success)}])]
    (debug "merging optimistic update with message: " messages
           "for key: " k
           "params: " params)
    (m/merge-mutation mobile-merge db k params)))

(defmethod mobile-merge 'transaction/create
  [& args]
  (apply sync-optimistic-with-message {:message "Created transaction"
                                       :error-message "Error creating transaction"}
         args))

(defmethod mobile-merge 'transaction/edit
  [& args]
  (apply sync-optimistic-with-message {:message "Edited transaction"
                                       :error-message "Error editing transaction"}
         args))

(defmethod mobile-merge 'http/get
  [db k params]
  (debug "Merging: " k " with params: " params)
  (condp = (:mutation params)
    'login/verify (let [login-verified? (<= 200 (:status params) 299)
                        _ (when-not login-verified?
                            (error "User not logged in. Mutation:" k " params: " params))]
                    (m/transact db [(mutate/set-route-tx (if login-verified? :route/transactions :route/login))
                                    {:ui/singleton                         :ui.singleton/configuration
                                     :ui.singleton.configuration/logged-in login-verified?}]))))

(defmethod mobile-merge 'signin/facebook
  [db k params]
  (debug "Merging " k " with params: " params)
  db
  (let [login-verified? (get-in params [:result :auth])]
    (m/transact db {:ui/singleton :ui.singleton/configuration
                    :ui.singleton.configuration/logged-in login-verified?})))
