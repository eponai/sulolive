(ns eponai.common.parser
  (:refer-clojure :exclude [merge])
  (:require [eponai.common.parser.read :as read]
            [eponai.common.parser.mutate :as mutate]

    #?(:clj [om.next.server :as om]
       :cljs [om.next :as om])
    #?(:clj  [datomic.api :as d]
       :cljs [datascript.core :as d])
    #?(:clj [eponai.server.datomic.filter :as filter])))

(defn wrap-om-next-error-handler
  "For each :om.next/error, replace the value with only the error's
  ex-data.
  Throws an exception if the error did not have any data."
  [parser]
  (fn [& args]
    (let [ret (apply parser args)
          extract-ex-data-from-errors
          (fn [m k v]
            (if-not (:om.next/error v)
              m
              (update-in m [k :om.next/error]
                         (fn [err]
                           (if-let [data (ex-data err)]
                             (assoc data ::ex-message #?(:clj  (.getMessage err)
                                                         :cljs (.-message err)))
                             (throw (ex-info "Unable to get ex-data from error"
                                             {:error err
                                              :where ::wrap-om-next-error-handler
                                              :parsed-key   k
                                              :parsed-value v})))))))]
      (reduce-kv extract-ex-data-from-errors
                 ret
                 ret))))

#?(:clj
   (defn wrap-user-context-only [parser]
     (fn [env & args]
       (let [user-id (get-in env [:auth :username])
             env (assoc env :db (filter/user-db (d/db (:state env))
                                                user-id))]
         (if user-id
           (apply parser env args)
           (throw (ex-info "Unable to get user-id from env"
                           {:env env}))))))
   :cljs
   (defn wrap-db [parser]
     (fn [env & args]
       (apply parser (assoc env :db (d/db (:state env)))
              args))))


(defn wrap-state-rename
  "Removes the state key containing the connection to the database.
  Doing this because reads should not affect the database...?
  Use the key :db instead to get a db to do reads from.."
  [read-or-mutate]
  (fn
    [env & args]
    (apply read-or-mutate (-> env
                 (assoc :unsafe-conn (:state env))
                 (dissoc :state))
           args)))

(defn parser
  ([]
   (let [parser (om/parser {:read   (wrap-state-rename read/read)
                            :mutate (wrap-state-rename mutate/mutate)})]
     #?(:cljs (-> parser
                  wrap-db)
        :clj (-> parser
                 wrap-om-next-error-handler
                 wrap-user-context-only)))))
