(ns eponai.client.backend
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<! >! chan]]
            [eponai.common.parser.util :as parser.util]
            [eponai.web.homeless :as homeless]
            [cljs-http.client :as http]
            [taoensso.timbre :refer-macros [debug error trace]]))

(defn- post [url opts]
  (let [transit-opts {:transit-opts
                      {:decoding-opts
                       {:handlers {"n" cljs.reader/read-string
                                   "f" cljs.reader/read-string}}}}]
    (http/post url (merge opts transit-opts))))

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
