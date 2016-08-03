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
    [om.next.server :as om]))

(def login-parser (om/parser
                    {:mutate (fn [_ k p]
                               (if (= k 'signin/facebook)
                                 {:action (fn [] p)}))
                     :read   (fn [_ _ _])}))


(deftest testing-successful-facebook-authentication
  (testing "Successful login returns auth map for auth'ed user"
    (let [email "email"
          id "some-id"
          access-token "some-token"
          account (f/user-account-map email
                                      {:user/status   :user.status/active
                                       :fb-user/id    id
                                       :fb-user/token access-token})
          conn (new-db (vals account))
          credential-fn (fn [input] (a/auth-map conn input))
          workflow (w/facebook nil nil)
          res (workflow {:login-parser             login-parser
                         :path-info                "/api"
                         :body                     {:query [`(signin/facebook ~{:user-id id :access-token access-token})]}
                         ::friend/auth-config      {:credential-fn      credential-fn
                                                    :login-mutation-uri "/api"}
                         :facebook-token-validator (fn [_ _ p]
                                                     {:user_id id :access_token access-token :fb-info-fn (fn [& args] {:email email})})})
          db-user (p/lookup-entity (d/db conn) [:user/email email])]
      (is (= (:identity res) (:db/id db-user))))))