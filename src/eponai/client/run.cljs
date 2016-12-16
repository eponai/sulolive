(ns eponai.client.run
  (:require
    [eponai.client.utils :as utils]
    [eponai.common.parser :as parser]
    [eponai.client.parser.read]
    [eponai.client.parser.mutate]
    [eponai.client.parser.merge :as merge]
    [eponai.client.backend :as backend]
    [eponai.client.remotes :as remotes]
    [goog.dom :as gdom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    ;; Routing
    [bidi.bidi :as bidi]
    [pushy.core :as pushy]
    [eponai.client.routes :as routes]
    [eponai.common.routes :as common.routes]
    [eponai.common.ui.router :as router]))

(defn update-route-fn [reconciler-atom]
  (fn [{:keys [handler route-params]}]
    (let [r @reconciler-atom]
      (routes/set-route! r handler {:route-params route-params
                                    :queue?       (some? (om/app-root r))}))))

(defn apply-once [f]
  (let [appliced? (atom false)]
    (fn [x]
      (if @appliced?
        x
        (do (reset! appliced? true)
            (f x))))))

(defonce history-atom (atom nil))

(defn run-element [{:keys [id] :as routed}]
  (let [init? (atom false)
        reconciler-atom (atom nil)
        _ (when-let [h @history-atom]
            (pushy/stop! h))
        match-route (partial bidi/match-route common.routes/routes)
        history (pushy/pushy (update-route-fn reconciler-atom) match-route)
        current-route-fn #(:handler (match-route (pushy/get-token history)))
        parser (parser/client-parser (parser/client-parser-state
                                       {::parser/get-route-params #(:route-params (current-route-fn))}))
        conn (utils/create-conn)
        remotes [:remote :remote/user]
        send-fn (backend/send! reconciler-atom
                               {:remote      (-> (remotes/post-to-url "/api")
                                                 (remotes/read-basis-t-remote-middleware conn))
                                :remote/user (-> (remotes/post-to-url "/api/user")
                                                 (remotes/read-basis-t-remote-middleware conn)
                                                 (remotes/wrap-auth))}
                               {:did-merge-fn #(when-not @init?
                                                (reset! init? true)
                                                (debug "First merge happened. Adding reconciler to root.")
                                                (binding [parser/*parser-allow-remote* false]
                                                  (om/add-root! @reconciler-atom router/Router (gdom/getElement router/dom-app-id))))
                                :query-fn     (apply-once (fn [q]
                                                            {:pre [(sequential? q)]}
                                                            (into [:datascript/schema] q)))})
        reconciler (om/reconciler {:state     conn
                                   :ui->props (utils/cached-ui->props-fn parser)
                                   :parser    parser
                                   :remotes   remotes
                                   :send      send-fn
                                   :merge     (merge/merge!)
                                   :migrate   nil})]
    (reset! history-atom history)
    (reset! reconciler-atom reconciler)
    (binding [parser/*parser-allow-remote* false]
      (pushy/start! history))
    (utils/init-state! reconciler remotes send-fn parser router/Router)))

(defn run [k]
  (prn "RUN: " k)
  (run-element (common.routes/route->component k)))