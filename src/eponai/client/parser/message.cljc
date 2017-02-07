(ns eponai.client.parser.message
  (:require [datascript.db :as db]
            [datascript.core :as d]
            [om.next :as om]
            [eponai.common.database :as database]
            [eponai.common.parser :as parser]
            [taoensso.timbre :refer [debug warn error]])
  #?(:clj (:import [datascript.db DB])))

;; Alias for public API to make it easier to use.
(def pending? parser/pending?)
(def final? parser/final?)
(def success? parser/success?)
(def message parser/message)

;; -----------------------------
;; -- Mutation message public api

(extend-protocol parser/IMutationMessage
  nil
  (final? [_] nil)
  (pending? [_] nil)
  (message [_] (throw (ex-info (str "(message) undefined for nil. "
                                    "Use (final?) or (pending?) before calling (message).")
                               {})))
  (success? [_] (throw (ex-info (str "(success?) is undefined for nil."
                                     "Use (final?) first to check if (success?) can be called.")
                                {}))))

(defn mutation-message? [x]
  (and (satisfies? parser/IMutationMessage x)
       (some? (message x))))

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
  (-> (parser/->MutationMessage (:mutation-message/mutation-key entity)
                                (:mutation-message/message entity)
                                (:mutation-message/message-type entity))
      (assoc :db/id (:db/id entity))))

(defn- store-message-datascript [this history-id mutation-message]
  (assert (some? history-id))
  (assert (mutation-message? mutation-message)
          (str "Mutation message was not a valid mutation-message. Was: " mutation-message))
  (let [existing ((parser/get-message-fn this) history-id (:mutation-key mutation-message))]
    (debug "Storing message with history-id: " [history-id mutation-message :existing? existing])
    (if existing
      (update-message this existing mutation-message)
      (new-message this history-id mutation-message))))

