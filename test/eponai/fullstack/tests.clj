(ns eponai.fullstack.tests
  (:require [om.next :as om]
            [om.util]
            [eponai.common.database.pull :as pull]
            [eponai.common.parser :as parser]
            [eponai.common.format.date :as date]
            [eponai.common.format :as format]
            [eponai.client.utils :as utils]
            [eponai.server.auth.workflows :as wf]
            [eponai.server.auth.workflows-test :as wf.test]
            [eponai.server.middleware]
            [taoensso.timbre :refer [info debug error]]
            [clojure.data :as diff]
            [clojure.test :as test]
            [datascript.core :as datascript]
            [eponai.fullstack.framework :as fw]
            [eponai.fullstack.jvmclient :as jvmclient :refer [JvmRoot]]
            [clojure.test :as test]
            [eponai.fullstack.utils :as fs.utils]
            [clojure.walk :as walk]
            [aprint.dispatch :as adispatch]
            [datomic.api :as d])
  (:import (org.eclipse.jetty.server Server)
           (datomic.Entity)
           (datascript.impl.entity.Entity)))

(def days-from-now (comp date/date->long date/days-from-now))

(defn- app-state [reconciler]
  (fw/call-parser reconciler (om/get-query (or (om/app-root reconciler) JvmRoot))))

(defn db [client]
  (pull/db* (om/app-state client)))

(defn entity? [x]
  (or
    (instance? datascript.impl.entity.Entity x)
    (instance? datomic.Entity x)))

(defn entity-map
  [client lookup-ref]
  (->> (pull/entity* (db client) lookup-ref)
       (walk/prewalk #(cond->> %
                               (entity? %)
                               (into {:db/id (:db/id %)})))))

(defn equal-app-states? [clients]
  (let [app-states (map app-state clients)
        eq? (apply = app-states)]
    (if eq?
      eq?
      (run! (fn [[a b]]
              (when (not= a b)
                (let [[left right both] (vec (diff/diff a b))]
                  (error "App state NOT eq. diff: ")
                  (error "in left one: " left)
                  (error "in right one: " right)
                  (error "in both: " both))))
            (partition 2 1 app-states)))))

(defn new-transaction [client]
  (let [project-uuid (pull/find-with (pull/db* (om/app-state client))
                                     {:find-pattern '[?uuid .]
                                      :where        '[[_ :project/uuid ?uuid]]})]
    {:transaction/tags       #{{:tag/name "thailand"}}
     :transaction/date       {:date/ymd "2015-10-10"}
     :transaction/type       :transaction.type/expense
     :transaction/currency   {:currency/code "THB"}
     :transaction/title      "lunch"
     :transaction/project    {:project/uuid project-uuid}
     :transaction/uuid       (datascript/squuid)
     :transaction/amount     "180"
     :transaction/created-at 1}))

(defn has-transaction? [tx client]
  (pull/lookup-entity (db client) [:transaction/uuid (:transaction/uuid tx)]))

(def get-transaction has-transaction?)

(defn has-edit?
  ([tx {:keys [edit-fn remove-fn key-fn compare-fn] :as edit} client]
   {:pre [(map? tx) (map? edit) (om/reconciler? client)]}
   ((or compare-fn =)
     (key-fn (cond-> (entity-map client [:transaction/uuid (:transaction/uuid tx)])
                     remove-fn
                     remove-fn))
     (key-fn (cond-> tx
                     edit-fn
                     edit-fn)))))

(defn is-running? [^Server server]
  (.isRunning server))

(defn stop-server! [server & {:keys [asserts]}]
  {:pre [(or (nil? asserts) (fn? asserts))]}
  {::fw/transaction #(do (.stop server)
                         (.join server))
   ::fw/asserts     #(do (assert (not (is-running? server)))
                         (when asserts (asserts)))})

(defn start-server! [server & {:keys [await asserts]}]
  {:pre [(vector? await)]}
  {::fw/transaction   #(.start server)
   ::fw/await-clients await
   ::fw/sync-clients! true
   ::fw/asserts       #(do (assert (is-running? server))
                           (when asserts (asserts)))})

