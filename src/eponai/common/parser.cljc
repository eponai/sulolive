(ns eponai.common.parser
  (:refer-clojure :exclude [merge])
  (:require [eponai.common.parser.read :as read]
            [eponai.common.parser.mutate :as mutate]
            [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [debug error info]]
    #?(:clj [om.next.server :as om]
       :cljs [om.next :as om])
    #?(:clj  [datomic.api :as d]
       :cljs [datascript.core :as d])
    #?(:clj [eponai.server.datomic.filter :as filter])))


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

(defn wrap-parser-filter-atom
  "Returns a parser with a filter-atom assoc'ed in the env."
  [parser]
  (fn [env & args]
    (apply parser (assoc env ::filter-atom (atom nil))
           args)))

(defn parser
  ([]
   (let [parser (om/parser {:read   (-> read/read read-without-state wrap-db)
                            :mutate (-> mutate/mutate wrap-db)})]
     #?(:cljs parser
        :clj  (-> parser
                  wrap-parser-filter-atom
                  wrap-om-next-error-handler)))))
