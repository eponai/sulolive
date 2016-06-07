(ns eponai.client.lib.transactions
  (:require [clojure.set :as set]
            [cljs.reader :as reader]
            [taoensso.timbre :refer-macros [debug]]))

(defn filter-changed-fields
  "Takes an edited transaction and an original transaction and returns a transaction with
  only the edited values."
  [edited original]
  (debug "Filtering changed fields. Edited: " edited " orig: " original)
  (let [tag-set-fn (fn [tags] (set (map #(select-keys % [:tag/name :tag/status]) tags)))
        changed-fields (reduce-kv
                         (fn [m k v]
                           (let [init-v (get original k)
                                 map-v (condp = k
                                         :transaction/tags #(do
                                                             (debug "tags: " %)
                                                             (filter :tag/status %))
                                         identity)
                                 equal? (condp = k
                                          :transaction/tags
                                          (= (tag-set-fn v)
                                             (tag-set-fn init-v))
                                          :transaction/amount
                                          (= (reader/read-string (str v))
                                             (reader/read-string (str init-v)))

                                          (= v init-v))]
                             (cond-> m (not equal?) (assoc k (map-v v)))))
                         {}
                         edited)]
    changed-fields))

;; TODO: Use a more general diff algorithm?

(defn diff-transaction
  "Returns the difference between two transactions."
  [edited original]
  (filter-changed-fields edited original))