(defn create-transaction! [server clients client tx]
  (fn []
    {::fw/transaction (fn [] [client `[(transaction/create ~tx)]])
     ::fw/asserts     (if (is-running? server)
                        ;; Server is running before creation, assert everyone
                        ;; gets the transaction.
                        (fn []
                          (assert (every? (partial has-transaction? tx) clients))
                          (assert (equal-app-states? clients)))
                        ;; Server was not running. Only the client gets the transaction.
                        (fn []
                          (assert (test/is (has-transaction? tx client)))
                          (assert (test/is (not-any? (partial has-transaction? tx)
                                                     (remove #(= client %) clients))))))}))

(defn edit-transaction! [server clients client tx edit]
  {:pre [(om/reconciler? client)
         (map? tx)]}
  (fn []
    (let [{:keys [edit-fn remove-fn ::parser/created-at]} edit
          _ (assert (or edit-fn remove-fn))
          tx (entity-map client [:transaction/uuid (:transaction/uuid tx)])]
      {::fw/transaction [client `[(transaction/edit ~(assoc {:old (cond-> tx remove-fn remove-fn)
                                                             :new (cond-> tx edit-fn edit-fn)}
                                                       ::parser/created-at created-at))]]
       ::fw/assert      (if (is-running? server)
                          #(assert (every? (partial has-edit? tx edit) clients))
                          #(do (assert (has-edit? tx edit client))
                               (assert (not-any? (partial has-edit? tx edit)
                                                 (remove (partial = client) clients)))))})))

(defmethod print-method om.next.Reconciler [x writer]
  (print-method (str "[Reconciler id=[" (get-in x [:config :id-key]) "]]") writer))

(defmethod adispatch/color-dispatch om.next.Reconciler [x]
  (adispatch/color-dispatch [(str "Reconciler" :id-key (get-in x [:config :id-key]))]))

(defn set-amount [& [x]]
  (fn [tx]
    (assoc tx :transaction/amount (str (or x 4711)))))

(def get-amount #(-> % :transaction/amount bigdec))

(defn test-system-setup [server clients]
  {:label   "System setup should always have a running server."
   :actions [{::fw/transaction [(rand-nth clients) []]
              ::fw/asserts     #(do (assert (.isRunning ^Server server))
                                    (assert (equal-app-states? clients)))}]})

