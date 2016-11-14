(ns eponai.client.parser.merge
  (:require [datascript.core :as d]
            [datascript.db :as db]
            [eponai.common.parser :as parser]
            [eponai.client.parser.message :as message]
            [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [info debug error trace warn]]
            [eponai.client.utils :as utils]))

(defn transact [db tx]
  (if (empty? tx)
    db
    (let [tx (if (sequential? tx) tx [tx])]
      (d/db-with db tx))))

(defn merge-error [db key val]
  (error "error on key:" key "error:" val ". Doing nothing with it.")
  db)

(defn merge-schema [db _ datascript-schema]
  (let [current-schema (:schema db)
        current-entities (d/q '[:find [(pull ?e [*]) ...] :where [?e]] db)
        new-schema (merge-with merge current-schema datascript-schema)
        new-conn (d/create-conn new-schema)]
    (d/transact new-conn current-entities)
    (d/db new-conn)))

;(defn merge-current-user [db _ current-user]
;  (debug "IS MERGING CURRENT_USER: " current-user)
;  (if (:error current-user)
;    (d/db-with db [[:db.fn/retractEntity [:ui/singleton :ui.singleton/auth]]])
;    (d/db-with db [{:ui/singleton           :ui.singleton/auth
;                    :ui.singleton.auth/user current-user}])))

(defn merge-transactions [db _ {:keys [transactions conversions refs]}]
  (-> db
      (transact refs)
      ;;(transact conversions)
      ;;(transact transactions)
      ))

(defn merge-mutation-message [db history-id key val message-type]
  (let [message (get-in val [::parser/mutation-message message-type])]
    (debug "Mutation message-type" message-type "for :" key " message: " message)
    (message/store-message db history-id (message/->message-from-server key message message-type))))

(defn merge-signout [db key val]
  ;;TODO: We probably need to do more retracting.
  (letfn [(all-entities-with-attr [attr]
            (into [] (map :e) (d/datoms db :aevt attr)))]
    (transact db (into [] (comp (mapcat all-entities-with-attr)
                                (map #(vector :db.fn/retractEntity %)))
                       [:user/uuid
                        :transaction/uuid
                        :project/uuid
                        :eponai.common.parser.read-basis-t/map]))))

;;;;;;; API

(defn merge-mutation [merge-fn db history-id key val]
  {:pre  [(methods merge-fn)
          (db/db? db)
          (symbol? key)]
   :post [(db/db? %)]}

  ;; Passing db to all functions for mutate.
  ;; We may want to do this for merge-read also.
  (cond-> db
          (contains? (methods merge-fn) key)
          (merge-fn key val)

          (some? (get-in val [:om.next/error ::parser/mutation-message]))
          (merge-mutation-message history-id key (:om.next/error val) ::parser/error-message)

          (some? (get-in val [:result ::parser/mutation-message]))
          (merge-mutation-message history-id key (:result val) ::parser/success-message)

          (= key 'session/signout)
          (merge-signout key val)))

;; TODO: Fix comments in this namespace.
;; TODO: Clean up merge-read and merge-mutate now that we have a merge-fn.
(defn merge-read [merge-fn db key val]
  {:pre [(methods merge-fn) (db/db? db)]
   :post [(db/db? %)]}

  ;; Dispatch proxy first, so our merge-fn doesn't have to
  ;; implement that themselves.
  (if (#{"proxy" "routing"} (namespace key))
    (reduce-kv (fn [db k v] (merge-read merge-fn db k v))
               db
               val)
    (cond

      (contains? (methods merge-fn) key)
      (merge-fn db key val)

      (or (= key :om.next/error)
          (some? (:om.next/error val)))
      (merge-error db key val)

      (= :datascript/schema key)
      (merge-schema db key val)

      (= :query/transactions key)
      (merge-transactions db key val)

      ;;(= :user/current key)
      ;;(merge-current-user db key val)

      :else
      (transact db val))))

(defn merge-novelty-by-key
  ;; TODO: Add comment when this has stabilized.
  "Merges server response for each [k v] in novelty. Returns the next db and the keys to re-read."
  [merge-fn db novelty history-id]
  {:pre [(methods merge-fn) (db/db? db)]
   :post [(:keys %) (db/db? (:next %))]}
  ;; Merge :datascript/schema first if it exists
  (let [keys-to-merge-first (select-keys novelty [:datascript/schema :user/current])
        other-novelty (apply dissoc novelty keys-to-merge-first)
        ordered-novelty (concat keys-to-merge-first other-novelty)]
    (reduce
      (fn [{:keys [next] :as m} [key value]]
        (debug "Merging response for key:" key "value:" value)
        (if (symbol? key)
          (do (assert (some? history-id)
                      (str "No history-id was provided when merging mutations for novelty: " ordered-novelty))
              (-> m
                  (assoc :next (merge-mutation merge-fn next history-id key value))
                  (update :keys conj :query/message-fn)))
          (-> m
              (assoc :next (merge-read merge-fn next key value))
              (update :keys conj key))))
      {:keys [] :next db}
      ordered-novelty)))

(defn merge-meta [db novelty-meta]
  {:post [(db/db? %)]}
  (let [new-map (:eponai.common.parser/read-basis-t novelty-meta)
        old-map (:eponai.common.parser.read-basis-t/map
                  (d/entity db [:db/ident :eponai.common.parser/read-basis-t]))
        merged-map (utils/deep-merge old-map new-map)]
    ;; TODO: Only transact if the read-basis-t has changed?
    ;; (maybe it doesn't matter when there are a lot of users).
    (cond-> db
            (some? merged-map)
            (transact {:db/ident                              :eponai.common.parser/read-basis-t
                       :eponai.common.parser.read-basis-t/map merged-map}))))

(defn merge!
  "Takes a merge-fn which is passed [db key params] and
  should return db. Hook for specific platforms to do
  arbitrary actions by key.
  Returns merge function for om.next's reconciler's :merge"
  [merge-fn]
  (fn [reconciler current-db {:keys [db result meta history-id] :as novelty} query]
    #?(:cljs (debug "Merge! transacting novelty:" (update novelty :db :max-tx)))
    (let [db (cond-> db (some? meta) (merge-meta meta))
          ret (if result
                (merge-novelty-by-key merge-fn db result history-id)
                ;; TODO: What keys can we pass to force re-render?
                {:next db :keys []})]
      #?(:cljs (debug "Merge! returning keys:" (:keys ret) " with db: " (-> ret :next :max-tx)))
      ret)))
