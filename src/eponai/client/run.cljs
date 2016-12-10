(ns eponai.client.run
  (:require
    [eponai.client.utils :as utils]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.parser :as parser]
    [eponai.client.parser.read]
    [eponai.client.parser.merge :as merge]
    [eponai.client.backend :as backend]
    [eponai.client.remotes :as remotes]
    [goog.dom :as gdom]
    [om.next :as om]
    [eponai.common.ui.checkout :as checkout]
    [eponai.common.ui.store :as store]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.goods :as goods]))

(defmulti client-merge om/dispatch)

(defn run-element [{:keys [id component]}]
  (let [parser (parser/client-parser)
        reconciler-atom (atom nil)
        conn (utils/create-conn)
        init? (atom false)
        send-fn (backend/send! reconciler-atom
                               {:remote (-> (remotes/post-to-url "/api")
                                            (remotes/read-basis-t-remote-middleware conn)
                                            (remotes/wrap-auth))}
                               {:did-merge-fn #(when-not @init?
                                                 (reset! init? true)
                                                 (debug "First merge happened. Adding reconciler to root.")
                                                 (om/add-root! @reconciler-atom component (gdom/getElement id)))
                                :query-fn      (fn [q]
                                                 {:pre [(sequential? q)]}
                                                 (into [:datascript/schema] q))})
        reconciler (om/reconciler {:state     conn
                                   :ui->props (utils/cached-ui->props-fn parser)
                                   :parser    parser
                                   :remotes   [:remote]
                                   :send      send-fn
                                   :merge     (merge/merge! client-merge)
                                   :migrate   nil})
        remote-query (parser (backend/to-env reconciler)
                             (om/get-query component)
                             :remote)]
    (reset! reconciler-atom reconciler)
    (debug "Remote-query: " remote-query)
    (send-fn {:remote remote-query}
             (fn send-cb
               ([res]
                (om/merge! reconciler res nil))
               ([res query]
                (om/merge! reconciler res query))))))

(def inline-containers
  {:navbar   {:id        "sulo-navbar"
              :component nav/Navbar}
   :store    {:id        "sulo-store-container"
              :component store/Store}
   :checkout {:id        "sulo-checkout-container"
              :component checkout/Checkout}
   :goods    {:id        "sulo-items-container"
              :component goods/Goods}})

(defn run [k]
  (prn "RUN: " k)
  (run-element (:navbar inline-containers))
  (run-element (get inline-containers k)))