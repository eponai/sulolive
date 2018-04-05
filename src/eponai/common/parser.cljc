(ns eponai.common.parser
  (:require [eponai.common.parser.util :as util #?(:clj :refer :cljs :refer-macros) [timeit]]
            [taoensso.timbre :as timbre :refer [debug error info warn trace]]
            [om.next :as om]
            [om.next.cache :as om.cache]
            [om.next.impl.parser :as om.parser]
            [eponai.client.auth :as client.auth]
            [eponai.client.utils :as client.utils]
            [eponai.client.routes :as client.routes]
            [eponai.common.routes :as common.routes]
            [eponai.common.auth :as auth]
            [eponai.common.database :as db]
            [eponai.common.format.date :as date]
            [clojure.data :as diff]
            [lajt.read]
            [lajt.parser :as lajt]
            [lajt.read.datascript :as lajt.db]
            [datascript.core :as datascript]
    #?(:cljs [pushy.core :as pushy])
    #?(:clj
            [datomic.api :as datomic])
    #?(:clj
            [eponai.server.datomic.filter :as filter])
            [clojure.set :as set]
            [medley.core :as medley]
            [cemerick.url :as url]
    #?(:clj
            [eponai.server.log :as log])
            [eponai.common.format :as f])
  #?(:clj
     (:import (clojure.lang ExceptionInfo))))

(defmulti lajt-read (fn [k] k))

(defmulti client-read om/dispatch)
(defmulti client-mutate om/dispatch)

(defmulti server-read om/dispatch)
(defmulti server-mutate om/dispatch)
(defmulti server-message om/dispatch)

(defmulti log-param-keys om/dispatch)
(defmethod log-param-keys :default
  [_ _ _]
  ;; Return values:
  ;; vector of keys -> selects the keys from params
  ;; map -> returns (and logs) the map
  ;; nil -> returns no params
  ;; ::no-logging -> skips logging of this mutation/read
  nil)

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

(defn is-proxy? [k]
  (= "proxy" (namespace k)))

(defn is-routing? [k]
  (= "routing" (namespace k)))

(defn is-read-with-state? [k]
  (= :read/with-state k))

(defn is-special-key? [k]
  (or (is-proxy? k)
      (is-routing? k)
      (is-read-with-state? k)))

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

(defmethod client-read :read/with-state [env k p] (util/read-with-state env k p))
(defmethod server-read :read/with-state
  [{:keys [db] :as env} k {:keys [route-params] :as params}]
  (util/read-with-state env k
                        (cond-> params
                                (seq route-params)
                                (update :route-params client.routes/normalize-route-params db))))

(defmethod server-message :default
  [e k p]
  (warn "No message implemented for mutation: " k)
  {::success-message (str "Action " k " was successful!")
   ::error-message   (str "Something went wrong for action: " k)})

;; -------- Debug stuff

(defn wrap-debug-read-or-mutate [read-or-mutate]
  (fn [env k params]
    (debug "parsing key:" k " query: " (:query env))
    (let [ret (read-or-mutate env k params)]
      (debug "parsed key:" k "returned:" ret)
      ret)))

