(ns eponai.client.backend
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<! >! chan]]
            [eponai.common.parser.util :as parser.util]
            [cljs-http.client :as http]
            [taoensso.timbre :refer-macros [debug error trace]]))

(defn- post [url opts]
  (let [transit-opts {:transit-opts
                      {:decoding-opts
                       {:handlers {"n" cljs.reader/read-string
                                   "f" cljs.reader/read-string}}}}]
    (http/post url (merge opts transit-opts))))

(defn- send-query! [route->url cb remote-key query]
  (let [query (parser.util/unwrap-proxies query)]
    (debug "Sending to " remote-key " query: " query)
    (go
      (try
        (let [url (get route->url remote-key)
              {:keys [body status headers]} (<! (post url {:transit-params query}))]
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
          (trace "Error when posting query to remote:" remote-key "error:" e)
          (throw e))))))

(defn send!
  [route->url]
  {:pre [(map? route->url)]}
  (fn [queries cb]
    (run! (fn [[key query]] (send-query! route->url cb key query))
          queries)))
