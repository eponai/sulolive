(ns eponai.server.auth-test
  (:require [clojure.test :as test :refer [deftest is are]]
            [com.stuartsierra.component :as component]
            [eponai.server.core :as core]
            [clj-http.client :as http]
            [aleph.netty :as netty]
            [ring.util.response :as response]
            [eponai.common.routes :as routes]
            [eponai.server.test-util :as util]
            [eponai.server.datomic.mocked-data :as mocked-data]
            [taoensso.timbre :as timbre :refer [debug]]
            [eponai.common.database :as db]))

(def ^:dynamic *system* nil)
(def user-email mocked-data/test-user-email)

(defn with-system [test-fn]
  (binding [*system* (timbre/with-level :error
                                        (-> (core/system-for-tests)
                                            (component/start-system)))]
    (try
      (test-fn)
      (finally
        (timbre/with-level :error (component/stop *system*))))))

(test/use-fixtures :each with-system)

(deftest test-not-authed-http
  (let [red (:found response/redirect-status-codes)]
    (test/are [status route route-params]
      (= (:status (http/get (str (util/server-url *system*)
                                 (routes/path route route-params))
                            {:follow-redirects false}))
         status)
      200 :coming-soon nil
      red :user-settings nil
      red :store-dashboard {:store-id 123})))

(deftest test-authed-http
  (let [red (:found response/redirect-status-codes)
        db (util/system-db *system*)
        user-id (db/one-with db {:where   '[[?e :user/email ?email]]
                                 :symbols {'?email user-email}})
        store-id (db/one-with db {:where   '[[?e :store/owners ?owners]
                                             [?owners :store.owner/user ?user]]
                                  :symbols {'?user user-id}})]
    (util/with-auth
      *system* user-email
      (test/are [status route route-params]
        (do (debug "endpoint: " (util/endpoint-url *system* route route-params))
          (= status
             (:status (http/get (util/endpoint-url *system* route route-params)
                                {:follow-redirects false}))))
        200 :coming-soon nil
        200 :user-settings nil
        ;red :user-settings {:user-id (dec user-id)}
        200 :store-dashboard {:store-id store-id}
        red :store-dashboard {:store-id (dec store-id)}))))
