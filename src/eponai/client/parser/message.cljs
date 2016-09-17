(ns eponai.client.parser.message
  (:require [datascript.db :as db]
            [datascript.core :as d]
            [om.next :as om]
            [eponai.common.parser :as parser]))

(defrecord MutationMessage [mutation message success?])

(defn success? [mm]
  (:success? mm))

(defn get-message [mm]
  (get-in mm [:message (if (success? mm) ::parser/success-message ::parser/error-message)]))

(defn mutation-message? [x]
  (and (map? x)
       (every? #(contains? x %) (keys (map->MutationMessage {})))
       (some? (get-message x))))



;; ---------------------------
;; -- Mutation message protocol

(defprotocol IStoreMessages
  (store-message [this id mutation-message]
                 "Stores a mutation message on the id. Can store multiple messages for each id.")
  (get-messages-fn [this]
                   "Returns a function that takes an id and returns all mutation-messages for that id."))

(defn- get-messages-entity [db]
  (or (into {} (d/entity db [:ui/component :ui.component/mutation-messages]))
      {:ui/component :ui.component/mutation-messages}))

(extend-protocol IStoreMessages
  db/DB
  (store-message [this id mutation-message]
    (assert (mutation-message? mutation-message)
            (str "Mutation message was not a valid mutation-message. Was: " mutation-message))
    (d/db-with this [(update-in (get-messages-entity this)
                                [:ui.component-mutation-messages/messages id]
                                (fnil conj #{})
                                mutation-message)]))
  (get-messages-fn [this]
    (let [entity (get-messages-entity this)]
      (fn [id]
        (get-in entity [:ui.component-mutation-messages/messages id])))))

(defn om-transact!
  "Like om.next/transact! but it returns the history-id generated from the transaction."
  [x tx]
  {:pre [(or (om/component? x)
             (om/reconciler? x))]}
  (om/transact! x tx)
  (parser/reconciler->history-id (cond-> x (om/component? x) (om/get-reconciler))))