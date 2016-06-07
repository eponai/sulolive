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
            [eponai.web.ui.utils :as utils]
            [clojure.data :as diff]))

(defn mount-app-with-history
  "Putting this in its own function because the order of which these
  methods are called are important, and we want to use the functionallity
  for both our app and playground."
  [reconciler]
  (let [history (history/init-history reconciler)]
    (binding [parser/*parser-allow-remote* false]
      (om/add-root! reconciler root/App (gdom/getElement "my-app")))
    (history/start! history)))

(defn- traverse-query [query [p :as path]]
  (cond
    (or (empty? path) (= p ::root))
    query

    (number? p)
    (recur query (rest path))

    (map? query)
    (when (contains? query p)
      (recur (get query p) (rest path)))

    (sequential? query)
    (some #(traverse-query % path) query)

    :else
    (do (debug "not traversing query: " query)
        nil)))

(def traverse-query-memoized (memoize traverse-query))

(defn path->paths [path]
  {:pre [(vector? path)]}
  (when (seq path)
    (reduce conj [path] (path->paths (subvec path 0 (dec (count path)))))))

(defn- find-cached-props [cache c-path c-query]
  (let [find-props (fn [{:keys [query props]}]
                     (let [t-query (traverse-query-memoized query c-path)
                           ct-query (traverse-query-memoized c-query c-path)]
                       (when (= ct-query t-query)
                         (let [c-props (get-in props c-path)]
                           (when (some? c-props)
                             (debug "found cached props for c-path: " c-path " c-props: " c-props))
                           c-props))))
        ret (->> (butlast c-path)
                 (vec)
                 (path->paths)
                 (cons [::root])
                 (map #(get-in cache %))
                 (some find-props))]
    ret))

(defn component-parser [parser]
  (let [cache (atom {})]
    (fn [env query component]
      {:pre [(om/component? component)]}
     (let [path (om/path component)
           db (d/db (:state env))
           cache-db (::db @cache)]
       (when (not= db cache-db)
         (reset! cache {::db db}))
       (if-let [c-props (find-cached-props @cache path query)]
         c-props
         (let [props (parser env query)]
           (swap! cache assoc-in
                  (or (seq path) [::root])
                  {:query query
                   :props props})
           props))))))

(defn ui->props-fn [parser]
  (fn [env c]
    {:pre [(map? env) (om/component? c)]}
    (let [fq (om/full-query c)]
      (when-not (nil? fq)
        (let [s (system-time)
              ui (parser env fq c)
              e (system-time)]
          (when-let [l (:logger env)]
            (let [dt (- e s)]
              (when (< 16 dt)
                (glog/warning l (str (pr-str c) " query took " dt " msecs")))))
          (get-in ui (om/path c)))))))

(defn initialize-app [conn & [reconciler-opts]]
  (debug "Initializing App")
  (let [parser (parser/parser)
        component-parser (component-parser parser)
        reconciler (om/reconciler (merge
                                    {:state     conn
                                     :ui->props (ui->props-fn component-parser)
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
