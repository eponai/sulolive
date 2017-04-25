(ns eponai.common.parser
  (:require [eponai.common.parser.util :as util #?(:clj :refer :cljs :refer-macros) [timeit]]
            [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [debug error info warn trace]]
            [om.next :as om]
            [om.next.cache :as om.cache]
            [eponai.client.auth :as client.auth]
            [eponai.client.utils :as client.utils]
            [eponai.client.routes :as client.routes]
            [eponai.common.routes :as routes]
            [eponai.common.auth :as auth]
            [eponai.common.database :as db]
            [eponai.common.format.date :as date]
            [datascript.core :as datascript]
    #?(:cljs [pushy.core :as pushy])
    #?(:clj
            [datomic.api :as datomic])
    #?(:clj
            [eponai.server.datomic.filter :as filter])
            [clojure.set :as set]
            [medley.core :as medley]
            [cemerick.url :as url])
  #?(:clj
     (:import (clojure.lang ExceptionInfo))))

(defmulti client-read om/dispatch)
(defmulti client-mutate om/dispatch)

(defmulti server-read om/dispatch)
(defmulti server-mutate om/dispatch)
(defmulti server-message om/dispatch)

(defmulti server-auth-role om/dispatch)
(defmulti client-auth-role om/dispatch)
(defmethod server-auth-role :default
  [_ k p]
  (throw (ex-info (str "auth-role not implemented by: " k)
                  {:key k :params p})))

(defmethod client-auth-role :default
  [_ k p]
  ;; Client is more relaxed, as it can't always decide if it has access or not.. maybe?
  ::auth/public)

;; -------- Messaging protocols
;; TODO: Move to its own protocol namespace? Like om.next.impl.protocol

