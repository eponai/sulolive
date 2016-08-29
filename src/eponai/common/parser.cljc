(ns eponai.common.parser
  (:refer-clojure :exclude [read])
  (:require [eponai.common.parser.util :as util #?(:clj :refer :cljs :refer-macros) [timeit]]
            [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [debug error info warn trace]]
    #?(:clj
            [om.next.server :as om]
       :cljs [om.next :as om])
    #?(:clj
            [datomic.api :as d]
       :cljs [datascript.core :as d])
    #?(:cljs [eponai.client.utils :as client.utils])
    #?(:clj
            [eponai.server.datomic.filter :as filter])
            [clojure.walk :as w]
            [clojure.data :as diff]))

(defmulti read (fn [_ k _] k))
(defmulti mutate (fn [_ k _] k))

;; -------- No matching dispatch

(defmethod read :default
  [e k p]
  (cond
    (= "proxy" (namespace k))
    (util/proxy e k p)

    (= "return" (namespace k))
    (util/return e k p)

    #?@(:clj [(= "routing" (namespace k))
              (util/proxy e k p)])

    :else (warn "Returning nil for parser read key: " k)))

;; -------- Debug stuff

(defn debug-read [env k params]
  (debug "reading key:" k)
  (let [ret (read env k params)]
    (debug "read key:" k "returned:" ret)
    ret))

(defn with-times [read-or-mutate]
  (fn [env k p]
    (timeit (str "parsed: " k)
            (read-or-mutate env k p))))

;; ############ middlewares

#?(:clj
   (defn default-error-fn [err]
     (error err)
         (if-let [data (ex-data err)]
           (assoc data ::ex-message (.getMessage ^Throwable err))
           (throw (ex-info (str "Unable to get ex-data from error " )
                           {:error err
                            :where ::wrap-om-next-error-handler})))))

#?(:clj
   (defn wrap-om-next-error-handler
     "Wraps the parser and calls every value for :om.next/error in the
     result of a parse with the :parser-error-fn passed in env.

     If parser throws an exception we'll return:
     {:om.next/error (parser-error-fn e)}"
     [parser]
     (fn [env query & [target]]
       (let [parser-error-fn (or (:parser-error-fn env) default-error-fn)
             map-parser-errors (fn [m k v]
                                 (if-not (:om.next/error v)
                                   m
                                   (update-in m [k :om.next/error] parser-error-fn)))]
         (try
           (trace "Calling parser with body: " query)
           (let [ret (parser env query target)]
             (trace "Called parser with body: " query " returned: " ret)
             (reduce-kv map-parser-errors ret ret))
           (catch Throwable e
             (error e)
             {:om.next/error (parser-error-fn e)}))))))

#?(:clj
   (defn wrap-db
     "Wraps the key :db to the environment with the latest filter.
     Filters are updated incrementally via the ::filter-atom (which
     is renewed everytime parser is called (see wrap-parser-filter-atom)."
     [read-or-mutate]
     (fn [{:keys [state ::filter-atom] :as env} & args]
       (let [db (d/db state)
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
         (apply read-or-mutate (assoc env :db db)
                args)))))

#?(:cljs
   (defn wrap-db
     "Wraps a :db in the environment for each read.
     We currently don't have any filters for the client
     side, so we're not using ::filter-atom."
     [read-or-mutate]
     (fn [{:keys [state] :as env} & args]
       (apply read-or-mutate (assoc env :db (d/db state))
              args))))

(defn read-without-state
  "Removes the state key containing the connection to the database.
  Doing this because reads should not affect the database...?
  Use the key :db instead to get a db to do reads from..

  If we decide to add the connection back to the env, we should
  put it under some obscure key, so developers know that they
  might be doing something wrong. Obscure key name suggestion:
  :are-you-sure-you-want-the-connection?
  :unsafe-connection
  :this-is-an-antipattern/conn"
  [read]
  (fn [env & args]
    (apply read
           env
          args)))

