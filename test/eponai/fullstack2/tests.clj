(ns eponai.fullstack2.tests
  (:require
    [clojure.core.async :as a :refer [go <! >!]]
    [clojure.test :as test :refer [is]]
    [eponai.server.core :as core]
    [eponai.client.parser.message :as message]
    [eponai.client.auth :as auth]
    [eponai.client.reconciler :as reconciler]
    [eponai.client.utils :as client.utils]
    [eponai.common.parser :as parser :refer [client-mutate server-mutate]]
    [eponai.server.datomic-dev :as datomic-dev]
    [datomic.api :as datomic]
    [datomock.core :as dato-mock]
    [taoensso.timbre :refer [info debug]]
    [eponai.client.backend :as backend]
    [eponai.client.remotes :as remotes]
    [com.stuartsierra.component :as component]
    [om.next :as om]
    [aleph.netty :as netty]
    [clojure.data :as data]
    [clj-http.cookies :as cookies]
    [clj-http.client :as http]
    [eponai.common.routes :as routes]
    [aprint.dispatch :as adispatch]
    [taoensso.timbre :as timbre])
  (:import (om.next Reconciler)))

;; Print method stuff

(defmethod print-method Reconciler [x writer]
  (print-method (str "[Reconciler id=[" (get-in x [:config :id-key]) "]]") writer))

(defmethod adispatch/color-dispatch Reconciler [x]
  (adispatch/color-dispatch [(str "Reconciler" :id-key (get-in x [:config :id-key]))]))

;; End print method stuff


(defn unwrap [f & args]
  (loop [f f args args]
    (if (fn? f)
      (recur (apply f args) nil)
      f)))

;; Start and stop system

(defn- get-port [system]
  (netty/port (get-in system [:system/aleph :server])))

(defn- start-system [system]
  ;; TODO: Use datomic forking library, to setup and fork our datomic connection once.
  (let [system (core/start-server-for-tests {:conn (::forked-conn system)
                                             ;; Re-use the server's port
                                             ;; to be more kind to the test system.
                                             :port (::server-port system 0)})]
    (assoc system ::server-port (get-port system)
                  ::forked-conn (dato-mock/fork-conn (get-in system [:system/datomic :conn])))))

(defn- stop-system [system]
  (component/stop-system system))

(defn setup-system []
  nil)

;; END System

;; Create client

