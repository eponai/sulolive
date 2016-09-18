(ns eponai.client.parser.message
  (:require [datascript.db :as db]
            [datascript.core :as d]
            [om.next :as om]
            [eponai.common.database.pull :as pull]
            [eponai.common.parser :as parser]))

;; -----------------------------
;; -- Mutation message public api

(defprotocol IStoreMessages
  (store-message [this id mutation-message]
                 "Stores a mutation message on the id. Can store multiple messages for each id.")
  (get-messages [this]
                "Returns messages in insertion order")
  (get-message-fn [this]
                  "Returns a 2-arity function taking id and mutation-key returning mutation message."))

(defprotocol IMutationMessage
  (message [this] "Returns the message")
  (final? [this] "true if the mutation message was returned from the server")
  (pending? [this] "true if we're still waiting for a server response.")
  (success? [this] "true if the server mutation was successful."))

(defn mutation-message? [x]
  (satisfies? IMutationMessage x))

(defrecord MutationMessage [mutation-key message message-type]
  IMutationMessage
  (message [_] message)
  (final? [_] (contains? #{::parser/success-message ::parser/error-message} message-type))
  (pending? [this] (not (final? this)))
  (success? [_] (::parser/success-message message-type)))

(defn ->message-from-server [mutation-key message message-type]
  {:pre [(contains? #{::parser/success-message ::parser/error-message} message-type)]}
  (->MutationMessage mutation-key message message-type))

(defn ->pending-message [mutation message]
  (->MutationMessage mutation message nil))

;; ------------------------------------
;; -- Datascript mutation message storage implementation

(defn- new-message [db id mutation-message]
  (d/db-with db [{:mutation-message/message      (message mutation-message)
                  :mutation-message/history-id   id
                  :mutation-message/mutation-key (:mutation-key mutation-message)
                  :mutation-message/message-type (:message-type mutation-message)}]))

(defn- update-message [db old-mm new-mm]
  {:pre [(some? (:db/id old-mm))]}
  (assert (pending? old-mm)
          (str "Stored message was not pending. Can only update pending messages."
               "Old message:" old-mm " new message: " new-mm))
  (d/db-with db [{:db/id                         (:db/id old-mm)
                  :mutation-message/message      (message new-mm)
                  :mutation-message/message-type (:message-type new-mm)}]))

(defn- entity->MutationMessage [entity]
  {:pre [(:db/id entity)]}
  (-> (->MutationMessage (:mutation-message/mutation-key entity)
                         (:mutation-message/message entity)
                         (:mutation-message/message-type entity))
      (assoc :db/id (:db/id entity))))

(extend-protocol IStoreMessages
  db/DB
  (store-message [this history-id mutation-message]
    (assert (some? history-id))
    (assert (mutation-message? mutation-message)
            (str "Mutation message was not a valid mutation-message. Was: " mutation-message))
    (if-let [existing ((get-message-fn this) history-id (:mutation-key mutation-message))]
      (update-message this existing mutation-message)
      (new-message this history-id mutation-message)))
  (get-message-fn [this]
    (fn [history-id mutation-key]
      (assert (some? history-id) (str "Called (get-message-fn ) with history-id nil."
                                      " Needs history-id to look up messages. mutation-key: " mutation-key))
      (some->> (pull/one-with this {:where   '[[?e :mutation-message/history-id ?history-id]
                                               ['?e :mutation-message/mutation-key ?mutation-key]]
                                    :symbols {'?history-id   history-id
                                              '?mutation-key mutation-key}})
               (d/entity this)
               entity->MutationMessage)))
  (get-messages [this]
    (some->> (pull/find-with this {:find-pattern '[?e ?tx]
                                   :where        '[[?e :mutation-message/history-id _ ?tx]]})
             (into (sorted-set-by #(compare (:tx %1) (:tx %2)))
                   (comp (map (fn [[id tx]] (into {:tx tx :db/id id}
                                                (d/entity this id))))
                         (map entity->MutationMessage))))))

(defn om-transact!
  "Like om.next/transact! but it returns the history-id generated from the transaction."
  [x tx]
  {:pre [(or (om/component? x)
             (om/reconciler? x))]}
  (om/transact! x tx)
  (parser/reconciler->history-id (cond-> x (om/component? x) (om/get-reconciler))))
