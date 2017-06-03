(ns eponai.fullstack2.tests
  (:require
    [clojure.core.async :as a :refer [go <! >!]]
    [clojure.test :as test :refer [is]]
    [eponai.server.core :as core]
    [eponai.client.parser.message :as message]
    [eponai.client.auth :as auth]
    [eponai.client.reconciler :as reconciler]
    [eponai.client.utils :as client.utils]
    [eponai.client.parser.read]
    [eponai.client.parser.mutate]
    [eponai.common.ui-namespaces]
    [eponai.common.parser :as parser :refer [client-mutate server-mutate]]
    [datomock.core :as dato-mock]
    [taoensso.timbre :refer [info debug]]
    [eponai.client.backend :as backend]
    [eponai.client.remotes :as remotes]
    [com.stuartsierra.component :as component]
    [om.next :as om]
    [aleph.netty :as netty]
    [clj-http.cookies :as cookies]
    [eponai.server.test-util :as test-util]
    [eponai.server.datomic.mocked-data :as mocked-data]
    [aprint.dispatch :as adispatch]
    [taoensso.timbre :as timbre]
    [eponai.common.ui.router :as router]
    [clojure.data :as diff]
    [clojure.data :as data]
    [eponai.common.database :as db])
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
  (some-> system
          (get-in [:system/aleph :server])
          (netty/port)))

(defn- start-system [system]
  ;; TODO: Use datomic forking library, to setup and fork our datomic connection once.
  (let [system (component/start-system system)]
    (assoc system ::cached {::server-port (get-port system)
                            ::forked-conn (dato-mock/fork-conn (get-in system [:system/datomic :conn]))})))

(defn- stop-system [system]
  (component/stop-system system))

(defn setup-system [{::keys [cached] :as old-system}]
  (core/system-for-tests {:conn (::forked-conn cached)
                          ;; Re-use the server's port
                          ;; to be more kind to the test system.
                          :port (::server-port cached 0)}))

;; END System

;; Create client

(defn- remote-config-with-server-url [conn system cookie-store]
  (letfn [(same-jvm-remote [remote-fn]
            (-> remote-fn
                (remotes/update-key :url #(str (test-util/server-url system) %))
                (remotes/update-key :opts assoc :cookie-store cookie-store)))]
    (reduce (fn [m k]
              (update m k same-jvm-remote))
            (reconciler/remote-config conn)
            (reconciler/remote-order))))

(defn- clj-auth-lock [system cookie-store]
  (reify
    auth/IAuthLock
    (show-lock [this]
      (fn [{:keys [email]}]
        (let [ret (test-util/auth-user! system email cookie-store)]
          (info "Got auth response: " ret))))))

(defn create-client [system merge-chan]
  (let [reconciler-atom (atom nil)
        conn (client.utils/create-conn)
        cookie-store (cookies/cookie-store)
        remote-config (remote-config-with-server-url conn system cookie-store)
        reconciler (reconciler/create {:conn             conn
                                       :parser           (parser/client-parser)
                                       :send-fn          (backend/send! reconciler-atom remote-config
                                                                        {:did-merge-fn (fn [reconciler]
                                                                                         (go (>! merge-chan reconciler)))})
                                       :history          1000
                                       :route            :index
                                       :shared/auth-lock (clj-auth-lock system cookie-store)})]
    (reset! reconciler-atom reconciler)
    reconciler))

(defn- take-with-timeout [chan timed-out]
  (let [timeout-ms 3000
        timeout (a/timeout timeout-ms)
        [v c] (a/alts!! [chan timeout])]
    (if (= c timeout)
      timed-out
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
                     :in-map   actual}))))

         (filter some?)
         (seq))))

(defn run-test [system client merge-chan test-fn]
  {:post [(map? system)]}
  (let [{::keys [label actions]} (unwrap test-fn)]
    (reduce (fn [system {::keys [tx pre post post-fn] :as action}]
              (let [system (assoc system :reconciler client)
                    tx (if (fn? tx) (tx system) tx)
                    pre-tx-parse (om/transact! client tx)
                    history-id (parser/reconciler->history-id client)
                    response (take-with-timeout merge-chan :timed-out)]
                (debug "Got response from transaction: " response)
                (if (= response :timed-out)
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
                        post-result (when post-fn (post-fn system))
                        pass? (and (every? success-message? messages)
                                   (empty? pre-compare)
                                   (empty? post-compare)
                                   (nil? post-result))]
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
                        (run! report-compare [[:pre pre-compare] [:post post-compare]])
                        (when post-result (test/report post-result))))
                    system))))
            system
            actions)))

