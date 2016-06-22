(ns eponai.web.app
  (:require [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [goog.dom :as gdom]
    ;; To initialize ReactDOM:
            [cljsjs.react.dom]
            [datascript.core :as d]
            [goog.log :as glog]
            [eponai.client.backend :as backend]
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
            [eponai.web.ui.utils :as utils]))

(defn mount-app-with-history
  "Putting this in its own function because the order of which these
  methods are called are important, and we want to use the functionallity
  for both our app and playground."
  [reconciler]
  (let [history (history/init-history reconciler)]
    (binding [parser/*parser-allow-remote* false]
      (om/add-root! reconciler root/App (gdom/getElement "my-app"))
      (history/start! history))
    (binding [parser/*parser-allow-local-read* false]
      (om/transact! reconciler (om/full-query (om/app-root reconciler))))))

(defn ui->props-fn [parser]
  (fn [env c]
    {:pre [(map? env) (om/component? c)]}
    (let [fq (om/full-query c)]
      (when-not (nil? fq)
        (let [s (system-time)
              ;; Assoc's :component in env so we can use parser/component-parser.
              ui (parser (assoc env :component c) fq)
              e (system-time)]
          (when-let [l (:logger env)]
            (let [dt (- e s)]
              (when (< 16 dt)
                (glog/warning l (str (pr-str c) " query took " dt " msecs")))))
          (get-in ui (om/path c)))))))

(defn initialize-app [conn & [reconciler-opts]]
  (debug "Initializing App")
  (let [parser (parser/parser)
        reconciler (om/reconciler (merge
                                    {:state     conn
                                     :ui->props (ui->props-fn (parser/component-parser parser))
                                     :parser    parser
                                     :remotes   [:remote]
                                     :send      (backend/send! {:remote (-> (backend/post-to-url homeless/om-next-endpoint-user-auth)
                                                                            (utils/read-basis-t-remote-middleware conn))})
                                     :merge     (merge/merge! web.merge/web-merge)
                                     :migrate   nil}
                                    reconciler-opts))]
    (reset! utils/reconciler-atom reconciler)
    (mount-app-with-history reconciler)))

(defn run []
  (info "Run called in: " (namespace ::foo))
  (initialize-app (utils/init-conn)))
