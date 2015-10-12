(ns flipmunks.budget.om_query
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

(defn read [{:keys [parser state selector entity] :as env} attr params]
  (let [ret (cond 
              (keyword? attr) 
              (if (nil? selector)
                (-> (d/entity (d/db state) entity)
                    (get attr))
                (let [entities (-> (d/pull (d/db state) [attr] entity)
                                   (get attr)
                                   make-seq
                                   (->> (map :db/id)))
                      query  (if (vector? selector)
                               selector
                               (:each selector))
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
                    res   (-> (query state (:q selector) attr entry)
                              (make-seq))]
                (map #(parser (assoc env :entity % :selector nil) (:each selector)) 
                     res)))]
    {:value ret}))

