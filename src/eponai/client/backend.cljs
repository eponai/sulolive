(ns eponai.client.backend
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<! >! chan]]
            [datascript.core :as d]
            [datascript.db :as db]
            [eponai.common.parser.util :as parser.util]
            [eponai.client.parser.merge :as merge]
            [cljs-http.client :as http]
            [eponai.common.datascript :as e.datascript]
            [taoensso.timbre :refer-macros [debug error trace]]
            [clojure.string :as s]))

(defn html-content-type? [headers]
  (s/starts-with? (or (get headers "content-type")
                      (get headers "Content-Type")
                      "")
                  "text/html"))

(defn- popup-window-with-body [body]
  (let [error-window (.open js/window "" "_blank" "status=0,scrollbars=1, location=0")]
    (try
      (.. error-window -document (write body))
      (finally
        (.. error-window -document close)))))

(defn- popup-window-when-error [k value]
  ;; when there's an error and it's of text/html content type,
  ;; this means we're hopefully in development and we'll
  ;; show the error in a popup.
  (when-let [{:keys [body headers]} (or (when (= k :om.next/error) value)
                       (:om.next/error value))]
    (when (and body (html-content-type? headers))
      (popup-window-with-body body))))

(defn wrap-merge-by-key [env k {:keys [result] :as value}]
  (debug "Merging response for key:" k "value:" value)
  (popup-window-when-error k value)
  (when (and (symbol? k) (:mutation-uuid result))
    (let [db (d/db (:state env))
          mutation-uuid (:mutation-uuid result)
          tx-time (d/q '{:find  [?tx .]
                         :in    [$ ?uuid]
                         :where [[?e :tx/mutation-uuid ?uuid ?tx]
                                 [?e :tx/reverted ?reverted]
                                 [(not ?reverted)]]}
                       db
                       mutation-uuid)
          optimistic-tx-inverse
          (when tx-time
               (->> (db/-search (d/db (:state env)) [nil nil nil tx-time])
                    (map #(update % :added not))
                    (map (fn [[e a v _ added]] [(if added :db/add :db/retract) e a v]))
                    (cons {:tx/mutation-uuid mutation-uuid :tx/reverted true})))

          real-tx (->> (:datoms result)
                       (into [] (comp
                                  (filter (fn [[_ a]] (not= a :tx/mutation-uuid)))
                                  (map (fn [[e a v _ added]] [(if added :db/add :db/retract) e a v])))))

          tx (concat optimistic-tx-inverse real-tx)]
      (debug "Optimistic: " tx)
      (when tx
        (d/transact! (:state env) tx))))
  (merge/merge-novelty env k (if (symbol? k)
                               (update value :result dissoc :mutation-uuid :datoms)
                               value)))

(def merge-novelty-by-key
  "Calls merge-novelty for each [k v] in novelty with an environment including {:state conn}.
  Passes an array with keys to process first."
  (parser.util/post-process-parse wrap-merge-by-key [:datascript/schema]))

(defn- post [url opts]
  (let [transit-opts {:transit-opts
                      {:decoding-opts
                       {:handlers {"n" cljs.reader/read-string}}}}]
    (http/post url (merge opts transit-opts))))

(defn merge!
  [conn]
  (fn [_ _ novelty]
    (debug "Merge! transacting novelty:" novelty)
    (let [_ (merge-novelty-by-key {:state conn} novelty)
          ks (keys novelty)]
      (debug "Merge! returning keys:" ks)
      {:keys ks
       :next  (d/db conn)})))

(defn send!
  [path]
  (fn [{:keys [remote]} cb]
    (let [remote (parser.util/unwrap-proxies remote)]
      (debug "Sending query to remote: " remote)
      (go
        (try
          (let [url (str "/api" path)
                {:keys [body status headers]} (<! (post url {:transit-params remote}))]
            (cond
              ;; Pop up window with the text/html
              ;; Should only really happen in development...?
              (html-content-type? headers)
              (popup-window-with-body body)

              (<= 200 status 299)
              (do
                (debug "Recieved response from remote:" body "status:" status)
                (cb body))

              :else
              (throw (ex-info "Not 2xx response remote."
                              {:remote :remote
                               :status status
                               :url    url
                               :body   body
                               :TODO   "Handle HTTP errors better."}))))
          (catch :default e
            (trace "Error when posting query to remote:" :remote "error:" e)
            (throw e)))))))
