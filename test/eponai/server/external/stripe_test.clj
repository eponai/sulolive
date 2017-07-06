(ns eponai.server.external.stripe-test
  (:require
    [clojure.test :refer [deftest is are testing]]
    [eponai.server.test-util :refer [new-db]]
    [eponai.server.external.stripe.webhooks :as stripe-wh]
    [eponai.server.datomic.format :as f]
    [eponai.common.format :as cf]
    [eponai.common.database :as db]
    [taoensso.timbre :refer [debug]]
    [eponai.server.log :as log]))

(deftest test-account-update-webhook
  (let [stripe-id "store-stripe"
        store-uuid (db/squuid)
        store (cf/add-tempid {:store/uuid   store-uuid
                              :store/status {:status/type :status.type/open}
                              :store/stripe (cf/add-tempid {:stripe/id stripe-id})})
        conn (new-db [store])
        event (fn [& [account]]
                {:type "account.updated"
                 :data {:object (merge {:id                stripe-id
                                        :details_submitted true
                                        :payouts_enabled   true
                                        :charges_enabled   true
                                        :tos_acceptance    {:date 1234}} account)}})
        send-webhook (fn [e]
                       (stripe-wh/handle-connected-webhook {:state         conn
                                                            :logger        (force log/no-op-logger)
                                                            :webhook-event e}
                                                           (get-in e [:data :object])))
        pull-stripe #(db/pull (db/db conn) [{:stripe/status [:status/type]}] [:stripe/id stripe-id])
        pull-store #(db/pull (db/db conn) [{:store/status [:status/type]}] [:store/uuid store-uuid])]
    
    (testing "Account updated from nil to active "
      (let [activate-event (event nil)]
        (send-webhook activate-event)
        (is (= :status.type/active (get-in (pull-stripe) [:stripe/status :status/type])))
        (is (= :status.type/open (get-in (pull-store) [:store/status :status/type])))))

    (testing "Account updated to disabled if payouts disabled"
      (let [disable-payouts-event (event {:payouts_enabled false})]
        (send-webhook disable-payouts-event)
        (is (= :status.type/inactive (get-in (pull-stripe) [:stripe/status :status/type])))
        (is (= :status.type/closed (get-in (pull-store) [:store/status :status/type])))))

    (testing "Account updated to disabled if charges disabled"
      (let [disable-payouts-event (event {:charges_enabled false})]
        (send-webhook disable-payouts-event)
        (is (= :status.type/inactive (get-in (pull-stripe) [:stripe/status :status/type])))
        (is (= :status.type/closed (get-in (pull-store) [:store/status :status/type])))))

    (testing "Account updated to disabled if we have a disabled reason"
      (let [disable-payouts-event (event {:verification {:disabled_reason "some reason"}})]
        (send-webhook disable-payouts-event)
        (is (= :status.type/inactive (get-in (pull-stripe) [:stripe/status :status/type])))
        (is (= :status.type/closed (get-in (pull-store) [:store/status :status/type])))))

    (testing "Account updated to active when all is good again"
      (let [disable-payouts-event (event nil)]
        (send-webhook disable-payouts-event)
        (is (= :status.type/active (get-in (pull-stripe) [:stripe/status :status/type])))
        (is (= :status.type/closed (get-in (pull-store) [:store/status :status/type])))))))