;; END Create client

(defn run-tests [test-fns]
  (reduce (fn [system test-fn]
            (let [system (-> (setup-system system) (start-system))
                  merge-chan (a/chan)
                  client (create-client system merge-chan)]
              (-> system
                  (run-test client merge-chan (unwrap test-fn))
                  (stop-system))))
          nil
          test-fns))

(defmethod client-mutate 'fullstack/login
  [{:keys [parser shared] :as env} k {:user/keys [email]}]
  {:action (fn []
             ;; The auth lock for the jvm client cannot show an input dialog
             ;; So it returns a function that takes the email and does the authorization.
             (let [parsed-auth (parser env [{:query/auth [:db/id]}])]
               (debug "Parsed auth: " parsed-auth)
               (when (empty? (:query/auth parsed-auth))
                 (let [lock (auth/show-lock (:shared/auth-lock shared))]
                   (assert (fn? lock) (str "show-lock did not return a function. Was: " lock))
                   (lock {:email email})))
               nil))})

;; TODO: Could define a multimethod for each mutation
;;       that returns reads it should read
;;       and for each read, pre and post paths that should hold.
;;       (For :query/auth in this example, the pre path would
;;       need to check for the current value of :user/email).
;; WOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOW...?
;; We'd also need a generator params of the mutation.
(defn test-store-login-2 []
  (let [test {::tx   `[(fullstack/login {:user/email ~mocked-data/test-user-email})
                       {:query/auth [:user/email]}]
              ::pre  {[:query/auth :user/email] nil}
              ::post {[:query/auth :user/email] mocked-data/test-user-email}}]
    {::label   "Should be able to log in store"
     ::actions [test
                (assoc test ::pre (::post test))]}))

(defn route->route-data [conn route]
  (letfn [(get-id [query]
            (str (db/one-with (db/db conn) query)))]
    (merge
      {:route route}
      (cond
        (#{:checkout :store :store-dashboard} route)
        {:route-params {:store-id (get-id {:where '[[?e :store/owners]]})}}
        (#{:product} route)
        {:route-params {:product-id (get-id {:where '[[?e :store.item/name]]})}}
        (#{:user} route)
        {:route-params {:user-id (get-id {:where '[[?e :user/email]]})}}))))

(defn test-dedupe-parser-returns-super-set-of-original-parser []
  (let [original (parser/client-parser (parser/client-parser-state {::parser/skip-dedupe true}))
        orig-parse (fn [reconciler]
                     (original (#'om/to-env reconciler) (om/get-query router/Router) nil))
        dedupe (parser/client-parser (parser/client-parser-state {::parser/skip-dedupe false}))
        dedupe-parse (fn [reconciler]
                       (dedupe (#'om/to-env reconciler) (om/get-query router/Router) nil))
        route->test-parser-returning-same-thing
        (fn [route]
          {::tx      (fn [system]
                       `[(~'routes/set-route!
                           ~(route->route-data
                              (get-in system [:system/datomic :conn])
                              route))
                         ~@(om/get-query router/Router)])
           ::post-fn (fn [{:keys [reconciler]}]
                       (let [[orig dedup both]
                             (data/diff (orig-parse reconciler)
                                        (dedupe-parse reconciler))]
                         (when (some? dedup)
                           (info "Dedupe got something else: " dedupe))
                         (when (some? orig)
                           {:type     :fail
                            :expected "dedupe parse to have at least everything that original parse had"
                            :actual   orig})))})]
    {::label   "Dedupe parser should return a superset of the original parser"
     ::actions (into [{::tx [:datascript/schema]}]
                     (comp
                       ;; Removes unauthorized because its query doesn't send
                       ;; anything remote, causing our tests to timeout.
                       ;; TODO: Make it possible to create a tx that doesn't
                       ;; send remote.
                       (remove #{:unauthorized})
                       (map route->test-parser-returning-same-thing))
                     router/routes)}))


(test/deftest full-stack-tests
  ;; Runs the test multiple times to make sure things are working
  ;; after setup and tear down.
  (run-tests [test-store-login-2
              test-dedupe-parser-returns-super-set-of-original-parser
              ]))
