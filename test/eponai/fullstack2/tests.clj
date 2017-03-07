(ns eponai.fullstack2.tests
  (:require [eponai.server.core :as core]
            [eponai.client.auth :as auth]
            [eponai.common.parser :refer [client-mutate server-mutate]]
            [clojure.test :as test :refer [is]]))

(defn unwrap [f]
  (if (fn? f)
    (recur (f))
    f))

(defn run-test [f]
  (let [{::keys [actions]} (unwrap f)
        ;; See what we did in framework to setup, run and teardown server/clients.
        ;; I think I like how tests are defined and how we auth.
        server :todo
        client :todo]))

(defmethod client-mutate 'fullstack/login
  [{:keys [shared]} k {:user/keys [email]}]
  {:action (fn []
             ;; The auth lock for the jvm client cannot show an input dialog
             ;; So it returns a function that takes the email and does the authorization.
             (let [lock (auth/show-lock (:shared/auth-lock shared))]
               (assert (fn? lock) (str "show-lock did not return a function. Was: " lock))
               (lock {:email email})))})

(defn test-store-logging-in []
  (fn []
    {::actions [{::read [{:query/auth [:user/email]}]
                 ::test (fn [read]
                          (is (= nil (:query/auth read))))}
                {::tx   `[(fullstack/login {:user/email "dev@sulo.live"})]
                 ::read [{:query/auth [:user/email]}]
                 ::test (fn [read]
                          (is (= "dev@sulo.live"
                                 (get-in read [:query/auth :user/email]))))}]}))

