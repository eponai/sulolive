(ns eponai.client.run
  (:require
    [eponai.client.utils :as utils]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.parser :as parser]
    [eponai.client.parser.read]
    [eponai.client.parser.mutate]
    [eponai.client.parser.merge :as merge]
    [eponai.client.backend :as backend]
    [eponai.client.remotes :as remotes]
    [goog.dom :as gdom]
    [om.next :as om]
    [eponai.common.ui.checkout :as checkout]
    [eponai.common.ui.store :as store]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.goods :as goods]
    [eponai.common.ui.index :as index]
    [eponai.common.ui.product :as product]
    [eponai.common.ui.streams :as streams]))

(defmulti client-merge om/dispatch)

(defn run-element [{:keys [id component]}]
  (let [parser (parser/client-parser)
        reconciler-atom (atom nil)
        conn (utils/create-conn)
        init? (atom false)
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
                                                (om/add-root! @reconciler-atom component (gdom/getElement id)))
                                :query-fn     (fn [q]
                                                {:pre [(sequential? q)]}
                                                (into [:datascript/schema] q))})
        reconciler (om/reconciler {:state     conn
                                   :ui->props (utils/cached-ui->props-fn parser)
                                   :parser    parser
                                   :remotes   remotes
                                   :send      send-fn
                                   :merge     (merge/merge! client-merge)
                                   :migrate   nil})
        remote-queries (into {}
                             (map (fn [remote]
                                    [remote (parser (backend/to-env reconciler) (om/get-query component) remote)]))
                             remotes)]
    (reset! reconciler-atom reconciler)
    (debug "Remote-queries: " remote-queries)
    (send-fn remote-queries
             (fn send-cb
               ([res]
                (om/merge! reconciler res nil))
               ([res query]
                (om/merge! reconciler res query))))))

(def inline-containers
  {:navbar   {:id        "sulo-navbar-container"
              :component nav/Navbar}
   :index    {:id        "sulo-index"
              :component index/Index}
   :store    {:id        "sulo-store"
              :component store/Store}
   :checkout {:id        "sulo-checkout"
              :component checkout/Checkout}
   :goods    {:id        "sulo-items"
              :component goods/Goods}
   :product  {:id        "sulo-product-page"
              :component product/ProductPage}
   :streams  {:id        "sulo-streams"
              :component streams/Streams}})

(defn run-navbar []
  (run-element (:navbar inline-containers)))

(defn run [k]
  (prn "RUN: " k)
  ;(run-navbar)
  (run-element (get inline-containers k)))