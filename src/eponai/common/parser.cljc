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
    #?(:clj
            [eponai.server.datomic.filter :as filter])
            [clojure.walk :as w]))

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
                (assoc env :query (if (#{"return" "proxy"} (namespace k))
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
                  (update ret :action (fn [action]
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
                                        (d/since db basis-t-for-this-key)))
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
  (fn [env k p]
    (when *parser-allow-local-read*
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
                                          mutate-with-idempotent-invariants
                                          mutate-with-error-logging
                                          wrap-db
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

;; ---- Parser caching middleware ---------------

(defn- traverse-query
  "Given a query and a path, traverse the query until the end of the path."
  [query [p :as path]]
  (cond
    (or (empty? path) (= p ::root))
    query

    ;; Queries doesn't know or care about cardinality.
    ;; Skip this part of the path and continue with the
    ;; same query.
    (number? p)
    (recur query (rest path))

    ;; No where to traverse here.
    (keyword? query)
    nil

    (map? query)
    (when (contains? query p)
      (recur (get query p) (rest path)))

    ;; Query is sequential, return the first matching path.
    (sequential? query)
    (some #(traverse-query % path) query)

    :else
    (do (debug "not traversing query: " query)
        nil)))

(defn path->paths [path]
  {:pre [(vector? path)]}
  (when (seq path)
    (reduce conj [path] (path->paths (subvec path 0 (dec (count path)))))))

(defn- find-cached-props
  "Given a cache with map of path->"
  [cache c-path c-query]
  (let [find-props (fn [{:keys [::query ::props]}]
                     (let [t-query (traverse-query query c-path)
                           ct-query (traverse-query c-query c-path)]
                       (when (= ct-query t-query)
                         (let [c-props (get-in props c-path)]
                           (when (some? c-props)
                             (debug "found cached props for c-path: " c-path))
                           c-props))))
        ret (->> (butlast c-path)
                 (vec)
                 (path->paths)
                 (cons [::root])
                 (map #(get-in cache %))
                 (some find-props))]
    ret))

#?(:cljs
   (defn component-parser
     "Requires :component in the env. Using the query and the components
     path, we can re-use already parsed components.

     Example:
     Let's say we've got components A and B.
     B is a subquery of A, i.e. query A: [... (om/get-query B) ...]
     When we parse A, we'll get the props of both A and B.
     We can get B's props by using (om/path B) in props of A.
     This is what we're doing with (find-cached-props ...)."
     [parser]
     (let [cache (atom {})]
       (fn self
         ([env query] (self env query nil))
         ([{:keys [state component] :as env} query _]
          {:pre [(om/component? component)]}
          (let [path (om/path component)
                db (d/db state)
                cache-db (::db @cache)]
            (if (= db cache-db)
              ;; db's are equal, swap to make sure they are identical
              ;; for future equality checks.
              (swap! cache assoc ::db db)
              ;; db's are not equal. Reset the cache.
              (reset! cache {::db db}))

            (let [props (or (find-cached-props @cache path query)
                            (parser env query))]
              (swap! cache update-in
                     (or (seq path) [::root])
                     merge
                     {::query query
                      ::props props})
              props)))))))

;; ---- END Parser caching middleware ------------
