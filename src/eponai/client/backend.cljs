(ns eponai.client.backend
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<! >! chan timeout]]
            [eponai.common.parser.util :as parser.util]
            [cljs-http.client :as http]
            [datascript.impl.entity :as e]
            [cognitect.transit :as transit]
            [taoensso.timbre :refer-macros [debug error trace]]))

(def DatascriptEntityAsMap (transit/write-handler (constantly "map")
                                                  (fn [v] (into {} v))))

(defn- send [send-fn url opts]
  (let [transit-opts {:transit-opts
                      {:encoding-opts {:handlers {e/Entity DatascriptEntityAsMap}}
                       :decoding-opts
                       ;; favor ClojureScript UUIDs instead of Transit UUIDs
                       ;; https://github.com/cognitect/transit-cljs/pull/10
                                      {:handlers {"u" uuid
                                                  "n" cljs.reader/read-string
                                                  "f" cljs.reader/read-string}}}}]
    (send-fn url (merge opts transit-opts))))

(defn query-transactions->ds-txs [novelty]
  (let [{:keys [transactions conversions]} (->> (:result novelty)
                                                (tree-seq map? vals)
                                                (filter #(and (map? %) (contains? % :query/transactions)))
                                                (map :query/transactions)
                                                (apply merge-with conj))]
    ;; transact conversions before transactions
    ;; because transactions depend on conversions.
    (concat conversions (cond-> transactions
                                (seq transactions)
                                (rseq)))))

(defn- send-query! [remote->send cb remote-key query]
  (let [query (parser.util/unwrap-proxies query)]
    (go
      (try
        (let [{:keys [method url opts response-fn]} ((get remote->send remote-key) query)
              _ (debug "Sending to " remote-key " query: " query
                       "method: " method " url: " url "opts: " opts)
              {:keys [body status headers]}
              (response-fn (<! (send (condp = method
                                       :get http/get
                                       :post http/post)
                                     url opts)))]
          (cond
            (<= 200 status 299)
            (do
              (debug "Recieved response from remote:" body "status:" status)
              (let [txs (query-transactions->ds-txs body)]
                (cb body)
                (loop [txs txs]
                  (when (seq txs)
                    (<! (timeout 0))
                    (let [[head tail] (split-at 100 txs)]
                      (cb {:result {:proxy/app-content {:just/transact head}}})
                      (recur tail))))))

            :else
            (throw (ex-info "Not 2xx response remote."
                            {:remote remote-key
                             :status status
                             :url    url
                             :body   body
                             :query query
                             :TODO   "Handle HTTP errors better."}))))
        (catch :default e
          (error "Error when posting query to remote:" remote-key "error:" e)
          (throw e))))))

(defn send!
  [remote->send]
  {:pre [(map? remote->send)]}
  (fn [queries cb]
    (run! (fn [[key query]] (send-query! remote->send cb key query))
          queries)))

;; Remote helpers

(defn post-to-url [url]
  (fn [query]
    {:method      :post
     :url         url
     :opts        {:transit-params {:query query}}
     :response-fn identity}))
