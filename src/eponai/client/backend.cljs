(ns eponai.client.backend
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<! >! chan]]
            [datascript.core :as d]
            [cljs-http.client :as http]
            [eponai.client.datascript :as e.datascript]))

(defn- post [url opts]
  (let [transit-opts {:transit-opts
                      {:decoding-opts
                       {:handlers {"n" cljs.reader/read-string}}}}]
    (http/post url (merge opts transit-opts))))

(defn merge!
  [conn]
  (fn [_ _ novelty]
    (let [temp-id-novelty (e.datascript/db-id->temp-id #{} (flatten (vals novelty)))]
      {:keys (keys novelty)
      :next (:db-after @(d/transact conn temp-id-novelty))})))

(defn send!
  []
  (fn [{:keys [remote]} cb]
    (let [remote (->> remote
                      (reduce (fn [query x]
                                (if (vector? x)
                                  (concat query (flatten x))
                                  (conj query x)))
                              []))]
      (prn "send to remote: " remote)
      (go
        (let [{:keys [body]} (<! (post "/api/user/"
                                       {:transit-params remote}))]
          (cb body))))))

(defn db-schema []
  (http/get "/api/schema"))
