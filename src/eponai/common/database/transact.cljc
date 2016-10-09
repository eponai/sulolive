(ns eponai.common.database.transact
  (:require [datascript.core :as datascript]
            #?(:clj [datomic.api :as datomic])
            [clojure.walk :as walk]
            [medley.core :as medley]
            [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [trace debug error]])
  #?(:clj (:import [datomic Connection])))

(defprotocol ITransact
  (transact* [conn txs]))

(declare convert-datomic-ids)

(extend-protocol ITransact
  #?@(:clj [Connection
            (transact* [conn txs] (datomic/transact conn txs))
            clojure.lang.Atom
            (transact* [conn txs]
              ;; Convert datomic id's to datascript ids when running jvmclient.
              (datascript/transact conn (convert-datomic-ids txs)))]
      :cljs [Atom
             (transact* [conn txs] (datascript/transact conn txs))]))

(defn transact
  "Transact a collecion of entites into datomic.
  Throws ExceptionInfo if transaction failed."
  [conn txs]
  (try
    (trace "Transacting: " txs)
    (let [ret @(transact* conn txs)]
      ret)
    (catch #?@(:clj [Exception e] :cljs [:default e])
           (let [#?@(:clj  [msg (.getMessage e)]
                     :cljs [msg (.-message e)])]
             (error "Transaction error: " e)
             (throw (ex-info msg
                             {:cause     ::transaction-error
                              :data      {:conn conn
                                          :txs  txs}
                              :message   msg
                              :exception e
                              #?@(:clj [:status :eponai.server.http/service-unavailable])}))))))

(defn transact-map
  "Transact a map into datomic, where the keys names the entities to be transacted for developer convenience.

  Will call transact on (vals m)."
  [conn m]
  (transact conn (vals m)))

(defn transact-one
  "Transact a single entity or transaction into datomic"
  [conn value]
  (transact conn [value]))

(defn transact-schemas [conn schemas]
  (doseq [schema schemas]
    (transact conn schema)))

;; --------------------------------------------
;; -- Datomic to datascript temp-id conversion

#?(:clj
   (def datomic-tempid-type (type (datomic/tempid :db.part/user))))
#?(:clj
   (def datomic-tempid-keys (keys (datomic/tempid :db.part/user))))

#?(:clj
   (defn datomic-id->datascript-id [tempid]
     (assert (= [:part :idx] datomic-tempid-keys)
             (str "Implementation of datomic tempid has changed."
                  " Keys are now: " datomic-tempid-type))
     (datascript/tempid (:part tempid))))

#?(:clj
   (defn convert-datomic-ids
     ([txs] (convert-datomic-ids txs (memoize datomic-id->datascript-id)))
     ([txs datomic->ds-fn]
      (->> txs
           (walk/postwalk #(cond-> %
                                   (instance? datomic-tempid-type %)
                                   (datomic->ds-fn)))
           (into [])))))


(comment
  ;; A faster version for converting datomic ids, but it's not as general
  ;; so it might be wrong.
  (defn convert-datomic-ids
    ([x] (convert-datomic-ids x (memoize datomic-id->datascript-id)))
    ([x datomic->ds-fn]
     (cond
       (instance? datomic-tempid-type x)
       (datomic->ds-fn x)
       (record? x) x
       (map? x)
       (medley/map-vals #(convert-datomic-ids % datomic->ds-fn) x)
       (coll? x)
       (mapv #(convert-datomic-ids % datomic->ds-fn) x)
       :else x))))
