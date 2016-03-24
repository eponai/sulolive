(ns eponai.client.backend
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<! >! chan]]
            [eponai.common.parser.util :as parser.util]
            [cljs-http.client :as http]
            [taoensso.timbre :refer-macros [debug error trace]]))

(defn- send [send-fn url opts]
  (let [transit-opts {:transit-opts
                      {:decoding-opts
                       {:handlers {"n" cljs.reader/read-string
                                   "f" cljs.reader/read-string}}}}]
    (send-fn url (merge opts transit-opts))))

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
              (cb body))

            :else
            (throw (ex-info "Not 2xx response remote."
                            {:remote remote-key
                             :status status
                             :url    url
                             :body   body
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
     :opts        {:transit-params query}
     :response-fn identity}))
