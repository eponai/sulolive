(ns eponai.server.auth.workflows-test
  (:require
    [cemerick.friend :as friend]
    [clojure.test :refer :all]
    [datomic.api :as d]
    [eponai.server.auth.credentials :as a]
    [eponai.server.auth.workflows :as w]
    [eponai.common.database.pull :as p]
    [eponai.server.datomic.format :as f]
    [eponai.server.test-util :refer [new-db]]
    [om.next.server :as om]
    [eponai.server.external.facebook :as facebook])
  (:import (clojure.lang ExceptionInfo)))

(def login-parser (om/parser
                    {:mutate (fn [_ k p]
                               {:action (fn [] p)})
                     :read   (fn [_ _ _])}))

(defn test-facebook-token-validator [{:keys [user-id token email error is-valid]}]
  (fn [app-id secret params]
    (facebook/user-token-validate app-id secret params {:to-long-lived-token-fn (fn [& _] {:access_token token})
                                                        :inspect-token-fn       (fn [& _]
                                                                                  (cond-> {:data {:user_id      user-id
                                                                                                  :access_token token
                                                                                                  :is_valid     is-valid
                                                                                                  :app_id       app-id}}
                                                                                          (some? error)
                                                                                          (assoc :error error)))
                                                        :user-info-fn           (fn [& _]
                                                                                  {:email email})})))


(deftest testing-successful-facebook-authentication-new-user
  (testing "Successful login returns auth map for auth'ed newly created user"
    (let [email "email"
          id "some-id"
          access-token "some-token"
          account (f/user-account-map email
                                      {:user/status   :user.status/active})
          conn (new-db (vals account))
          credential-fn (fn [input] (a/auth-map conn input))
          workflow (w/facebook "app-id" "app-secret")
          _ (assert (nil? (p/lookup-entity (d/db conn) [:fb-user/id id])))
          res (workflow {:login-parser             login-parser
                         :path-info                "/api"
                         :body                     {:query [`(session.signin/facebook ~{:user-id id :access-token access-token})]}
                         ::friend/auth-config      {:credential-fn      credential-fn
                                                    :login-mutation-uri "/api"}
                         :facebook-token-validator (test-facebook-token-validator {:email   email
                                                                                   :user-id id
                                                                                   :token   "long-lived-token"
                                                                                   :is-valid true})})
          db-user (p/lookup-entity (d/db conn) [:user/email email])
          db-fb-user (p/lookup-entity (d/db conn) [:fb-user/id id])]
      (is (= (:identity res) (:db/id db-user)))
      (is (= (:fb-user/token db-fb-user) "long-lived-token")))))

(deftest testing-failed-facebook-authentication
  (testing "Failed login returns auth map for auth'ed user, user id doesn't match the inspected tokens user id."
    (let [email "email"
          id "some-id"
          access-token "some-token"
          account (f/user-account-map email
                                      {:user/status   :user.status/active
                                       :fb-user/id    id
                                       :fb-user/token access-token})
          conn (new-db (vals account))
          credential-fn (fn [input] (a/auth-map conn input))
          workflow (w/facebook "some-id" "some-secret")]
      (is (thrown-with-msg?
            ExceptionInfo
            #"Facebook login error"
            (workflow {:login-parser             login-parser
                       :path-info                "/api"
                       :body                     {:query [`(session.signin/facebook ~{:user-id id :access-token access-token})]}
                       ::friend/auth-config      {:credential-fn      credential-fn
                                                  :login-mutation-uri "/api"}
                       :facebook-token-validator (test-facebook-token-validator {:email   "email"
                                                                                 :user-id "other-id"
                                                                                 :token   "long token"})}))))))