(defn wrap-debug-parser [parser]
  (fn [env query & [target]]
    (debug "parser query: " query " target: " target)
    (parser env query target)))

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
     "Wraps the key :db to the environment with a database filter.
     If passed an atom, it'll cache the filter for consecutive reads or mutates.

     Separating filter caches for reads and mutates (at the moment) because mutates
     might change what a user has access to."
     ([read-or-mutate] (wrap-datomic-db read-or-mutate nil))
     ([read-or-mutate filter-atom]
      (fn [{:keys [state auth] :as env} k p]
        (let [db (datomic/db state)
              auth-filter (if-some [filter (some-> filter-atom deref)]
                            filter
                            (let [authed-user-id
                                  (when (:email auth)
                                    (db/find-with db (db/merge-query (auth/auth-role-query ::auth/any-user auth {})
                                                                     {:find '[?user .]})))
                                  authed-store-ids
                                  (when authed-user-id
                                    (db/find-with db (db/merge-query (auth/auth-role-query ::auth/any-store-owner auth {})
                                                                     {:symbols {'?user authed-user-id}
                                                                      :find    '[[?store ...]]})))
                                  filter (filter/filter-authed authed-user-id authed-store-ids)]
                              (debug "Setting filter using user-id: " authed-user-id
                                     " auth: " auth)
                              (when filter-atom (reset! filter-atom filter))
                              filter))]
          (read-or-mutate (assoc env :db (datomic/filter db auth-filter)
                                     :raw-db db)
                          k
                          p))))))

(defn wrap-datascript-db
  "Wraps a :db in the environment for each read.
  We currently don't have any filters for the client
  side, so we're not using ::filter-atom."
  [read-or-mutate]
  (fn [{:keys [state] :as env} k p]
    (let [db (datascript/db state)]
      ;; associng :raw-db to match server-side where we filter the :db.
      (read-or-mutate (assoc env :db db :raw-db db) k p))))

(defn read-with-dbid-in-query [read]
  (fn [{:keys [query] :as env} k p]
    (let [env (if-not query
                env
                (assoc env :query (if (is-special-key? k)
                                    query
                                    (util/put-db-id-in-query query))))]
      (read env k p))))

(defn wrap-server-parser-state
  "Returns a parser with a filter-atom assoc'ed in the env."
  [parser]
  (fn [env query & [target]]
    (let [graph-atom (or (::read-basis-t-graph env)
                         (atom (util/graph-read-at-basis-t)))
          env (assoc env ::server? true
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
     nil))

#?(:clj
   (defn validate-read-basis-params [env k p x]
     (if (or (nil? x)
             (and (map? x)
                  (every? #(and (vector? %) (== 2 (count %)))
                          (:custom x))
                  (every? keyword? (mapcat #(get x %) [:params :route-params :query-params]))))
       x
       (throw (ex-info (str "Return from read-basis-params for key: " k
                            " was not valid. Was: " x)
                       {:key          k
                        :params       p
                        :route-params (:route-params env)
                        :query-params (:query-params env)
                        :return       x})))))

#?(:clj
   (defn read-returning-basis-t [read]
     (fn [{:keys  [db query locations]
           ::keys [force-read-without-history read-basis-t-graph] :as env} k p]
       {:pre [(some? db)]}
       (if (or (is-proxy? k) (is-routing? k))
         ;; Do nothing special for routing and proxy keys
         (read env k p)
         (let [read-basis-params (->> (read-basis-params env k p)
                                      (validate-read-basis-params env k p))
               route-basis-kvs (or (:custom read-basis-params)
                                   (into []
                                         (mapcat (fn [[param-key param-map]]
                                                   (let [params-order (get read-basis-params param-key)]
                                                     (map (juxt identity #(get param-map %))
                                                          params-order))))
                                         [[:route-params (:route-params env)]
                                          [:query-params (:query-params env)]
                                          [:params p]]))
               read-basis-params (-> []
                                     (into route-basis-kvs)
                                     (conj [:query-hash (hash query)]))
               basis-t-for-this-key (util/get-basis-t @read-basis-t-graph k read-basis-params)
               env (if (contains? @force-read-without-history k)
                     (do
                       ;; read the key without the db-history
                       (debug "Force reading key: " k)
                       (swap! force-read-without-history disj k)
                       (dissoc env :db-history))
                     (assoc env
                       ::read-basis-t-for-this-key basis-t-for-this-key
                       ;; basis-t can be customized by reads to return whatever they want.
                       ;; for example: :query/chat wants to use two basis-t, one for each db.
                       :db-history (when (and (some? basis-t-for-this-key)
                                              (not (coll? basis-t-for-this-key)))
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

(defn- validated-auth-role [auth-role k p]
  (assert (or (keyword? auth-role)
              (and (map? auth-role)
                   (== 1 (count auth-role))
                   (map? (val (first auth-role)))))
          (str "Return of key: " k
               " with params: " p
               " for auth was not either a keyword or a map of auth-params. Was: " auth-role))
  auth-role)

(defn- wrap-auth [auth-role-method read-or-mutate on-not-authed]
  (fn [env k p]
    (let [auth-role (-> (auth-role-method env k p)
                        (validated-auth-role k p))]
      (if (auth/is-public-role? auth-role)
        (read-or-mutate env k p)
        (let [role (cond-> auth-role (map? auth-role) (-> first key))
              auth-params (get auth-role role nil)]
          ;; Use an unfiltered db to check auth.
          (if-some [user (auth/authed-user-for-params (:raw-db env) role (:auth env) auth-params)]
            (read-or-mutate (update env :auth assoc :user-id user)
                            k p)
            (on-not-authed (assoc env :auth-roles auth-role) k p)))))))

(defn wrap-server-read-auth [read]
  (let [read-authed (wrap-auth server-auth-role read
                               (fn [{:keys [auth logger] :as env} k p]
                                 #?(:clj
                                    (when (not-empty auth)
                                      (log/info! logger
                                                 :eponai.server.parser/auth
                                                 {:response-type :unauthorized
                                                  :parser-key    k
                                                  :parser-type   :read})))
                                 (debug "Not authed enough for read: " k
                                        " params: " p
                                        " auth-roles: " (:auth-roles env))))]
    (fn [env k p]
      (if (is-special-key? k)
        (read env k p)
        (read-authed env k p)))))

(defn wrap-server-mutate-auth [mutate]
  (wrap-auth server-auth-role mutate
             (fn [{::keys [auth-responder] :keys [auth logger]} k _]
               (let [message (if (empty? auth)
                               (do (auth/-prompt-login auth-responder nil)
                                   "You need to log in to perform this action")
                               (do (auth/-unauthorize auth-responder)
                                   #?(:clj
                                      (log/info! logger
                                                 :eponai.server.parser/auth
                                                 {:response-type :unauthorized
                                                  :parser-key    k
                                                  :parser-type   :mutate}))
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

#?(:clj
   (defn- thunk-logger
     "Returns a function that takes a thunk (0-arity function) and logs it"
     [env k p]
     (fn [thunk]
       (let [start (System/currentTimeMillis)
             log-then-return
             (fn [x]
               (let [error? (instance? Throwable x)
                     log-fn (if error? log/error! log/info!)
                     end (System/currentTimeMillis)
                     read-basis-t (::read-basis-t-for-this-key env)
                     read? (keyword? k)]
                 (log-fn (:logger env)
                         (keyword (str "eponai.server.parser")
                                  (if read? "read" "mutate"))
                         (merge {:parser-key    k
                                 :parser-type   (if read? :read :mutate)
                                 :response-type (if error? :error :success)
                                 :event-time-ms (- end start)}
                                (cond
                                  error?
                                  {:exception (log/render-exception x)}
                                  read?
                                  (cond-> (f/remove-nil-keys (select-keys env [:route :route-params :query-params]))
                                          (coll? x)
                                          (assoc :return-empty? (empty? x))
                                          (counted? x)
                                          (assoc :return-count (count x))
                                          (some? read-basis-t)
                                          (assoc ::read-basis-t read-basis-t)))))
                 x))]
         (try
           (log-then-return (thunk))
           (catch Throwable e
             (throw (log-then-return e))))))))

#?(:clj
   (defn with-server-logging [read-or-mutate]
     (fn [env k p]
       (let [log-action (thunk-logger env k p)]
         (if (and (keyword? k)
                  (not (is-special-key? k)))
           (log-action #(read-or-mutate env k p))
           (update-action
             (read-or-mutate env k p)
             (fn [action]
               (fn []
                 (log-action action)))))))))

#?(:clj
   (defn with-server-logger [read-or-mutate]
     (fn [{:keys [logger] :as env} k p]
       (read-or-mutate
         (cond-> env
                 (not (is-special-key? k))
                 (assoc :logger
                        (log/with (or logger
                                      ;; Using a no-op-logger for testing environments that don't care.
                                      (force log/no-op-logger))
                                  #(let [param-keys (log-param-keys env k p)]
                                     ;; Returning ::no-logging from log-param-keys will
                                     ;; not log this mutation or read.
                                     (if (= ::no-logging param-keys)
                                       log/skip-message
                                       (assoc % :parse-key k
                                                :parse-type (if (keyword? k) :read :mutation)
                                                :parse-params (if (map? param-keys)
                                                                ;; If implementation of log-params-keys returns a map
                                                                ;; we just return it. It enables us to return the params
                                                                ;; in whatever form we want. For example:
                                                                ;; Flattened keys, anonymous email, changed values.
                                                                param-keys
                                                                (select-keys p param-keys))))))))
         k p))))

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
    (if (and (not *parser-allow-local-read*)
             (nil? target)
             (not (is-special-key? k)))
      ;; If there's a regular read, when reading without target, and we don't care about read results
      ;; return an empty value, making om.next's transact* as efficient as possible.
      {:value {}}
      (read env k p))))

(defn with-elided-paths [read-or-mutate child-parser]
  {:pre [(delay? child-parser)]}
  (fn [env k p]
    (read-or-mutate (assoc env :parser @child-parser) k p)))

(defn with-txs-by-project-atom [read txs-by-project]
  {:pre [(some? txs-by-project)]}
  (fn [env k p]
    (read (assoc env :txs-by-project txs-by-project) k p)))

(defn- make-parser [{:keys [read mutate ->parser parser-state]
                     :or {->parser om/parser}
                     :as state}
                    parser-mw read-mw mutate-mw]
  (let [read (-> read
                 with-remote-guard
                 with-local-read-guard
                 read-with-dbid-in-query
                 (read-mw state))
        mutate (-> mutate
                   with-remote-guard
                   mutate-with-error-logging
                   (mutate-mw state))]
    (-> (->parser (merge
                    {:read        read
                    :mutate      mutate
                    :elide-paths (:elide-paths state)}
                    parser-state))
        (parser-mw (assoc state ::read read)))))

(defn client-parser-state [& [custom-state]]
  (merge {:read           client-read
          :mutate         client-mutate
          ;; We do it manually now.
          :elide-paths    true
          :txs-by-project (atom {})
          ::db-state      (atom {})}
         custom-state))

(defn- client-db-state [env state]
  (let [db (db/db (:state env))
        db-state (deref (::db-state state))
        state (if (identical? db (:db db-state))
                db-state
                (let [{:keys [route route-params query-params]} (client.routes/current-route db)]
                  (reset! (::db-state state)
                          {:db                       db
                           :route                    route
                           ;; Stores different versions of route-params since they differ between
                           ;; target=nil and target=<anything>
                           ::route-params-nil-target  (client.routes/normalize-route-params route-params db)
                           ;; We're not normalizing route-params when we've got a target
                           ;; since normalizing a param can change it's value. But since
                           ;; we're not supposed to use the values in :route-params when
                           ;; we've got a target, but we want to check for the existence
                           ;; of a route-param, we put a fake value for all of its keys.
                           ::route-params-some-target (medley/map-vals (constantly ::use-only-to-check-if-one-got-the-param)
                                                                      route-params)
                           ;; The non-normalized route-params if we need it.
                           :raw-route-params         route-params

                           :query-params             query-params
                           :auth                     (when-let [email (client.auth/authed-email db)]
                                                       {:email email})
                           ::auth-responder          (reify
                                                       auth/IAuthResponder
                                                       (-redirect [this path]
                                                         ;; This redirect could be done cljs side with pushy and :shared/browser-history
                                                         ;; but we don't use this yet, so let's not implement it yet.
                                                         (throw (ex-info "Unsupported function -redirect. Implement if needed"
                                                                         {:this this :path path :method :IAuthResponder/-redirect})))
                                                       (-prompt-login [this anything]
                                                         (client.auth/show-login (:shared/login (:shared env))))
                                                       (-unauthorize [this]
                                                         ;;TODO: When :shared/jumbotron is implemented (a place where we
                                                         ;;      can show messages to a user), call the jumbotron with
                                                         ;;      an unauthorized message
                                                         #?(:cljs (js/alert "You're unauthorized to do that action"))
                                                         ))})))]
    (-> state
        (assoc :route-params (if (nil? (:target env))
                               (::route-params-nil-target state)
                               (::route-params-some-target state)))
        (dissoc ::route-params-nil-target
                ::route-params-some-target))))

(declare dedupe-parser lajto-dedupe-parser)

(defn db-state-parser [parser state]
  (fn self
    ([env query]
     (self env query nil))
    ([env query target]
     (parser (into env (-> (client-db-state (assoc env :target target) state)
                           (assoc ::server? false)
                           (assoc ::read (::read state))))
             query target))))

(defn client-parser
  ([] (client-parser (client-parser-state)))
  ([state]
   {:pre [(every? #(contains? state %) (keys (client-parser-state)))]}
   (make-parser state
                (fn [parser state]
                  (-> parser
                      (lajto-dedupe-parser)
                      #_(cond-> (not (::skip-dedupe state)) (dedupe-parser (::read state)))
                      (db-state-parser state)))
                (fn [read {:keys [txs-by-project]}]
                  (-> read
                      wrap-datascript-db
                      (with-txs-by-project-atom txs-by-project)
                      (cond-> (::debug-reads state) wrap-debug-read-or-mutate)))
                (fn [mutate state]
                  (-> mutate
                      with-pending-message
                      client-mutate-creation-time
                      mutate-with-db-before-mutation
                      wrap-client-mutate-auth
                      wrap-datascript-db)))))

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
                         with-server-logging
                         read-returning-basis-t
                         wrap-server-read-auth
                         with-server-logger
                         (wrap-datomic-db (atom nil))))

                   (fn [mutate state]
                     (-> mutate
                         ;mutate-with-tx-meta
                         server-mutate-creation-time-env
                         with-mutation-message
                         mutate-without-history-id-param
                         with-server-logging
                         wrap-server-mutate-auth
                         with-server-logger
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

;; Dedupe parser
(defn- join-ast-queries [ast-key asts]
  (letfn [(map-pattern [query]
            (into {} (map (fn [x]
                            (cond (keyword? x)
                                  [x nil]
                                  (and (map? x) (= 1 (count x)))
                                  (first x)
                                  :else
                                  (throw (ex-info "Unknown query item" {:query query :item x})))))
                  query))
          (merge-fn [a b]
            (if (and a b)
              (if (and (vector? a) (vector? b))
                (merge-queries [a b])
                (throw (ex-info "Unknown merge conflict" {:a a :b b})))
              (or a b)))
          (unwrap-query [query]
            (into []
                  (if (map? query)
                    (map (fn [[k v]]
                           ;; Unwraps the map around a query {:key nil}
                           ;; where the pattern is nil after joining patterns.
                           (cond-> k (some? v) (hash-map (unwrap-query v)))))
                    (mapcat (fn [x] (cond
                                      (map? x) (unwrap-query x)
                                      (keyword? x) (vector x)
                                      :else
                                      (throw (ex-info "Unable to unwrap query" {:query query
                                                                                :x     x}))))))
                  query))
          (merge-queries [queries]
            (unwrap-query (apply merge-with merge-fn (map map-pattern queries))))]
    (if-let [query (not-empty (merge-queries (map :query asts)))]
      {ast-key query}
      ast-key)))

(defn- flatten-reads
  "Returns map with #{:key :params :query}"
  [env parser-read query]
  (let [target (:target env)
        flattened (atom [])
        ;; TODO: Flatten reads without calling parser.
        ;; Calling parser will create an ast of everything.
        ;; Which takes like ~20+ ms in dev.
        read (fn [env k p]
               (cond (is-routing? k)
                     (parser-read env k p)
                     (is-proxy? k)
                     (util/read-join env k p)
                     :else
                     (let [ast (if (nil? target)
                                 (:ast env)
                                 (let [ret (get (parser-read env k p) target)]
                                   ;; Makes it possible for remote queries to be different then the
                                   ;; original ast
                                   (if (true? ret)
                                     (:ast env)
                                     ret)))]
                       (when (some? ast)
                         (swap! flattened conj ast))))
               ;; Return nil so the parser doesn't have to do anything with the return.
               nil)
        parser (om/parser {:read        read
                           :mutate      (constantly nil)
                           :elide-paths true})]
    (parser env query target)
    (not-empty @flattened)))

(defn- key-params->dedupe-key [key params]
  (keyword "proxy" (str (when-let [ns (namespace key)] (str ns "-"))
                        (name key) "-" (hash params))))

(defn- dedupe-reads [parser-read env reads]
  (let [flattened (flatten-reads env parser-read reads)
        grouped (group-by (juxt :key :params) flattened)
        joined (into [] (map (fn [[[key params] asts]]
                               {:key    key
                                :params params
                                :query  (cond-> (join-ast-queries key asts)
                                                (seq params)
                                                (list params))}))
                     grouped)
        by-key (group-by :key joined)
        deduped-query (into []
                            (mapcat (fn [[k vs]]
                                      ;; More than one query per key requires
                                      ;; multiple proxies... hmm?
                                      (into []
                                            (map (fn [{:keys [params query]}]
                                                   {(key-params->dedupe-key k params)
                                                    [query]}))
                                            vs)))
                            by-key)]
    deduped-query))

(defn dedupe-parser [parser parser-read]
  (let [dedupe-cache (atom {})]
    (fn [env query & [target]]
      ;; assoc the passed parser instead of using this parser.
      (let [env (assoc env :parser parser :target target)
            {mutations true reads false} (group-by mutation? query)
            ;; Do mutations first.
            mutate-ret (when (seq mutations)
                         (parser env mutations target))
            cache-path (delay [target
                               (hash reads)
                               (hash (client.routes/current-route (:state env)))])
            deduped-query (when (seq reads)
                            (if-let [q (get-in @dedupe-cache @cache-path)]
                              q
                              (let [q (dedupe-reads parser-read env reads)]
                                (swap! dedupe-cache assoc-in @cache-path q)
                                q)))
            deduped-parse (parser env deduped-query target)
            deduped-result-parser
            (om/parser
              ;; Eliding paths and adding it in later.
              {:elide-paths true
               :mutate      (constantly nil)
               :read        (fn [env k p]
                              (cond (is-routing? k)
                                    (parser-read env k p)
                                    (is-proxy? k)
                                    (util/read-join env k p)
                                    (is-read-with-state? k)
                                    (util/read-with-state env k p)
                                    :else
                                    (when-let [v (get-in deduped-parse
                                                         [(key-params->dedupe-key k p) k])]
                                      {:value v})))})
            ret (if (some? target)
                  (cond-> mutate-ret
                          (seq deduped-parse)
                          ;; We wrap all reads with state that the reads depend on.
                          ;; This is only used for client-parsers (they are the ones getting :target).
                          ((fnil conj []) (list {:read/with-state deduped-parse}
                                                (-> (select-keys env [:route :raw-route-params :query-params])
                                                    ;; Using the raw route-params when sending to server
                                                    ;; so the server gets a chance to normalize them.
                                                    (set/rename-keys {:raw-route-params :route-params})
                                                    (f/remove-nil-keys)))))
                  (cond->> mutate-ret
                           (seq reads)
                           (merge (om.parser/path-meta (deduped-result-parser env query target)
                                                       (if (contains? env :path) (:path env) [])
                                                       query))))]
        (not-empty ret)))))

(def lajt-conf
  (let [om->lajt-read (-> lajt-read
                          (lajt.read/->read-fn (lajt.db/db-fns))
                          #_(lajt.read/om-next-value-wrapper))
        lajt-read (fn [env k p]
                    (if (contains? (methods lajt-read) k)
                      (om->lajt-read env k p)
                      (do
                        (warn "lajt read not implemented for " k)
                        (let [ret (client-read env k p)]
                          (if-some [t (:target env)]
                            (get ret t)
                            (:value ret))))))
        lajt-mutate (fn [env k p]
                      (let [ret (client-mutate env k p)]
                        (debug "LAJT mutate " k " returned: " ret)
                        (if-some [t (:target env)]
                          (get ret t)
                          (when-some [a (:action ret)]
                            (debug "LAJT executing action: " a)
                            (a)
                            nil))))]
    {:read            lajt-read
     :mutate          lajt-mutate
     :join-namespace  "proxy"
     :union-namespace "routing"
     :union-selector  (fn [env k p]
                        ;; Dispatch to read
                        (debug "Calling read: " (:read env) " with k: " k)
                        (let [ret ((:read env) (dissoc env :query) k p)]
                          (debug "Called k: " k " returned: " ret)
                          (if (map? ret)
                            (do (debug "k: " k " return map!")
                                (:value ret))
                            ret)))}))

(defn lajto-dedupe-parser [parser]
  (fn [env query & [target]]
    (let [env (assoc env :debug true :parser parser)
          dq (lajt/dedupe-query (assoc lajt-conf :read (::read env))
                                (assoc env :debug false)
                                query)]
      (parser env dq target))))

(defn lajt-parser []
  (client-parser
    (client-parser-state
      {::skip-dedupe true
       :read         (:read lajt-conf)
       :mutate       (:mutate lajt-conf)
       :->parser     lajt/parser
       :parser-state lajt-conf})))

(defn multi-parser [test-fn & parsers]
  (fn [env query & [target]]
    (let [mutates (vec (filter mutation? query))
          mutate-ret (when (seq mutates)
                       ((first parsers) env mutates target))
          reads (vec (remove mutation? query))
          reads-ret (->> parsers
                         (map #(% env reads target))
                         (map-indexed vector)
                         vec)]
      (test-fn reads-ret)
      (into (or mutate-ret (if (some? target) [] {}))
            (second (first reads-ret))))))
