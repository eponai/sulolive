(ns eponai.client.backend
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<! >! chan]]
            [datascript.core :as d]
            [eponai.client.parser.merge :as merge]
            [cljs-http.client :as http]
            [eponai.common.datascript :as e.datascript]))

(defn- post [url opts]
  (let [transit-opts {:transit-opts
                      {:decoding-opts
                       {:handlers {"n" cljs.reader/read-string}}}}]
    (http/post url (merge opts transit-opts))))

(defn merge-novelty-by-key
  "Calls merge-novelty for each [k v] in novelty with an environment including {:state conn}.
  Implementation detail: Some merge-novelty methods need to be called in order. Doing this with
  a vector of keys to be called first."
  ([env novelty] (merge-novelty-by-key env novelty [:datascript/schema]))
  ([env novelty keys-to-merge-first]
   (let [merge-novelty-subset (fn [novelty novelty-subset]
                                (reduce-kv (fn [m k v]
                                             (let [merged (merge/merge-novelty env k v)]
                                               (cond
                                                 (or (nil? merged) (identical? merged v)) m
                                                 (keyword-identical? merged ::merge/dissoc) (dissoc m k)
                                                 :else (assoc m k merged))))
                                           novelty
                                           novelty-subset))]
     (reduce merge-novelty-subset
             novelty
             [(select-keys novelty keys-to-merge-first)
              (apply dissoc novelty keys-to-merge-first)]))))

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
