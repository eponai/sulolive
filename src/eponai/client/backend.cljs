(ns eponai.client.backend
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<! >! chan]]
            [datascript.core :as d]
            [eponai.common.parser.util :as parser.util]
            [eponai.client.parser.merge :as merge]
            [cljs-http.client :as http]
            [eponai.common.datascript :as e.datascript]
            [taoensso.timbre :refer-macros [debug error trace]]
            [clojure.string :as s]))

(defn- post [url opts]
  (let [transit-opts {:transit-opts
                      {:decoding-opts
                       {:handlers {"n" cljs.reader/read-string}}}}]
    (http/post url (merge opts transit-opts))))

(defn trace-merge-by-key [env k value]
  (trace "Merging response for key:" k "value:" value)
  (merge/merge-novelty env k value))

(def merge-novelty-by-key
  "Calls merge-novelty for each [k v] in novelty with an environment including {:state conn}.
  Passes an array with keys to process first."
  (parser.util/post-process-parse trace-merge-by-key [:datascript/schema]))

(defn merge!
  [conn]
  (fn [_ _ novelty]
    (let [novelty (merge-novelty-by-key {:state conn} novelty)
          temp-id-novelty (e.datascript/db-id->temp-id #{} (flatten (vals novelty)))
          ks (keys novelty)]
      (debug "Merge! returning keys:" ks)
      (trace "Merge! transacting novelty:" temp-id-novelty)
      {:keys ks
       :next (:db-after @(d/transact conn temp-id-novelty))})))

(defn- popup-window-with-body [body]
  (let [error-window (.open js/window "" "_blank" "status=0,scrollbars=1, location=0")]
    (try
      (.. error-window -document (write body))
      (finally
        (.. error-window -document close)))))

(defn send!
  [path]
  (fn [{:keys [remote]} cb]
    (let [remote (->> remote
                      (reduce (fn [query x]
                                (if (vector? x)
                                  (concat query (flatten x))
                                  (conj query x)))
                              []))]
      (debug "Sending to remote: " :remote " query: " remote)
      (go
        (try
          (let [url (str "/api" path)
                {:keys [body status headers]} (<! (post url {:transit-params remote}))]
            (cond
              ;; Pop up window with the text/html
              ;; Should only really happen in development...?
              (s/starts-with? (get headers "content-type") "text/html")
              (popup-window-with-body body)

              (<= 200 status 299)
              (do
                (debug "Recieved response from remote:" :remote "respone(keys only):" (keys body) "status:" status)
                (trace "Whole response:" body)
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
