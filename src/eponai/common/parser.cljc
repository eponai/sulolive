(ns eponai.common.parser
  (:require [eponai.common.parser.util :as util #?(:clj :refer :cljs :refer-macros) [timeit]]
            [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [debug error info warn trace]]
            [om.next :as om]
            [om.next.cache :as om.cache]
            [eponai.common.database :as db]
            [eponai.common.format.date :as date]
            [datascript.core :as datascript]
    #?(:clj
            [datomic.api :as datomic])
    #?(:clj
            [eponai.server.datomic.filter :as filter])
            [eponai.client.utils :as client.utils]
            [clojure.set :as set]
            [medley.core :as medley])
  #?(:clj
     (:import (clojure.lang ExceptionInfo))))

(defmulti client-read om/dispatch)
(defmulti client-mutate om/dispatch)

(defmulti server-read om/dispatch)
(defmulti server-mutate om/dispatch)
(defmulti server-message om/dispatch)

;; -------- No matching dispatch

(defn default-read [e k p type]
  (cond
    (= "proxy" (namespace k))
    (util/read-join e k p)

    (= "return" (namespace k))
    (util/return e k p)

    (and (= type :server) (= "routing" (namespace k)))
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
                                         (:om.next/error v)
                                         (update-in [k :om.next/error] error->message)))]
         (try
           (trace "Calling parser with body: " query)
           (let [ret (parser env query target)]
             (trace "Called parser with body: " query " returned: " ret)
             (reduce-kv map-parser-errors ret ret))
           (catch Throwable e
             (error e)
             {:om.next/error (error->message e)}))))))

#?(:clj
   (defn wrap-datomic-db
     "Wraps the key :db to the environment with the latest filter.
     Filters are updated incrementally via the ::filter-atom (which
     is renewed everytime parser is called (see wrap-parser-filter-atom)."
     [read-or-mutate]
     (fn [{:keys [state ::filter-atom] :as env} k p]
       (let [db (datomic/db state)
             user-id (get-in env [:auth :username])
             update-filters (fn [old-filters]
                              (let [filters (or old-filters
                                                (if user-id
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
  (fn [{:keys [state] :as env} & args]
    (apply read-or-mutate (assoc env :db (datascript/db state))
           args)))

(defn read-with-dbid-in-query [read]
  (fn [{:keys [query] :as env} k p]
    (let [env (if-not query
                env
                (assoc env :query (if (#{"return" "proxy" "routing"} (namespace k))
                                    query
                                    (util/put-db-id-in-query query))))]
      (read env k p))))

(defn wrap-server-parser-state
  "Returns a parser with a filter-atom assoc'ed in the env."
  [parser]
  (fn [env query & [target]]
    (let [env (assoc env ::filter-atom (atom nil)
                         :user-uuid (get-in env [:auth :username])
                         ::server? true
                         ::force-read-without-history (atom #{}))
          ret (parser env query target)]
      (when-let [keys-to-force-read (not-empty @(::force-read-without-history env))]
        (warn "Did not force read all keys during parse. "
              " Keys left to force read: " keys-to-force-read
              " query: " query))
      ret)))


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

(defn mutate-with-db-before-mutation [mutate]
  (fn [{:keys [target ast reconciler state] :as env} k p]
    (if (nil? reconciler)
      (do
        (assert (:running-tests? env)
                (str "Reconciler was nil in and we are not running tests."
                     " If you are running tests, please use (parser/test-parser)"
                     " instead of (parser/parser)."
                     " If that's not possible, some how assoc {:running-tests? true}"
                     " to parser's env argument."))
        (mutate env k p))
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
   (defmulti read-basis-param-path (fn [_ k _] k)))
#?(:clj
   (defmethod read-basis-param-path :default
     [_ _ _]
     []))

#?(:clj
   (defn read-returning-basis-t [read]
     (fn [{:keys [db ::force-read-without-history] :as env} k p]
       {:pre [(some? db)]}
       (let [param-path (read-basis-param-path env k p)
             _ (assert (or (nil? param-path) (sequential? param-path))
                       (str "Path returned from read-basis-param-path for key: " k " params: " p
                            " was not nil or sequential. Was: " param-path))
             default-path [:eponai.common.parser/read-basis-t (str (:user-uuid env)) k]
             path (into default-path param-path)
             basis-t-for-this-key (get-in env path)
             env (assoc env :db-since (when basis-t-for-this-key
                                        (datomic/since db basis-t-for-this-key)))
             env (if (contains? @force-read-without-history k)
                   (do
                     ;; read the key without the db-history
                     (debug "Force reading key: " k)
                     (swap! force-read-without-history disj k)
                     (dissoc env :db-history))
                   (assoc env :db-history (when basis-t-for-this-key
                                            (datomic/since (datomic/history db)
                                                           basis-t-for-this-key))))
             ret (read env k p)
             ret (cond-> ret
                         (nil? (:value ret))
                         (assoc :value {})
                         ;; Value has not already been set?
                         (not (contains? (meta (:value ret)) :eponai.common.parser/read-basis-t))
                         (update :value vary-meta assoc-in path (datomic/basis-t db)))]
         ret))))

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
                   (assert (and (::error-message msg)
                                (::success-message msg))
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
    (let [tx-meta (cond-> {}
                          (number? (::created-at env))
                          (assoc :tx/mutation-created-at (::created-at env))
                          (some? (:user-uuid env))
                          (assoc :tx/mutation-created-by [:user/uuid (:user-uuid env)]))]
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
  (merge {:read           client-read
          :mutate         client-mutate
          :elide-paths    false
          :txs-by-project (atom {})}
         custom-state))

(defn client-parser
  ([] (client-parser (client-parser-state)))
  ([state]
   {:pre [(every? #(contains? state %) (keys (client-parser-state)))]}
   (make-parser state
                (fn [parser state]
                  (fn [env query & [target]]
                    (parser (assoc env ::server? false
                                       :route-params ((::get-route-params state)))
                            query target)))
                (fn [read {:keys [elide-paths txs-by-project] :as state}]
                  (-> read
                      wrap-datascript-db
                      (with-txs-by-project-atom txs-by-project)
                      (cond-> (not elide-paths)
                              (with-elided-paths (delay (client-parser (assoc state :elide-paths true)))))))
                (fn [mutate {:keys [elide-paths] :as state}]
                  (-> mutate
                      client-mutate-creation-time
                      mutate-with-db-before-mutation
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
                         wrap-datomic-db))
                   (fn [mutate state]
                     (-> mutate
                         mutate-with-tx-meta
                         server-mutate-creation-time-env
                         with-mutation-message
                         mutate-without-history-id-param
                         wrap-datomic-db))))))

(defn test-client-parser
  "Parser used for client tests."
  []
  (let [parser (client-parser)]
    (fn [env query & [target]]
      (parser (assoc env :running-tests? true)
              query
              target))))

;; Case by case middleware. Used when appropriate

(defn parse-without-mutations
  "Returns a parser that removes remote mutations before parsing."
  [parser]
  (fn [env query & [target]]
    (let [query (->> query (into [] (remove #(and (sequential? %) (symbol? (first %))))))]
      (parser env query target))))

(defn parser-require-auth [parser]
  (fn [env query & [target]]
    (assert (some? (get-in env [:auth :username]))
            (str "No auth in parser's env: " env))
    (parser env query target)))
