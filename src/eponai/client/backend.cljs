(ns eponai.client.backend
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<! >! chan]]
            [datascript.core :as d]
            [datascript.db :as db]
            [eponai.common.parser.util :as parser.util]
            [eponai.client.homeless :as homeless]
            [eponai.client.parser.merge :as merge]
            [cljs-http.client :as http]
            [taoensso.timbre :refer-macros [debug error trace]]))

(defn merge-novelty-by-key
  ;; TODO: Add comment when this has stabilized.
  "Merges server response for each [k v] in novelty. Returns the next db and the keys to re-read."
  [db novelty]
  {:pre [(db/db? db)]
   :post [(:keys %) (db/db? (:next %))]}
  ;; Merge :datascript/schema first if it exists
  (let [keys-to-merge-first [:datascript/schema]
        ordered-novelty (concat (select-keys novelty keys-to-merge-first)
                                (apply dissoc novelty keys-to-merge-first))]
    (reduce
      (fn [{:keys [next] :as m} [key value]]
        (debug "Merging response for key:" key "value:" value)
        (if (symbol? key)
          (assoc m :next (merge/merge-mutation next key value))
          (-> m
              (assoc :next (merge/merge-read next key value))
              (update :keys conj key))))
      {:keys [] :next db}
      ordered-novelty)))

(defn- post [url opts]
  (let [transit-opts {:transit-opts
                      {:decoding-opts
                       {:handlers {"n" cljs.reader/read-string
                                   "f" cljs.reader/read-string}}}}]
    (http/post url (merge opts transit-opts))))

(defn merge!
  [conn]
  (fn [_ _ novelty]
    (debug "Merge! transacting novelty:" novelty)
    (let [merged-novelty (merge-novelty-by-key (d/db conn) novelty)
          ks (keys novelty)]
      (debug "Merge! returning keys:" ks)
      merged-novelty)))

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
              (homeless/content-type? headers)
              (homeless/popup-window-with-body body)

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