(defprotocol IStoreMessages
  (store-message [this id mutation-message]
    "Stores a mutation message on the id. Can store multiple messages for each id.")
  (get-messages [this]
    "Returns messages in insertion order.
    Messages should include a :tx to order messages by.")
  (get-message-fn [this]
    "Returns a 2-arity function taking id and mutation-key returning mutation message.
    Messages should include a number in :tx which makes messages time ordered."))

(defprotocol IMutationMessage
  (message [this] "Returns the message")
  (final? [this] "true if the mutation message was returned from the server")
  (pending? [this] "true if we're still waiting for a server response.")
  (success? [this] "true if the server mutation was successful. False is error occured."))

(defrecord MutationMessage [mutation-key message message-type]
  IMutationMessage
  (message [_] message)
  (final? [_] (contains? #{::success-message ::error-message} message-type))
  (pending? [this] (not (final? this)))
  (success? [_] (= ::success-message message-type))
  Object
  (toString [x]
    (str "[MutationMessage " {:message  (message x)
                              :final?   (final? x)
                              :pending? (pending? x)
                              :success? (success? x)
                              :obj      x} "]")))

(extend-protocol IMutationMessage
  nil
  (final? [_] nil)
  (pending? [_] nil)
  (message [_] (throw (ex-info (str "(message) undefined for nil. "
                                    "Use (final?) or (pending?) before calling (message).")
                               {})))
  (success? [_] (throw (ex-info (str "(success?) is undefined for nil."
                                     "Use (final?) first to check if (success?) can be called.")
                                {}))))

(defn ->message-from-server [mutation-key message message-type]
  {:pre [(contains? #{::success-message ::error-message} message-type)]}
  (->MutationMessage mutation-key message message-type))

(defn ->pending-message [mutation]
  (->MutationMessage mutation "" ::pending-message))

;; Auth responder

(defn stateful-auth-responder
  "Returns nil if a method has not been called, otherwise returns true or what it has been called with."
  []
  (let [state (atom {})
        read-then-update-key (fn [k f & args]
                               (let [ret (get @state k)]
                                 (apply swap! state update k f args)
                                 ret))]
    (reify
      auth/IAuthResponder
      (-redirect [this path]
        (read-then-update-key :redirect (fnil conj []) path))
      (-prompt-login [this _]
        (read-then-update-key :login (constantly true)))
      (-unauthorize [this]
        (read-then-update-key :unauthorized (constantly true))))))

;; -------- No matching dispatch

(defn- is-proxy? [k]
  (= "proxy" (namespace k)))

(defn- is-routing? [k]
  (= "routing" (namespace k)))

(defn- is-special-key? [k]
  (or (is-proxy? k)
      (is-routing? k)))

(defn default-read [e k p type]
  (cond
    (is-proxy? k)
    (util/read-join e k p)

    (and (= type :server) (is-routing? k))
    (util/read-join e k p)

    :else
    (warn (str "Read not implemented for key " k {:key k :params p}))))

(defmethod client-read :default [e k p] (default-read e k p :client))
(defmethod server-read :default [e k p] (default-read e k p :server))

(defmethod server-message :default
  [e k p]
  (warn "No message implemented for mutation: " k)
  {::success-message (str "Action " k " was successful!")
   ::error-message   (str "Something went wrong for action: " k)})

;; -------- Debug stuff

(defn wrap-debug-read-or-mutate [read-or-mutate]
  (fn [env k params]
    (debug "parsing key:" k)
    (let [ret (read-or-mutate env k params)]
      (debug "parsed key:" k "returned:" ret)
      ret)))

(defn with-times [read-or-mutate]
  (fn [env k p]
    (timeit (str "parsed: " k)
            (read-or-mutate env k p))))

;; ############ middlewares

(defn assoc-ast-param [{:keys [ast target]} mutation-ret k v]
  (if (nil? target)
    mutation-ret
    (let [ret-target (get mutation-ret target false)
          ast (if (true? ret-target) ast ret-target)]
      (assoc mutation-ret target
                          (cond-> ast
                                  (map? ast)
                                  (assoc-in [:params k] v))))))

(defn update-action [mutation-ret update-fn]
  (cond-> mutation-ret
          (fn? (:action mutation-ret))
          (update :action update-fn)))

#?(:clj
   (defn error->message [err]
     (error err)
     (if-let [msg (::mutation-message (ex-data err))]
       {::mutation-message msg}
       (throw (ex-info (str "Unable to get mutation-message from error ")
                       {:error err
                        :where ::wrap-om-next-error-handler})))))

#?(:clj
   (defn wrap-om-next-error-handler
     "Wraps the parser and calls every value for :om.next/error in the
     result of a parse.

     If parser throws an exception we'll return:
     {:om.next/error e}"
     [parser]
     (fn [env query & [target]]
       (let [map-parser-errors (fn [m k v]
                                 (cond-> m
                                         (and (symbol? k) (:om.next/error v))
                                         (update-in [k :om.next/error] error->message)))]
         (try
           (trace "Calling parser with body: " query)
           (let [ret (parser env query target)]
             (trace "Called parser with body: " query " returned: " ret)
             (reduce-kv map-parser-errors ret ret))
           (catch Throwable e
             (error e)
             {:om.next/error e}))))))

#?(:clj
   (defn wrap-datomic-db
     "Wraps the key :db to the environment with the latest filter.
     Filters are updated incrementally via the ::filter-atom (which
     is renewed everytime parser is called (see wrap-parser-filter-atom)."
     [read-or-mutate]
     (fn [{:keys [state ::filter-atom auth] :as env} k p]
       (let [db (datomic/db state)
             update-filters (fn [old-filters]
                              (let [filters (or old-filters
                                                (if-some [user-id (:user-id auth)]
                                                  (do (debug "Using auth db for user:" user-id)
                                                      (filter/authenticated-db-filters user-id))
                                                  (do (debug "Using non auth db")
                                                      (filter/not-authenticated-db-filters))))]
                                (filter/update-filters db filters)))
             filters (swap! filter-atom update-filters)
             db (filter/apply-filters db filters)]
         (read-or-mutate (assoc env :db db) k p)))))

(defn wrap-datascript-db
  "Wraps a :db in the environment for each read.
  We currently don't have any filters for the client
  side, so we're not using ::filter-atom."
  [read-or-mutate]
  (fn [{:keys [state] :as env} k p]
    (read-or-mutate (assoc env :db (datascript/db state)) k p)))

(defn read-with-dbid-in-query [read]
  (fn [{:keys [query] :as env} k p]
    (let [env (if-not query
                env
                (assoc env :query (if (#{"proxy" "routing"} (namespace k))
                                    query
                                    (util/put-db-id-in-query query))))]
      (read env k p))))

(defn wrap-server-parser-state
  "Returns a parser with a filter-atom assoc'ed in the env."
  [parser]
  (fn [env query & [target]]
    (let [graph-atom (or (::read-basis-t-graph env)
                         (atom (util/graph-read-at-basis-t)))
          env (assoc env ::filter-atom (atom nil)
                         ::server? true
                         ::force-read-without-history (atom #{})
                         ::read-basis-t-graph graph-atom)
          ret (parser env query target)]
      (when-let [keys-to-force-read (not-empty @(::force-read-without-history env))]
        (warn "Did not force read all keys during parse. "
              " Keys left to force read: " keys-to-force-read
              " query: " query))
      (vary-meta ret assoc ::read-basis-t-graph graph-atom))))


(comment
  "om.next main repo version:"
  (defn reconciler->history-id [reconciler]
    (let [history (-> reconciler :config :history)
          last-history-id (om.cache/get-most-recent-id history)]
      ;; Assertions about om.next's history implementation:
      (assert (.-arr history)
              (str "om.next's history had no property (.-arr h)."
                   " Check the implementation of om.next.cache."
                   " history: " history))
      last-history-id)))

;; org.clojars.petterik/om version:
(defn reconciler->history-id [reconciler]
  (let [history (-> reconciler :config :history)
        last-history-id (om.cache/get-most-recent-id history)]
    last-history-id))

(defn- some-reconciler? [env]
  (if (some? (:reconciler env))
    true
    (do (assert (:running-tests? env)
                (str "Reconciler was nil in and we are not running tests."
                     " If you are running tests, please use (parser/test-parser)"
                     " instead of (parser/parser)."
                     " If that's not possible, some how assoc {:running-tests? true}"
                     " to parser's env argument."))
        false)))

(defn mutate-with-db-before-mutation [mutate]
  (fn [{:keys [target ast reconciler state] :as env} k p]
    (if-not (some-reconciler? env)
      (mutate env k p)
      (let [mutation (mutate env k p)
            history-id (reconciler->history-id reconciler)]
        (if (nil? target)
          (update-action
            mutation
            (fn [action]
              (fn []
                (action)
                (datascript/reset-conn! state (client.utils/queue-mutation
                                                (datascript/db state)
                                                history-id
                                                (om/ast->query ast))))))
          (assoc-ast-param env
                           mutation
                           :eponai.client.backend/mutation-db-history-id
                           history-id))))))

(defn remote-mutation? [mutate env k p]
  (let [remote-return (mutate (assoc env :target :any-remote) k p)]
    (and (map? remote-return)
         (or (:remote remote-return)
             (some (fn [k]
                     (when (= "remote" (namespace k))
                       (get remote-return k)))
                   (keys remote-return))))))

(defn with-pending-message [mutate]
  (fn [{:keys [state target] :as env} k p]
    (let [ret (mutate env k p)]
      (if (or (some? target)
              (not (some-reconciler? env)))
        ret
        (cond-> ret
                (remote-mutation? mutate env k p)
                (update :action (fn [action]
                                  (fn []
                                    (when action (action))
                                    (datascript/reset-conn! state (store-message
                                                                    (datascript/db state)
                                                                    (reconciler->history-id (:reconciler env))
                                                                    (->pending-message k)))))))))))


#?(:clj
   (defn mutate-without-history-id-param [mutate]
     (fn [env k p]
       (mutate env k (cond-> p (map? p) (dissoc :eponai.client.backend/mutation-db-history-id))))))

(defn mutate-with-error-logging [mutate]
  (fn [env k p]
    (letfn [(log-error [e where]
              (error (str "Error in the " where " of mutation:") k
                     " thrown: " e
                     " will re-throw."))]
      (try
        (update-action (mutate env k p)
                       (fn [action]
                         (fn []
                           (try
                             (action)
                             (catch #?@(:clj  [Throwable e]
                                        :cljs [:default e])
                                    (log-error e ":action")
                               (throw e))))))
        (catch #?@(:clj  [Throwable e]
                   :cljs [:default e])
               (log-error e "body")
          (throw e))))))

;"Returns a path using params which needs its own read-basis-t.
;For example: We query/transactions by project-uuid. Then we want
;to store a basis-t per project-uuid. So if we query transactions
;by project-eid 1234567890, we'd want to return
;path: [1234567890]"

#?(:clj
   (defmulti read-basis-params (fn [_ k _] k)))
#?(:clj
   (defmethod read-basis-params :default
     [_ _ _]
     []))

#?(:clj
   (defn read-returning-basis-t [read]
     (fn [{:keys [db query]
           ::keys [force-read-without-history read-basis-t-graph] :as env} k p]
       {:pre [(some? db)]}
       (if (or (is-proxy? k) (is-routing? k))
         ;; Do nothing special for routing and proxy keys
         (read env k p)
         (let [read-basis-params (read-basis-params env k p)
               _ (assert (or (nil? read-basis-params)
                             (and (sequential? read-basis-params)
                                  (every? #(and (vector? %) (= 2 (count %)))
                                          read-basis-params)))
                         (str "Path returned from read-basis-param-path for key: " k
                              " params: " p
                              " was not nil or sequential with [k v] pairs. Was: " read-basis-params))
               read-basis-params (into [[:query-hash (hash query)]] read-basis-params)
               basis-t-for-this-key (util/get-basis-t @read-basis-t-graph k read-basis-params)
               env (if (contains? @force-read-without-history k)
                     (do
                       ;; read the key without the db-history
                       (debug "Force reading key: " k)
                       (swap! force-read-without-history disj k)
                       (dissoc env :db-history))
                     (assoc env
                       ::read-basis-t-for-this-key basis-t-for-this-key
                       :db-history (when basis-t-for-this-key
                                     (datomic/since (datomic/history db)
                                                    basis-t-for-this-key))))
               ret (read env k p)
               new-basis-t (or (::value-read-basis-t (meta (:value ret)))
                               (datomic/basis-t db))]
           (swap! read-basis-t-graph util/set-basis-t k new-basis-t read-basis-params)
           ret)))))

(defn value-with-basis-t
  "Use when you want to override the value of basis-t.

  Param v is the value of the :value key returned by read.
  Example usage in a read:
  {:value (parser/value-with-basis-t {:foo 1} 1234)}"
  [v basis-t]
  ((fnil vary-meta {}) v assoc ::value-read-basis-t basis-t))

(defn- wrap-auth [auth-role-method read-or-mutate on-not-authed]
  (fn [env k p]
    (let [roles (auth-role-method env k p)]
      (if (auth/is-public-role? roles)
        (read-or-mutate (dissoc env :auth) k p)
        (let [roles (cond-> roles (keyword? roles) (hash-map true))]
          (if-some [user (auth/authed-user-for-params (:db env) (keys roles) (:auth env) roles)]
            (read-or-mutate (update env :auth #(-> (dissoc % :email) (assoc :user-id user)))
                            k p)
            (on-not-authed (assoc env :auth-roles roles) k p)))))))

(defn wrap-server-read-auth [read]
  (let [read-authed (wrap-auth server-auth-role read (constantly nil))]
    (fn [env k p]
      (if (is-special-key? k)
        (read env k p)
        (read-authed env k p)))))

(defn wrap-server-mutate-auth [mutate]
  (wrap-auth server-auth-role mutate
             (fn [{::keys [auth-responder] :keys [auth]} _ _]
               (let [message (if (empty? auth)
                               (do (auth/-prompt-login auth-responder nil)
                                   "You need to log in to perform this action")
                               (do (auth/-unauthorize auth-responder)
                                   "You are unauthorized to perform this action"))]
                 {::mutation-message {::error-message message}}))))

(defn wrap-client-mutate-auth [mutate]
  (wrap-auth client-auth-role mutate
             (fn [{::keys [auth-responder] :keys [auth ast target auth-roles]} k p]
               (when (nil? target)
                 (debug "Not authed to perform " k " with params: " p " requiring auth-roles: " auth-roles)
                 (if (empty? auth)
                   (auth/-prompt-login auth-responder {:ast ast :params p})
                   (auth/-unauthorize auth-responder))))))

#?(:clj
   (defn with-mutation-message [mutate]
     (fn [env k p]
       (letfn [(x->message [x]
                 (let [success? (not (instance? Throwable x))
                       msg-env (assoc env
                                 ::success? success?
                                 (if success? ::return ::exception) x)
                       msg (server-message msg-env k p)
                       ;; Code for making defining of messages easier.
                       msg (cond-> msg
                                   (and (map? msg) (= [:success :error] (keys msg)))
                                   (set/rename-keys {:success ::success-message
                                                     :error   ::error-message})
                                   (and (vector? msg) (= 2 (count msg)))
                                   (->> (zipmap [::success-message
                                                 ::error-message])))]
                   (assert (and (contains? msg ::error-message)
                                (contains? msg ::success-message))
                           (str "Message for mutation: " k
                                " did not have both " ::error-message
                                " and " ::success-message
                                " Message was: " msg))
                   (assert (= 2 (count (keys msg)))
                           (str "Message for mutation: " k
                                " had more keys than error and success messages."
                                " This is probably a typo. Check it out."))
                   (dissoc msg (if success? ::error-message ::success-message))))]
         (update-action
           (mutate env k p)
           (fn [action]
             (fn []
               (try
                 (let [ret (action)]
                   (assert (or (nil? ret) (map? ret))
                           (str "Returned something from action: " k
                                " but it was not a map. Was: " ret
                                " Can only return maps from actions."))
                   (cond-> {::mutation-message (x->message ret)}
                           (map? ret)
                           (merge ret)))
                 (catch ExceptionInfo ex
                   (.printStackTrace ex)
                   (throw (ex-info (medley/ex-message ex)
                                   (assoc (ex-data ex)
                                     ::mutation-message (x->message ex)))))
                 (catch Throwable e
                   (throw (ex-info (.getMessage e)
                                   {:cause             e
                                    ::mutation-message (x->message e)})))))))))))

(defn client-mutate-creation-time [mutate]
  (fn [env k p]
    (assoc-ast-param env
                     (mutate env k p)
                     ::created-at
                     (or (::created-at p) (date/current-millis)))))

(defn server-mutate-creation-time-env [mutate]
  (fn [env k p]
    (mutate (assoc env ::created-at (::created-at p)) k (dissoc p ::created-at))))

(defn mutate-with-tx-meta [mutate]
  (fn [env k p]
    {:pre [(::created-at env)]}
    (let [user-id (:user-id (:auth env))
          tx-meta (cond-> {}
                          (number? (::created-at env))
                          (assoc :tx/mutation-created-at (::created-at env))
                          (some? user-id)
                          (assoc :tx/mutation-created-by user-id))]
      (update-action (mutate env k p)
                     (fn [action]
                       (fn []
                         (binding [db/*tx-meta* tx-meta]
                           (action))))))))

(def ^:dynamic *parser-allow-remote* true)
(def ^:dynamic *parser-allow-local-read* true)

(defn with-assoced-env [read-or-mutate map-thunk]
  (fn [env k p]
    (read-or-mutate (merge env (map-thunk)) k p)))

(defn with-remote-guard [read-or-mutate]
  (fn [{:keys [target] :as env} k p]
    (let [ret (read-or-mutate env k p)
          ret (cond-> ret
                      ;; Don't return target when we're not allowed to read/mutate remotely.
                      (and (some? target) (false? *parser-allow-remote*))
                      (dissoc target))]
      ret)))

(defn with-local-read-guard [read]
  (fn [{:keys [target] :as env} k p]
    (when (or *parser-allow-local-read*
              (some? target))
      (read env k p))))

(defn with-elided-paths [read-or-mutate child-parser]
  {:pre [(delay? child-parser)]}
  (fn [env k p]
    (read-or-mutate (assoc env :parser @child-parser) k p)))

(defn with-txs-by-project-atom [read txs-by-project]
  {:pre [(some? txs-by-project)]}
  (fn [env k p]
    (read (assoc env :txs-by-project txs-by-project) k p)))

(defn- make-parser [{:keys [read mutate] :as state} parser-mw read-mw mutate-mw]
  (let [read (-> read
                 with-remote-guard
                 with-local-read-guard
                 read-with-dbid-in-query
                 (read-mw state))
        mutate (-> mutate
                   with-remote-guard
                   mutate-with-error-logging
                   (mutate-mw state))]
    (-> (om/parser {:read read :mutate mutate :elide-paths (:elide-paths state)})
        (parser-mw state))))

(defn client-parser-state [& [custom-state]]
  (merge {:read              client-read
          :mutate            client-mutate
          :elide-paths       false
          :txs-by-project    (atom {})
          ::db->route-params (fn [db]
                               (:route-params (client.routes/current-route db)))
          ::db-state         (atom {})}
         custom-state))

(defn- client-query-params [env parser-state]
  #?(:cljs
          (->> (:shared/browser-history (:shared env))
               (pushy/get-token)
               (url/url)
               :query
               (medley/map-keys keyword))
     ;;TODO: Implement a shared protocol to get the query params
     :clj (:query-params parser-state)))

(defn- client-db-state [env state]
  (let [db (db/db (:state env))
        db-state (deref (::db-state state))]
    (if (identical? db (:db db-state))
      db-state
      (reset! (::db-state state) {:db              db
                                  :route-params    ((::db->route-params state) db)
                                  :query-params    (client-query-params env state)
                                  :auth            (when-let [email (client.auth/authed-email db)]
                                                     {:email email})
                                  ::auth-responder (reify
                                                     auth/IAuthResponder
                                                     (-redirect [this path]
                                                       ;; This redirect could be done cljs side with pushy and :shared/browser-history
                                                       ;; but we don't use this yet, so let's not implement it yet.
                                                       (throw (ex-info "Unsupported function -redirect. Implement if needed"
                                                                       {:this this :path path :method :IAuthResponder/-redirect})))
                                                     (-prompt-login [this anything]
                                                       (client.auth/show-lock (:shared/auth-lock (:shared env))))
                                                     (-unauthorize [this]
                                                       ;;TODO: When :shared/jumbotron is implemented (a place where we
                                                       ;;      can show messages to a user), call the jumbotron with
                                                       ;;      an unauthorized message
                                                       #?(:cljs (js/alert "You're unauthorized to do that action"))
                                                       ))}))))

(defn client-parser
  ([] (client-parser (client-parser-state)))
  ([state]
   {:pre [(every? #(contains? state %) (keys (client-parser-state)))]}
   (make-parser state
                (fn [parser state]
                  (fn [env query & [target]]
                    (parser (into env (-> (client-db-state env state)
                                          (assoc ::server? false)))
                            query target)))
                (fn [read {:keys [elide-paths txs-by-project] :as state}]
                  (-> read
                      wrap-datascript-db
                      (with-txs-by-project-atom txs-by-project)
                      (cond-> (not elide-paths)
                              (with-elided-paths (delay (client-parser (assoc state :elide-paths true)))))))
                (fn [mutate {:keys [elide-paths] :as state}]
                  (-> mutate
                      with-pending-message
                      client-mutate-creation-time
                      mutate-with-db-before-mutation
                      wrap-client-mutate-auth
                      wrap-datascript-db
                      (cond-> (not elide-paths)
                              (with-elided-paths (delay (client-parser (assoc state :elide-paths true))))))))))

(defn server-parser-state [& [custom-state]]
  (merge {:read        server-read
          :mutate      server-mutate
          :elide-paths true}
         custom-state))

#?(:clj
   (defn server-parser
     ([] (server-parser (server-parser-state)))
     ([state]
      {:pre [(every? #(contains? state %) (keys (server-parser-state)))]}
      (make-parser state
                   (fn [parser state]
                     (-> parser
                         wrap-server-parser-state
                         wrap-om-next-error-handler))
                   (fn [read state]
                     (-> read
                         read-returning-basis-t
                         wrap-server-read-auth
                         wrap-datomic-db))
                   (fn [mutate state]
                     (-> mutate
                         ;mutate-with-tx-meta
                         server-mutate-creation-time-env
                         with-mutation-message
                         mutate-without-history-id-param
                         wrap-server-mutate-auth
                         wrap-datomic-db))))))

(defn test-client-parser
  "Parser used for client tests."
  []
  (let [parser (client-parser)]
    (fn [env query & [target]]
      (parser (assoc env :running-tests? true)
              query
              target))))

(defn mutation? [x]
  (and (sequential? x)
       (symbol? (first x))))
