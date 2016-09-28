(ns eponai.fullstack.jvmclient
  (:require [om.next :as om]
            [om.dom :as dom]
            [eponai.common.parser :as parser]
            [eponai.client.parser.mutate]
            [eponai.client.parser.read]
            [eponai.client.backend :as backend]
            [eponai.client.remotes :as remotes]
            [eponai.client.utils :as utils]
            [eponai.client.parser.merge :as merge]
            [eponai.server.core :as core]
            [clj-http.client :as http]
            [clojure.core.async :as async]
            [taoensso.timbre :refer [debug]]))

(om/defui JvmRoot
  static om/IQuery
  (query [this] [:datascript/schema {:query/transactions [:transaction/title]}])
  Object
  (render [this]
    (dom/div nil
      (dom/p nil "First 10 transaction titles:")
      (apply dom/div nil (map #(dom/p nil (:transaction/title %))
                              (:query/transactions (om/props this)))))))

(defmulti jvm-client-merge om/dispatch)

(defn run-with-server [server]
  {:pre [(.isRunning server)]}
  (let [server-url (str "http://localhost:" (.getPort (.getURI server)) "/api")
        conn (utils/create-conn)
        reconciler-atom (atom nil)
        reconciler (om/reconciler {:root-render (fn [c target] (prn "Rendering: " (dom/render-to-str c)))
                                   :state       conn
                                   :parser      (parser/client-parser)
                                   :send        (backend/send! reconciler-atom
                                                               {:remote (-> (remotes/post-to-url server-url)
                                                                            (remotes/read-basis-t-remote-middleware conn))})
                                   :merge       (merge/merge! jvm-client-merge)
                                   :migrate     nil})
        _ (reset! reconciler-atom reconciler)
        _ (om/add-root! reconciler JvmRoot nil)
        c ((om/factory JvmRoot))
        html-string (dom/render-to-str c)]
    (prn "Html string: " html-string)))

(defn run []
  (let [server (core/start-server-for-tests)]
    (run-with-server server)))