(defn read-with-dbid-in-query [read]
  (fn [{:keys [query] :as env} k p]
    (let [env (if-not query
                env
                (assoc env :query (if (#{"return" "proxy" "routing"} (namespace k))
                                    query
                                    (util/put-db-id-in-query query))))]
      (read env k p))))

(defn wrap-parser-state
  "Returns a parser with a filter-atom assoc'ed in the env."
  [parser]
  (fn [env query & [target]]
    (parser (assoc env ::filter-atom (atom nil)
                       :user-uuid (get-in env [:auth :username]))
            query
            target)))

#?(:cljs
   (defn mutate-with-db-before-mutation [mutate]
     (fn [{:keys [target ast reconciler state] :as env} k p]
       (let [mutation (mutate env k p)
             target-mutation (get mutation target false)
             history (-> reconciler :config :history)
             last-history-id (last (.-arr history))]

         ;; Assertions about om.next's history implementation:
         (assert (.-arr history)
                 (str "om.next's history had no property (.-arr h)."
                      " Check the implementation of om.next.cache."
                      " history: " history))
         ;; The state hasn't changed yet if we're receiving target nil
         ;; in a mutation.
         (when (and (nil? target)
                    (not (identical? (d/db state)
                                     (om/from-history reconciler last-history-id))))
           (warn (str "Current db and db from history given the last-history-id were"
                      " not identical."
                      " Last-history-id: " last-history-id
                      " mutation: " k
                      " params: " p)))


         (if (nil? target)
           (update mutation :action
                   (fn [f]
                     (when f
                       (fn []
                         (f)
                         (try
                           (d/reset-conn! state (client.utils/queue-mutation
                                                  (d/db state)
                                                  last-history-id
                                                  (om/ast->query ast)))
                           (catch :default e
                             (error e)
                             (throw e)))))))
           (let [ret (if (true? target-mutation) ast target-mutation)]
             (assoc mutation
               target
               (cond-> ret
                       (map? ret)
                       (assoc-in [:params
                                  :eponai.client.backend/mutation-db-history-id]
                                 last-history-id)))))))))

#?(:clj
   (defn mutate-without-history-id-param [mutate]
     (fn [env k p]
       (mutate env k (cond-> p
                             (map? p)
                             (dissoc p
                                     :eponai.client.backend/mutation-db-history-id
                                     ;; TODO: Remove :mutation-uuid from here.
                                     ;; We need to clean up the rest of the code
                                     ;; and remove it from everywhere.
                                     :mutation-uuid))))))

(defn mutate-with-idempotent-invariants  [mutate]
  (fn [{:keys [target ast] :as env} k {:keys [mutation-uuid] :as params}]
    (let [params' (dissoc params :mutation-uuid #?(:clj :remote-sync?))
          env (assoc env :mutation-uuid mutation-uuid)
          ret (mutate env k params')
          remote? (get ret target)
          ;; Normalize mutation return to ast.
          #?@(:cljs [;; Remote sync is only needed when there was an optimistic
                     ;; change on the frontend.
                     ;; TODO: It's possible that we can have an action that
                     ;;       do not require remote-sync. What to do?
                     remote-sync? (and remote? (some? (:action ret)))]
              :clj  [remote-sync? (:remote-sync? params)])
          #?@(:cljs [ret (cond-> ret
                                 remote? (->
                                           (update target #(if (true? %) ast %))
                                           (assoc-in [target :params :remote-sync?] remote-sync?)))])]
      ;; TODO: Turn this into a test instead.
      (when remote?
        (assert (map? ret)))

      (when remote-sync?
        (assert (some? mutation-uuid)
                (str "No mutation-uuid for remote mutation: " k
                     ". Remote mutations needs :mutation-uuid in params "
                     " to synchronize state, params were: " (keys params)))
        (assert (and (map? ret) (:action ret))
                (str "Map with :action key was not returned from mutation: " k
                     " which is remote-symc?: " remote-sync? ". Mutations with"
                     " remote sync needs to return a map with an :action.")))
      (if-not remote-sync?
        ret
        (-> ret
          (update :action (fn [action]
                               (fn []
                                 (let [action-return (action)]
                                   (debug "mutation:" k "remote-sync?: " remote-sync?)
                                   (when remote-sync?
                                     (assert (map? action-return)
                                             (str "Return value from a mutation's :action need to be"
                                                  " a map for remote mutations that requires sync."
                                                  "Mutation:" k " remote sync?:" remote-sync?
                                                  " return value: " action-return))
                                     (assert (contains? action-return :db-after)
                                             (str ":db-after not in mutation action function's return value."
                                                  " keys returned: " (keys action-return)
                                                  ". Need :db-after to validate that mutation-uuid was"
                                                  " transacted to the database."))
                                     (assert (= mutation-uuid
                                                (d/q '{:find  [?uuid .]
                                                       :in    [$ ?uuid]
                                                       :where [[_ :tx/mutation-uuid ?uuid]]}
                                                     (:db-after action-return)
                                                     mutation-uuid))
                                             (str ":tx/mutation-uuid was not stored for mutation: " k
                                                  " mutation-uuid: " mutation-uuid
                                                  ". Mutation id needs to be stored in datascript/datomic"
                                                  " to synchronize state.")))
                                   #?(:cljs action-return
                                      :clj (let [datoms (d/q '{:find  [?e ?attr ?v ?tx ?added]
                                                        :in    [$ [[?e ?a ?v ?tx ?added] ...]]
                                                        :where [[?a :db/ident ?attr]]}
                                                      (:db-after action-return)
                                                      (:tx-data action-return))
                                                 _ (debug "mutation: " k " datoms: " datoms)
                                                 _ (debug "mutation: " k " tx-report: " action-return)]
                                      (assoc action-return :mutation-uuid mutation-uuid
                                                           :datoms datoms))))))))))))