(defn test-create-transaction [server [client1 :as clients]]
  (let [tx (new-transaction client1)]
    {:label   "created transactions should sync"
     :actions [(create-transaction! server clients client1 tx)
               {::fw/transaction [client1 `[(transaction/create ~tx)]]
                ::fw/asserts     (fn []
                                   (assert (every? (partial has-transaction? tx) clients))
                                   (assert (equal-app-states? clients)))}]}))

(defn test-create-transaction-offline [server [client1 :as clients]]
  (let [tx (new-transaction client1)]
    {:label   "Creating transaction offline should sync when client/server goes online"
     :actions [(stop-server! server)
               (create-transaction! server clients client1 tx)
               (start-server! server :await [client1]
                              :asserts #(assert (every? (partial has-transaction? tx) clients)) )]}))

(defn test-edit-transaction [server [client1 :as clients]]
  (let [tx (new-transaction client1)]
    {:label   "edit transaction: Last made edit should persist"
     :actions [(create-transaction! server clients client1 tx)
               (edit-transaction! server clients client1 tx {:edit-fn (set-amount) :key-fn get-amount})]}))

(defn test-edit-transaction-offline [server [client1 client2 :as clients]]
  (let [tx (new-transaction client1)
        c1-edit {:key-fn get-amount ::parser/created-at (days-from-now 2) :edit-fn (set-amount 10)}
        c2-edit {:key-fn get-amount ::parser/created-at (days-from-now 3) :edit-fn (set-amount 20)}]
    {:label   "Last edit should persist"
     :actions [(create-transaction! server clients client1 tx)
               (stop-server! server :asserts #(assert (every? (partial has-transaction? tx) clients)))
               (edit-transaction! server clients client1 tx c1-edit)
               (edit-transaction! server clients client2 tx c2-edit)
               (start-server! server
                              :await [client1 client2]
                              :asserts
                              #(do (assert (not-any? (partial has-edit? tx c1-edit) clients))
                                   (assert (every? (partial has-edit? tx c2-edit) clients))))]}))

(defn- create+edit-offline-test [label & edit-params]
  {:pre [(some? label)]}
  (fn [server [client1 :as clients]]
    (let [tx (new-transaction client1)
          edit (zipmap [:edit-fn :key-fn :compare-fn] edit-params)]
      {:label   label
       :actions [(stop-server! server)
                 (create-transaction! server clients client1 tx)
                 (edit-transaction! server clients client1 tx edit)
                 (start-server! server
                                :await [client1]
                                :asserts #(do (assert (every? (partial has-edit? tx edit) clients))))]})))

(def test-create+edit-amount-offline
  (create+edit-offline-test "create+edit amount offline"
                            (set-amount)
                            get-amount))

(def test-create+edit-title-offline
  (create+edit-offline-test "create+edit title offline"
                            #(assoc % :transaction/title "title")
                            :transaction/title
                            #(test/is (= "title" % %2))))

(def test-create+edit-category-offline
  (create+edit-offline-test "create+edit category offline"
                            #(assoc % :transaction/category {:category/name "category"})
                            (comp :category/name :transaction/category)
                            (partial = "category")))

(defn new-project []
  {:project/uuid (d/squuid)
   :project/name "fullstack test project"})

(defn has-project? [project client]
  (pull/lookup-entity (db client) [:project/uuid (:project/uuid project)]))

(def get-project has-project?)

(defn assert=
  ([a b] (assert= nil a b))
  ([expected a b]
   (letfn [(diff-str [label a b]
             (when (not= a b)
               (str "Diff" (when label (str " " label)) ": " (diff/diff a b) " ")))]
     (if (nil? expected)
       (assert (test/is (= a b))
               (diff-str nil a b))
       (assert (test/is (= a b expected))
               (str (diff-str "(a b)" a b)
                    (diff-str "(a expected)" a expected)
                    (diff-str "(b expected)" b expected)))))
   true))

(defn test-edit-transaction-offline-to-new-offline-project
  [server [client1 :as clients]]
  (let [tx (new-transaction client1)
        proj (new-project)
        uuid (:project/uuid proj)
        edit {:edit-fn    #(assoc % :transaction/project {:project/uuid uuid})
              :key-fn     #(-> % :transaction/project :project/uuid)
              :compare-fn (partial assert= uuid)}]
    {:label   (str "Can create a transaction and project offline, edit the transaction"
                   " to belong to the new project, then sync.")
     :actions [(stop-server! server)
               (create-transaction! server clients client1 tx)
               {::fw/transaction [client1 `[(project/save ~proj)]]
                ::fw/asserts     #(do (assert (has-project? proj client1))
                                      (assert (not-any? (partial has-project? proj)
                                                        (remove (partial = client1) clients))))}
               (edit-transaction! server clients client1 tx edit)
               (start-server! server :await [client1])
               {::fw/transaction   (constantly nil)
                ::fw/sync-clients! true
                ::fw/asserts       #(do (assert (every? (partial has-transaction? tx) clients))
                                        (assert (every? (partial has-project? proj) clients))
                                        (assert (every? (fn [client]
                                                          (do
                                                            (info "has edit? : " client)
                                                            (has-edit? tx edit client)))
                                                        clients)))}]}))