(defn- get-message-fn-datascript [this]
  (fn [history-id mutation-key]
    (assert (some? history-id) (str "Called (get-message-fn ) with history-id nil."
                                    " Needs history-id to look up messages. mutation-key: " mutation-key))
    (some->> (database/one-with this {:where   '[[?e :mutation-message/history-id ?history-id]
                                                 [?e :mutation-message/mutation-key ?mutation-key]]
                                      :symbols {'?history-id   history-id
                                                '?mutation-key mutation-key}})
             (d/entity this)
             entity->MutationMessage)))

(defn- get-messages-datascript [this]
  (some->> (database/find-with this {:find  '[?e ?tx]
                                     :where '[[?e :mutation-message/history-id _ ?tx]]})
           (into []
                 (comp (map (fn [[id tx]] (into {:tx tx :db/id id}
                                                (d/entity this id))))
                       (map entity->MutationMessage)))
           (sort-by :tx)))

(extend-protocol parser/IStoreMessages
  #?@(:clj  [DB
             (store-message [this history-id mutation-message]
               (store-message-datascript this history-id mutation-message))
             (get-message-fn [this]
               (get-message-fn-datascript this))
             (get-messages [this]
               (get-messages-datascript this))]
      :cljs [db/DB
             (store-message [this history-id mutation-message]
                            (store-message-datascript this history-id mutation-message))
             (get-message-fn [this]
                             (get-message-fn-datascript this))
             (get-messages [this]
                           (get-messages-datascript this))]))

;;;;;;;;;;;;;;;; Developer facing API. ;;;;;;;;;;;;;;;;;;
;; Usage:
;; (require '[eponai.client.parser.message :as msg])
;;
;; Use `om-transact!` to perform mutation and get history-id.
;; (let [history-id (msg/om-transact! this '[(mutate/this) :read/that])]
;;   (om/update-state! this assoc :pending-action {:id history-id :mutation 'mutate/this}))
;;
;; Use `find-message` to retrieve your mesage:
;; (let [{:keys [pending-action]} (om/get-state this)
;;       message (msg/find-message this (:id pending-action) (:mutation pending-action))]
;;   (if (msg/final? message)
;;     ;; Do fancy stuff with either success or error message.
;;     ;; Message pending, render spinner or something?
;;    ))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Cache the assert for preventing it to happen everytime it's called.
(let [cache (atom {})]
  (defn- assert-query
    "Makes sure component has :query/messages in its query, so it is
    refreshed when messages arrive."
    [component]
    (let [{:keys [components queries]} @cache
          query (om/get-query component)]
      (when-not (or (contains? components component)
                    (contains? queries (om/get-query component)))
        (let [parser (om/parser {:read   (fn [_ k _]
                                           (when (= k :query/messages)
                                             {:value true}))
                                 :mutate (constantly nil)})
              has-query-messages? (:query/messages (parser nil query))]
          (when-not has-query-messages?
            (error (str "Component did not have :query/messages in its query."
                        " Having :query/messages is needed for the component to"
                        " get refreshed when new messages are merged."
                        " Component: " (pr-str component))))
          (swap! cache (fn [m] (-> m (update :queries (fnil conj #{}) query
                                             :components (fnil conj #{}) component)))))))))

(defn om-transact!
  "Like om.next/transact! but it returns the history-id generated from the transaction."
  [x tx]
  {:pre [(or (om/component? x)
             (om/reconciler? x))]}
  (om/transact! x tx)
  (let [history-id (parser/reconciler->history-id (cond-> x (om/component? x) (om/get-reconciler)))]
    (when (om/component? x)
      (assert-query x)
      (om/update-state! x update ::component-messages
                        (fn [messages]
                          (let [mutation-keys (sequence (comp (filter coll?)
                                                              (map first)
                                                              (filter symbol?))
                                                        tx)]
                            (reduce (fn [m mutation-key]
                                      (update m mutation-key (fnil conj []) history-id))
                                    messages
                                    mutation-keys)))))
    history-id))


(defn find-message
  "Takes a component, a history id and a mutation-key which was used in the mutation
  and returns the message or nil if not found."
  [component history-id mutation-key]
  {:pre [(om/component? component)]}
  (let [db (d/db (om/app-state (om/get-reconciler component)))
        msg-fn (parser/get-message-fn db)]
    (msg-fn history-id mutation-key)))

(defn- message-ids-for-key [c k]
  (get-in (om/get-state c) [::component-messages k]))

(defn all-messages [component mutation-key]
  (assert-query component)
  (map #(find-message component % mutation-key)
       (message-ids-for-key component mutation-key)))

(defn one-message [component mutation-key]
  (let [messages (all-messages component mutation-key)]
    (when (< 1 (count messages))
      (warn "Found more than one message in call to `one-message` for component: " (pr-str component)
            " mutation-key: " mutation-key
            ". There is possibly a bug in your UI?"
            " If your component performs multiple messages, please keep track of the history id's"
            " returned by `om-transact!` and use `find-message` instead."))
    (first messages)))

(defn clear-messages! [component mutation-key]
  (om/update-state! component ::component-messages dissoc mutation-key))

(defn clear-one-message! [component mutation-key]
  (when (< 1 (count (message-ids-for-key component mutation-key)))
    (warn "Found more than one message in call to clear-one-message for component: " (pr-str component)
          " mutation-key: " mutation-key
          ". Will clear all messages."
          ". There is possibly a bug in your UI?"
          " Use `clear-messages` if you want to clear multiple messages."))
  (clear-messages! component mutation-key))

(defn message-status [component mutation-key & [not-found]]
  (if-let [msg (one-message component mutation-key)]
    (cond (pending? msg)
          ::pending
          (final? msg)
          (if (success? msg) ::success ::failure)
          :else
          (throw (ex-info "Message was neither success, error or failure."
                          {:message      msg
                           :component    (pr-str component)
                           :mutation-key mutation-key})))
    (or not-found ::not-found)))

(defn message-data [component mutation-key]
  (message (one-message component mutation-key)))
