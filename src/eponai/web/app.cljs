(ns eponai.web.app
  (:require [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [goog.dom :as gdom]
    ;; To initialize ReactDOM:
            [cljsjs.react.dom]
            [eponai.client.backend :as backend]
            [eponai.client.remotes :as remotes]
            [eponai.client.parser.merge :as merge]
            [eponai.web.parser.merge :as web.merge]
            [eponai.web.history :as history]
            [eponai.web.parser.mutate]
            [eponai.web.parser.read]
            [eponai.common.validate]
            [eponai.web.homeless :as homeless]
            [eponai.common.parser :as parser]
            [eponai.common.report]
            [eponai.web.ui.root :as root]
            [taoensso.timbre :refer-macros [info debug error trace]]
            [eponai.client.ui :refer-macros [opts]]
            [eponai.client.utils :as utils]))

(defn mount-app-with-history
  "Putting this in its own function because the order of which these
  methods are called are important, and we want to use the functionallity
  for both our app and playground."
  [reconciler]
  (let [history (history/init-history reconciler)]
    (binding [parser/*parser-allow-remote* false]
      (om/add-root! reconciler root/App (gdom/getElement "my-app")))
    (history/start! history)))

(defn initialize-app [conn & [reconciler-opts]]
  (debug "Initializing App")
  (let [parser (parser/client-parser)
        reconciler (om/reconciler (merge
                                    {:state     conn
                                     :ui->props (utils/cached-ui->props-fn parser)
                                     :parser    parser
                                     :remotes   [:remote]
                                     :send      (backend/send! utils/reconciler-atom
                                                  {:remote (-> (remotes/post-to-url homeless/om-next-endpoint-user-auth)
                                                               (remotes/read-basis-t-remote-middleware conn))})
                                     :merge     (merge/merge! web.merge/web-merge)
                                     :migrate   nil}
                                    reconciler-opts))]
    (reset! utils/reconciler-atom reconciler)
    (mount-app-with-history reconciler)))

(defn run []
  (info "Run called in: " (namespace ::foo))
  (initialize-app (utils/init-conn)))
