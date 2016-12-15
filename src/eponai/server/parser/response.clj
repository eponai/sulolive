(ns eponai.server.parser.response
  (:require
    [clojure.core.async :refer [go <!]]
    [taoensso.timbre :refer [debug error trace info warn]]))

(defn empty-coll? [c]
  (or
    (and (map? c) (every? #(empty-coll? (val %)) c))
    (and (coll? c) (empty? c))))

;; function to use with eponai.common.parser/post-process-parse
(defmulti response-handler (fn [_ k _] k))
(defmethod response-handler :default
  [_ k v]
  (trace "no response-handler for key:" k)
  (cond
    (#{"proxy" "routing"} (namespace k)) :call
    ;; Check for empty responses
    (keyword? k) (when (empty-coll? v) :dissoc)

    :else nil))

(defn remove-mutation-tx-reports
  "Removes :db-after, :db-before and :tx-data from our
  mutations' return values."
  [response]
  (reduce-kv (fn [m k _]
               (if-not (symbol? k)
                 m
                 (update-in m [k :result] dissoc :db-after :db-before :tx-data :tempids)))
             response
             response))