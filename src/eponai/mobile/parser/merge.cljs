(ns eponai.mobile.parser.merge
  (:require [eponai.client.parser.merge :as m]
            [eponai.mobile.parser.mutate :as mutate]
            [taoensso.timbre :refer-macros [debug error]]))

(defmulti mobile-merge (fn [_ k _] k))
(defmethod mobile-merge :default
  [_ _ _]
  nil)

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
