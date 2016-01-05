(ns eponai.client.backend
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<! >! chan]]
            [datascript.core :as d]
            [eponai.common.parser.util :as parser.util]
            [eponai.client.parser.merge :as merge]
            [cljs-http.client :as http]
            [eponai.common.datascript :as e.datascript]))

(defn- post [url opts]
  (let [transit-opts {:transit-opts
                      {:decoding-opts
                       {:handlers {"n" cljs.reader/read-string}}}}]
    (http/post url (merge opts transit-opts))))

(def merge-novelty-by-key
  "Calls merge-novelty for each [k v] in novelty with an environment including {:state conn}.
  Passes an array with keys to process first."
  (parser.util/post-process-parse merge/merge-novelty [:datascript/schema]))

(defn merge!
  [conn]
  (fn [_ _ novelty]
    (let [novelty (merge-novelty-by-key {:state conn} novelty)
          temp-id-novelty (e.datascript/db-id->temp-id #{} (flatten (vals novelty)))]
      {:keys (keys novelty)
       :next (:db-after @(d/transact conn temp-id-novelty))})))

(defn send!
  [path]
  (fn [{:keys [remote]} cb]
    (let [remote (->> remote
                      (reduce (fn [query x]
                                (if (vector? x)
                                  (concat query (flatten x))
                                  (conj query x)))
                              []))]
      (prn "send to remote: " remote)
      (go
        (try
          (let [{:keys [body]} (<! (post (str "/api" path) {:transit-params remote}))]
            (cb body))
          (catch :default e
            (prn e)))))))