(defn- remote-config-with-server-url [conn server-url cookie-store]
  (reduce (fn [m k]
            (update m k (fn [remote-fn]
                          (-> remote-fn
                              (remotes/update-key :url #(str server-url %))
                              (remotes/update-key :opts assoc :cookie-store cookie-store)))))
          (reconciler/remote-config conn)
          (reconciler/remote-order)))

(defn- clj-auth-lock [server-url cookie-store]
  (reify
    auth/IAuthLock
    (show-lock [this]
      (fn [{:keys [email]}]
        (binding [clj-http.core/*cookie-store* cookie-store]
          (let [endpoint (str server-url (routes/path :auth))
                _ (debug "Requesting auth to endpoint: " endpoint)
                response (http/get endpoint {:follow-redirects false
                                             :query-params {:code email :state "/"}})]
            (info "Got auth response: " response)))))))

(defn create-client [system merge-chan]
  (let [reconciler-atom (atom nil)
        conn (client.utils/create-conn)
        server-url (str "http://localhost:" (get-port system))
        cookie-store (cookies/cookie-store)
        remote-config (remote-config-with-server-url conn server-url cookie-store)
        reconciler (reconciler/create {:conn             conn
                                       :parser           (parser/client-parser)
                                       :send-fn          (backend/send! reconciler-atom remote-config
                                                                        {:did-merge-fn (fn [reconciler]
                                                                                         (go (>! merge-chan reconciler)))})
                                       :history          1000
                                       :route            :index
                                       :shared/auth-lock (clj-auth-lock server-url cookie-store)})]
    (reset! reconciler-atom reconciler)
    reconciler))

(defn- take-with-timeout [chan]
  (let [timeout-ms 3000
        timeout (a/timeout timeout-ms)
        [v c] (a/alts!! [chan timeout])]
    (when (not= c timeout)
      v)))

(defn action-messages [client history-id tx]
  (letfn [(remote-mutation? [mutation]
            (let [mutation-key (first mutation)]
              (some (fn [remote]
                      (let [parsed (client.utils/parse client [mutation] remote)]
                        (when (some? (get parsed mutation-key))
                          mutation-key)))
                    (client.utils/reconciler-remotes client))))]
    (into []
          (comp (filter parser/mutation?)
                (filter remote-mutation?)
                (map #(message/find-message client history-id %)))
          tx)))

(defn compare-paths [paths result]
  (let [actual (into {} (remove #(symbol? (key %))) result)]
    (->> paths
         (map (fn [[path expected]]
                (let [val ((apply comp (reverse path)) actual)
                      compared (if (fn? expected)
                                 (expected val)
                                 (= val expected))]
                  (when-not compared
                    {:expected expected
                     :actual   val
                     :path     path
                     :in-map   result}))))
         (filter some?)
         (seq))))

(defn run-test [system client merge-chan test-fn]
  {:post [(map? system)]}
  (let [{::keys [label actions]} (unwrap test-fn)]
    (reduce (fn [system {::keys [tx pre post] :as action}]
              (let [pre-tx-parse (om/transact! client tx)
                    history-id (parser/reconciler->history-id client)
                    response (take-with-timeout merge-chan)]
                (debug "Got response from transaction: " response)
                (if (nil? response)
                  (do (test/report {:type     :error
                                    :expected "Transaction to get a response."
                                    :actual   (ex-info "Test timed out" {:test-label label
                                                                         :timeout-ms 3000
                                                                         :action     action})})
                      ;; Abort test run for this test.
                      (reduced system))
                  (let [post-tx-parse (client.utils/parse client (vec (remove parser/mutation? tx)))
                        messages (action-messages client history-id tx)
                        pre-compare (when pre (compare-paths pre pre-tx-parse))
                        post-compare (when post (compare-paths post post-tx-parse))
                        success-message? #(and (message/final? %) (message/success? %))
                        pass? (and (every? success-message? messages)
                                   (empty? pre-compare)
                                   (empty? post-compare))]
                    (if pass?
                      (test/report {:type :pass})
                      (letfn [(report-message [fail-msg]
                                (test/report {:type     :fail
                                              :expected "Message to succeed"
                                              :actual   fail-msg}))
                              (report-compare [[pre-or-post compared]]
                                (run! (fn [compared]
                                        (test/report {:type     :fail
                                                      :expected {:expected (:expected compared)}
                                                      :actual   (-> compared
                                                                    (assoc :pre-or-post pre-or-post
                                                                           :tx tx)
                                                                    (dissoc :expected))}))
                                      compared))]
                        (run! report-message (filter (complement success-message?) messages))
                        (run! report-compare [[:pre pre-compare] [:post post-compare]])))
                    system))))
            system
            actions)))

;; END Create client

(defn run-tests [test-fns]
  (let [system (setup-system)]
    (reduce (fn [system test-fn]
              (let [system (start-system system)
                    merge-chan (a/chan)
                    client (create-client system merge-chan)]
                (-> system
                    (run-test client merge-chan (unwrap test-fn))
                    (stop-system))))
            system
            test-fns)))

(defmethod client-mutate 'fullstack/login
  [{:keys [parser shared] :as env} k {:user/keys [email]}]
  {:action (fn []
             ;; The auth lock for the jvm client cannot show an input dialog
             ;; So it returns a function that takes the email and does the authorization.
             (let [parsed-auth (parser env [{:query/auth [:db/id]}])]
               (debug "Parsed auth: " parsed-auth)
               (when-not (:query/auth parsed-auth)
                (let [lock (auth/show-lock (:shared/auth-lock shared))]
                  (assert (fn? lock) (str "show-lock did not return a function. Was: " lock))
                  (lock {:email email})))))})

;; TODO: Could define a multimethod for each mutation
;;       that returns reads it should read
;;       and for each read, pre and post paths that should hold.
;;       (For :query/auth in this example, the pre path would
;;       need to check for the current value of :user/email).
;; WOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOW...?
;; We'd also need a generator params of the mutation.
(defn test-store-login-2 []
  (let [test {::tx   `[(fullstack/login {:user/email "dev@sulo.live"})
                       {:query/auth [:user/email]}]
              ::pre  {[:query/auth :user/email] nil}
              ::post {[:query/auth :user/email] "dev@sulo.live"}}]
    {::label   "Should be able to log in store"
    ::actions [test
               (assoc test ::pre (::post test))]}))


;(test/deftest full-stack-tests
;  ;; Runs the test multiple times to make sure things are working
;  ;; after setup and tear down.
;  (run-tests [test-store-login-2
;              test-store-login-2
;              test-store-login-2]))
