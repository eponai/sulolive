(ns eponai.client.lib.transactions
  (:require [clojure.set :as set]))

(defn mark-removed-tags
  "Takes an edited transaction and an original transaction, marks every removed tag
  with :removed true."
  [edited original]
  (let [original-tags-by-name (group-by :tag/name (:transaction/tags original))]
    (cond-> edited
            (or (seq (:transaction/tags original))
                (seq (:transaction/tags edited)))
            (update :transaction/tags
                    (fn [tags]
                      (let [edited-tags-names (into #{} (map :tag/name) tags)
                            removed-tags (set/difference (set (keys original-tags-by-name))
                                                         edited-tags-names)]
                        (into tags (comp (mapcat
                                           (fn [tags] (map #(assoc % :tag/removed true) tags)))
                                         (remove
                                           #(and (:db/id %) (not (:tag/removed %)))))
                              (vals (select-keys original-tags-by-name removed-tags)))))))))

(defn filter-changed-fields
  "Takes an edited transaction and an original transaction and returns a transaction with
  only the edited values."
  [edited original]
  (let [tag-set-fn (fn [tags] (set (map #(select-keys % [:tag/name :tag/removed]) tags)))
        changed-fields (reduce-kv
                         (fn [m k v]
                           (let [init-v (get original k)
                                 equal? (if (= :transaction/tags k)
                                          (= (tag-set-fn v)
                                             (tag-set-fn init-v))
                                          (= v init-v))]
                             (cond-> m
                                     (not equal?) (assoc k v))))
                         {}
                         edited)]
    changed-fields))

;; TODO: Use a more general diff algorithm?

(defn diff-transaction
  "Returns the difference between two transactions."
  [edited original]
  (-> edited
      (mark-removed-tags original)
      (filter-changed-fields original)))