(defn compose-edits [edits]
  (let [edit-maps (sort-by ::parser/created-at edits)
        comp-edits (fn [k]
                     (fn [x]
                       (->> ((apply juxt (map #(get % k identity) edit-maps)) x)
                            (reduce utils/deep-merge x))))]
    {:edit-fn    (comp-edits :edit-fn)
     :remove-fn  (comp-edits :remove-fn)
     :key-fn     (or (some->> edit-maps
                              (map :key-fn)
                              (filter some?)
                              (seq)
                              (apply juxt))
                     ::no-key-fn)
     :compare-fn assert=}))

(defn edits->asserts [edits assert-per-client]
  (fn [clients tx]
    (let [composed-edits (compose-edits edits)]
      (when (= ::no-key-fn (:key-fn composed-edits))
        (assert (some? assert-per-client)
                (str "There's no :key-fn in edits and there's no asserts-per-client."
                     " We need to compare something. Either pass a :key-fn or an assert fn."
                     " edits: " edits)))
      (assert (every? (partial has-transaction? tx) clients))
      (assert (every? (partial has-edit? tx composed-edits) clients))
      (when assert-per-client
        (assert-per-client clients tx)))))

(defn create-two-client-edit-test [label actions & [assert-per-client]]
  (fn [server clients]
    (let [edits (map second (filter vector? actions))
          asserts (edits->asserts edits assert-per-client)
          tx (new-transaction (first clients))]
      {:label   label
       :actions (-> [(create-transaction! server clients (first clients) tx)]
                    (into (map (fn [action]
                                 (cond
                                   (= ::sync! action)
                                   (fn []
                                     {::fw/transaction   (constantly nil)
                                      ::fw/sync-clients! true})

                                   (= ::stop-server! action)
                                   (stop-server! server)

                                   (and (map? action) (= ::start-server! (ffirst action)))
                                   (start-server! server
                                                  :await (mapv #(nth clients %) (:await action)))
                                   ;; Edit:
                                   (vector? action)
                                   (fn []
                                     (let [[client-idx created-at edit] action
                                           client (nth clients client-idx)]
                                       (assert (number? client-idx))
                                       (assert (number? created-at))
                                       (assert (map? edit))
                                       (dissoc ((edit-transaction!
                                                  server clients client tx
                                                  (assoc edit ::parser/created-at
                                                              (+ (days-from-now 1) created-at))))
                                               ::fw/asserts)))
                                   :else
                                   (throw (ex-info (str "Unknown action: " action)
                                                   {:action  action
                                                    :actions actions})))))
                          actions)
                    (conj {::fw/transaction []
                           ::fw/asserts     #(asserts clients tx)}))})))


(def test-two-client-edit-amount
  (create-two-client-edit-test "amount test"
                               [[0 3000 {:edit-fn (set-amount 10) :key-fn get-amount}]
                                [1 2000 {:edit-fn (set-amount 20) :key-fn get-amount}]
                                ::sync!]
                               (fn [clients tx]
                                 (every? #(assert= (bigdec 10) (get-amount (get-transaction tx %)))
                                         clients))))

(defn add-tag [& names]
  (fn [tx]
    (update tx :transaction/tags (fnil into #{}) (map #(hash-map :tag/name %)) names)))

(defn remove-tag [name]
  (fn [tx]
    (update tx :transaction/tags (fn [tags]
                                   (assert (some #(= name (:tag/name %)) tags)
                                           (str "nope. : " tx))
                                   (->> tags (into #{} (remove #(= name (:tag/name %)))))))))

(defn clients-tags-are-equal [expected]
  (fn [clients tx]
    (apply assert= (into (set expected) (map :tag/name) (:transaction/tags tx))
           (into [] (comp (take 2)
                          (map (partial get-transaction tx))
                          (map #(into #{} (map :tag/name) (:transaction/tags %))))
                 clients))))

(def test-two-client-edit-tags-1
  (create-two-client-edit-test "tag test adds"
                               [[0 3000 {:edit-fn (add-tag "lunch")}]
                                [1 2000 {:edit-fn (add-tag "dinner")}]]
                               (clients-tags-are-equal #{"lunch" "dinner"})))

(def test-two-client-edit-tags-2
  (create-two-client-edit-test "tag test add and remove"
                               [[0 3000 {:edit-fn (add-tag "foo")}]
                                [1 4000 {:remove-fn (add-tag "foo")}]]
                               (clients-tags-are-equal #{})))

(def test-two-client-edit-tags-offline+sync-1
  (create-two-client-edit-test "tag test"
                               [::stop-server!
                                [0 2000 {:edit-fn (add-tag "dinner")}]
                                [0 4001 {:edit-fn (add-tag "lunch")}]
                                [1 2000 {:edit-fn (add-tag "lunch" "food")}]
                                [1 4000 {:remove-fn (add-tag "lunch")}]
                                {::start-server! {:await [0 1]}}
                                [1 3000 {:remove-fn (add-tag "food")}]
                                ;; Why do we need to sync here again?
                                ;; Because concurrent requests doens't
                                ;; return all retractions needed?
                                ::sync!]
                               (clients-tags-are-equal #{"dinner" "lunch"})))

(def test-two-client-edit-tags-offline+sync-2
  (create-two-client-edit-test "tag test 2"
                               [::stop-server!
                                [0 2000 {:edit-fn (add-tag "dinner")}]
                                [0 8000 {:edit-fn (add-tag "lunch")}]
                                {::start-server! {:await [0]}}
                                ::sync!
                                [1 4000 {:remove-fn (add-tag "lunch")}]]
                               (clients-tags-are-equal #{"dinner" "lunch"})))

(defn set-title [title]
  (fn [tx]
    (assoc tx :transaction/title title)))

(def test-two-client-edit-titles-offline+sync
  (create-two-client-edit-test (str "Edge case where we set the same value twice at different times"
                                    " and we shouldn't get the retract.")
                               [::stop-server!
                                [0 2000 {:edit-fn #(-> % ((set-title "cafe")) ((set-amount 10)))}]
                                {::start-server! {:await [0 1]}}
                                [1 3000 {:remove-fn #(dissoc % :transaction/title)
                                         :edit-fn (set-title "cafe")}]
                                [0 2500 {:edit-fn #(dissoc % :transaction/title)}]]
                               (fn [clients tx]
                                 (apply assert= "cafe" (into [] (comp (map #(get-transaction tx %))
                                                                      (map :transaction/title))
                                                             clients)))))

(defn get-email [client]
  (let [emails (pull/all-with (db client) {:where '[[?user :user/email ?e]]})]
    (assert= 1 (count emails))
    (first emails)))

(defn test-create-new-user-with-email [server clients]
  (let [new-email "foo@bar.com"]
    {:label   "Log out and create a new user with email"
     :actions [{::fw/asserts #(assert (every? (fn [client] (not= new-email (get-email client))) clients))}
               {::fw/transaction #(run! jvmclient/log-out! clients)}
               {::fw/transaction #(run! (fn [c]
                                          (jvmclient/log-in! c (jvmclient/log-in-with-email new-email)))
                                        clients)}
               {::fw/sync-clients! true
                ::fw/asserts       #(assert (every? (fn [client] (assert= new-email (get-email client)))
                                                    clients))}]}))

;; Facebook login params
(def user-id "user-id")
(def access-token "long-lived-token")

(defn with-fb-meta [email f]
  (with-meta f {:system {:server {:wrap-state
                                  {::wf/create-account-without-throwing true
                                   :facebook-token-validator            (wf.test/test-facebook-token-validator
                                                                          {:email    email
                                                                           :user-id  user-id
                                                                           :token    access-token
                                                                           :is-valid true})}}}}))

(defn returning-nil
  "Wraps a function in a new function that returns nil"
  [f]
  (fn [& args]
    (apply f args)
    nil))

(defn test-create-new-user-with-facebook [email]
  (with-fb-meta
    email
    (fn [server [client1]]
      ;; This test only works for 1 client. Why? Because facebook login is complicated?
      ;; TODO: Write a test for two clients interacting with facebook.
      (let [default-email "some@email.com"]
        {:label   "Log out and create a new user with facebook"
         :actions [{::fw/transaction (returning-nil #(jvmclient/log-out! client1))}
                   {::fw/transaction (returning-nil #(jvmclient/log-in! client1
                                                                        (jvmclient/log-in-with-facebook
                                                                          user-id access-token email default-email)))}
                   {::fw/sync-clients! true
                    ::fw/asserts       #(assert (assert= (if email email default-email) (get-email client1)))}]}))))

(defn create-log-out-log-in-test
  "Takes a number of runs, a client and a seq (maybe infinite) of login functions and verifies that we never
  create duplicate projects."
  [n client login-fns & [email]]
  (let [project-atom (atom nil)
        email (or email "email@tests.cljs")
        log-out-log-in-verify-project
        (fn [login-fn]
          [{::fw/transaction (returning-nil #(jvmclient/log-out! client))
            ::fw/asserts     #(do (when @project-atom
                                    (assert= nil (pull/lookup-entity (db client) (first @project-atom))))
                                  (assert= nil (seq (pull/all-with (db client) {:where '[[?e :user/uuid]]}))))
            ;; TODO: Make sure we can't access /api/user ?
            }
           {::fw/transaction   (returning-nil #(jvmclient/log-in! client (login-fn email)))
            ::fw/sync-clients! true
            ::fw/asserts       #(let [projects (pull/find-with (db client)
                                                               {:find-pattern '[?e ?uuid]
                                                                :where        '[[?e :project/uuid ?uuid]]})
                                      _ (assert= 1 (count projects))
                                      [project-eid project-uuid :as project] (first projects)]
                                 (if-let [[last-eid last-uuid] @project-atom]
                                   (do (assert= project-eid last-eid)
                                       (assert= project-uuid last-uuid))
                                   (do (reset! project-atom project))))}])]
    {:actions (into []
                    (comp (take n) (mapcat log-out-log-in-verify-project))
                    login-fns)}))

(defn test-new-user-with-email-never-gets-duplicate-projects [server [client1]]
  (create-log-out-log-in-test 5 client1 (repeat #(jvmclient/log-in-with-email %))))

(defn facebook-login-fns [email]
  ;; The first run we use the email associated with the facebook account.
  (cons (fn [default-email]
          (jvmclient/log-in-with-facebook user-id access-token email default-email))
        ;; The rest of the runs we use the default-email if we had no email
        ;; associated with our facebook acount.
        (repeat (fn [default-email]
                  (jvmclient/log-in-with-facebook user-id access-token
                                                  (if (nil? email)
                                                    (do (assert (some? default-email)) default-email)
                                                    email)
                                                  ;; The "default-email" should never be used
                                                  ;; after the first login.
                                                  nil)))))

(defn test-facebook-user-never-gets-duplicate-projects [email]
  (with-fb-meta
    email
    (fn [server [client1]]
      (create-log-out-log-in-test 5 client1 (facebook-login-fns email)))))

(defn test-alternating-facebook-and-email-login-never-get-dups [email]
  (with-fb-meta
    email
    (fn [server [client]]
      (create-log-out-log-in-test
        10 client
        (interleave (facebook-login-fns email)
                    (repeat #(jvmclient/log-in-with-email (or email %))))))))

(defn test-alternating-email-and-facebook-login-never-get-dups []
  (let [email "email-then-facebook@test.clj"]
    (with-fb-meta
      email
      (fn [server [client]]
        (create-log-out-log-in-test
          10 client
          (interleave (repeat #(do (assert (= email %))
                                   (jvmclient/log-in-with-email %)))
                      (repeat #(jvmclient/log-in-with-facebook user-id access-token % nil)))
          email)))))

(defn run []
  (fs.utils/with-less-loud-logger :info
    #(-> (fw/run-tests (->> [
                             test-system-setup
                             test-create-transaction
                             test-edit-transaction
                             test-create-transaction-offline
                             test-edit-transaction-offline
                             test-create+edit-amount-offline
                             test-create+edit-title-offline
                             test-create+edit-category-offline
                             test-edit-transaction-offline-to-new-offline-project
                             test-two-client-edit-amount
                             test-two-client-edit-tags-1
                             test-two-client-edit-tags-2
                             test-two-client-edit-tags-offline+sync-1
                             test-two-client-edit-tags-offline+sync-2
                             test-two-client-edit-titles-offline+sync
                             test-create-new-user-with-email
                             (test-create-new-user-with-facebook "facebook@test.com")
                             (test-create-new-user-with-facebook nil)
                             test-new-user-with-email-never-gets-duplicate-projects
                             (test-facebook-user-never-gets-duplicate-projects "facebook@test.com")
                             (test-facebook-user-never-gets-duplicate-projects nil)
                             (test-alternating-facebook-and-email-login-never-get-dups nil)
                             (test-alternating-facebook-and-email-login-never-get-dups "facebook@test.com")
                             (test-alternating-email-and-facebook-login-never-get-dups)
                             (test-alternating-email-and-facebook-login-never-get-dups)
                             ]
                            ;(filter (partial = test-new-user-with-email-never-gets-duplicate-projects))
                            ;(reverse)
                            ;(take 6)
                        ))
         (fw/result-summary))))

(test/deftest run-fullstack-tests
  (let [ret (run)]
    (test/is (zero? (:failures ret)))
    (test/is (zero? (:skips ret)))
    (test/is (pos? (:successes ret)))))