(ns eponai.client.run
  (:require
    [eponai.client.utils :as utils]
    [eponai.common.parser :as parser]
    [goog.dom :as gdom]
    [om.next :as om]
    [eponai.web.ui.stream :refer [VideoApp]]
    [taoensso.timbre :refer [debug]]))

(defn store []
  (debug "Run store stream")
  (let [conn (utils/init-conn)
        reconciler (om/reconciler {:state   conn
                                   :parser  (parser/client-parser)
                                   :remotes []
                                   :migrate nil})]
    (reset! utils/reconciler-atom reconciler)
    (om/add-root! reconciler VideoApp (gdom/getElement "stream-container"))))