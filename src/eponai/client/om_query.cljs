(ns eponai.client.om_query
  (:require [datascript.core :as d]
            [datascript.db   :as d.db]))

(defn query [conn {:keys [find where]} sym entry]
  (d/q (concat [:find]
               find
               [:in '$ sym]
               [:where]
               where)
       (d/db conn)
       entry))

(defn make-seq [x]
  (if (or (seq? x) (vector? x))
    x
    [x]))

(defn read [{:keys [parser state query entity] :as env} attr params]
  (let [ret (cond 
              (keyword? attr) 
              (if (nil? query)
                (-> (d/entity (d/db state) entity)
                    (get attr))
                (let [entities (-> (d/pull (d/db state) [attr] entity)
                                   (get attr)
                                   make-seq
                                   (->> (map :db/id)))
                      query  (if (vector? query)
                               query
                               (:each query))
                      result (map #(parser (assoc env :entity % :selector nil) query)
                                 entities)
                      schema (d.db/-schema (d/db state))]
                  (if (or
                        (= "_" (first (name attr)))
                        (= :db.cardinality/many (get-in schema [attr :db/cardinality])))
                    (vec result)
                    (first result))))

              (symbol? attr)
              (let [entry (:db/id (d/entity (d/db state) (:id params)))
                    res   (-> (query state (:q query) attr entry)
                              (make-seq))]
                (map #(parser (assoc env :entity % :selector nil) (:each query))
                     res)))]
    {:value ret}))

