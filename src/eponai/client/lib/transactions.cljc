(ns eponai.client.lib.transactions
  (:require
    #?(:clj  [clojure.edn :as reader]
       :cljs [cljs.reader :as reader])
            [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [debug]]))

(def full-transaction-pull-pattern
  [:db/id
   :transaction/uuid
   :transaction/title
   :transaction/amount
   :transaction/created-at
   {:transaction/currency [:currency/code
                           :currency/symbol-native
                           :currency/name]}
   {:transaction/category [:category/name]}
   {:transaction/tags [:db/id :tag/name]}
   {:transaction/date [:db/id
                       :date/timestamp
                       :date/ymd
                       :date/day
                       :date/month
                       :date/year]}
   {:transaction/project [:db/id :project/uuid :project/name]}
   {:transaction/type [:db/ident]}
   :transaction/conversion])

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
