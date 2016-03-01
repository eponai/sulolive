(ns eponai.common.parser
  (:refer-clojure :exclude [merge])
  (:require [eponai.common.parser.read :as read]
            [eponai.common.parser.mutate :as mutate]
            [clojure.walk :as walk]
            [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [debug error info]]
    #?(:clj
            [om.next.server :as om]
       :cljs [om.next :as om])
    #?(:clj
            [datomic.api :as d]
       :cljs [datascript.core :as d])
    #?(:clj
            [eponai.server.datomic.filter :as filter])
            [clojure.walk :as w]))


#?(:clj
   (defn default-error-fn [err]
         (if-let [data (ex-data err)]
           (assoc data ::ex-message (.getMessage ^Throwable err))
           (throw (ex-info "Unable to get ex-data from error"
                           {:error err
                            :where ::wrap-om-next-error-handler})))))

#?(:clj
   (defn wrap-om-next-error-handler
     "Wraps the parser and calls every value for :om.next/error in the
     result of a parse with the :parser-error-fn passed in env.

     If parser throws an exception we'll return:
     {:om.next/error (parser-error-fn e)}"
     [parser]
     (fn [env body]
       (let [parser-error-fn (or (:parser-error-fn env) default-error-fn)
             map-parser-errors (fn [m k v]
                                 (if-not (:om.next/error v)
                                   m
                                   (update-in m [k :om.next/error] parser-error-fn)))]
         (try
           (let [ret (parser env body)]
             (reduce-kv map-parser-errors ret ret))
           (catch Exception e
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

(defn put-db-id-in-query [query]
  (cond (map? query)
        (reduce-kv (fn [q k v]
                     (assoc q k (put-db-id-in-query v)))
                   {}
                   query)

        (sequential? query)
        (->> query
             (remove #(= :db/id %))
             (map put-db-id-in-query)
             (cons :db/id)
             (into []))

        :else
        query))

(defn read-with-dbid-in-query [read]
  (fn [{:keys [query] :as env} k p]
    (let [env (if-not query
                env
                (assoc env :query (if (#{"return" "proxy"} (namespace k))
                                    query
                                    (put-db-id-in-query query))))]
      (read env k p))))

(defn wrap-parser-filter-atom
  "Returns a parser with a filter-atom assoc'ed in the env."
  [parser]
  (fn [env & args]
    (apply parser (assoc env ::filter-atom (atom nil))
           args)))

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
          _ (debug "remote? : " remote?)
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
                                          _ (debug "mutation:" k " datoms: " datoms)]
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
          (assert (contains? ret :action) (str "Mutation " k " had no :action."))
          (update ret :action (fn [action]
                                (fn []
                                  (try
                                   (action)
                                   (catch #?@(:clj  [Throwable e]
                                              :cljs [:default e])
                                          (log-error e ":action")
                                     (throw e)))))))
        (catch #?@(:clj  [Throwable e]
                   :cljs [:default e])
               (log-error e "body")
          (throw e))))))

(defn parser
  ([]
   (let [parser (om/parser {:read   (-> read/read
                                        read-without-state
                                        read-with-dbid-in-query
                                        wrap-db)
                            :mutate (-> mutate/mutate
                                        mutate-with-idempotent-invariants
                                        mutate-with-error-logging
                                        wrap-db)})]
     #?(:cljs (fn [env & args]
                (apply parser env args))
        :clj  (-> parser
                  wrap-parser-filter-atom
                  wrap-om-next-error-handler)))))