(defn mutate-with-error-logging [mutate]
  (fn [env k p]
    (letfn [(log-error [e where]
              (error (str "Error in the " where " of mutation:") k
                     " thrown: " e
                     " will re-throw."))]
      (try
        (let [ret (mutate env k p)]
          (cond-> ret
                  (fn? (:action ret))
                  (update :action (fn [action]
                                    (fn []
                                      (try
                                        (action)
                                        (catch #?@(:clj  [Throwable e]
                                                   :cljs [:default e])
                                               (log-error e ":action")
                                          (throw e))))))))
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
     (fn [{:keys [db] :as env} k p]
       {:pre [(some? db)]}
       (let [param-path (read-basis-param-path env k p)
             _ (assert (or (nil? param-path) (sequential? param-path))
                       (str "Path returned from read-basis-param-path for key: " k " params: " p
                            " was not nil or sequential. Was: " param-path))
             path (reduce conj [:eponai.common.parser/read-basis-t k] param-path)
             basis-t-for-this-key (get-in env path)
             env (assoc env :db-since (when basis-t-for-this-key
                                        (d/since db basis-t-for-this-key))
                            :db-history (when basis-t-for-this-key
                                          (d/since (d/history db) basis-t-for-this-key)))
             ret (read env k p)]
         (cond-> ret
                 (nil? (:value ret))
                 (assoc :value {})
                 ;; Value has not already been set?
                 (not (contains? (meta (:value ret)) :eponai.common.parser/read-basis-t))
                 (update :value vary-meta assoc-in path (d/basis-t db)))))))

(def ^:dynamic *parser-allow-remote* true)
(def ^:dynamic *parser-allow-local-read* true)

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

(defn default-parser-initial-state []
  #?(:clj {} :cljs {:txs-by-project (atom {})}))

(defn parser
  ([] (parser {}))
  ([parser-opts]
   (parser parser-opts (default-parser-initial-state)))
  ([parser-opts initial-state]
   (let [p (om/parser (merge {:read   (-> read
                                          #?(:clj read-returning-basis-t)
                                          #?(:cljs (with-txs-by-project-atom (:txs-by-project initial-state)))
                                          with-remote-guard
                                          with-local-read-guard
                                          read-without-state
                                          read-with-dbid-in-query
                                          wrap-db
                                          ;; This is interesting, since it'll re-create all middlewares.
                                          #?(:cljs (cond-> (not (:elide-paths parser-opts))
                                                           (with-elided-paths (delay (parser (merge parser-opts {:elide-paths true})
                                                                                             initial-state))))))
                              :mutate (-> mutate
                                          with-remote-guard
                                          ;; mutate-with-idempotent-invariants
                                          mutate-with-error-logging
                                          wrap-db
                                          #?(:cljs mutate-with-db-before-mutation
                                             :clj mutate-without-history-id-param)
                                          #?(:cljs (cond-> (not (:elide-paths parser-opts))
                                                           (with-elided-paths (delay (parser (merge parser-opts {:elide-paths true})
                                                                                             initial-state))))))}
                                  parser-opts))]
     #?(:cljs p
        :clj  (-> p
                  wrap-parser-state
                  wrap-om-next-error-handler)))))

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

