(ns eponai.client.parser.merge
  (:require [datascript.core :as d]
            [taoensso.timbre :refer-macros [info debug error trace]]))

(defmulti merge-novelty (fn [_ k _] k))
(defmethod merge-novelty :default
  [_ k _]
  (trace "No merge-novelty for key: " k)
  nil)

(defmethod merge-novelty :datascript/schema
  [{:keys [state]} _ datascript-schema]
  (let [current-schema (:schema @state)
        current-entities (d/q '[:find [(pull ?e [*]) ...] :where [?e]] (d/db state))
        new-schema (merge-with merge current-schema datascript-schema)
        new-conn (d/create-conn new-schema)]
    ;; Setting the app/inited to true, so we can make desicions about this later. As of writing this,
    ;; it's used to not include the :datascript/schema key for remote reads.
    (d/transact new-conn current-entities)
    (reset! state @new-conn))
    :dissoc)

(defmethod merge-novelty 'transaction/create
  [_ _ novelty]
  (when-let [err (:om.next/error novelty)]
    (error "error from transaction/create:" err)
    (info "TODO: Do something about transaction/create errors!")
    :dissoc))
