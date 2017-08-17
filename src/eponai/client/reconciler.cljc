(ns eponai.client.reconciler
  (:require [om.next :as om]
            [eponai.client.parser.merge :as merge]
            [eponai.client.remotes :as remotes]

            [eponai.client.utils :as utils]
            [eponai.client.local-storage :as local-storage]
            [eponai.common.ui.router :as router]))

(defn remote-order []
  [:remote :remote/chat])

(defn remote-config
  "Returns a map with remotes (keywords) mapped to remote functions.
  A remote function is a 1-arity function taking a query and returning
  a map describing a request to the remote."
  [conn]
  {:order       (remote-order)
   :remote      (-> (remotes/post-to-url "/api")
                    (remotes/read-basis-t-remote-middleware conn)
                    (remotes/with-auth conn)
                    (remotes/with-route conn)
                    (remotes/with-random-seed))
   :remote/chat (-> (remotes/post-to-url "/api/chat")
                    (remotes/read-basis-t-remote-middleware conn)
                    (remotes/with-auth conn)
                    (remotes/with-route conn)
                    (remotes/with-random-seed))})

;; TODO: Use this from run.cljs and from fullstack tests.

(defn create [{:keys [parser
                      send-fn
                      ;; Optional
                      route
                      route-params
                      query-params
                      instrument
                      tx-listen
                      ;; With defaults:
                      conn
                      remotes
                      merge]
               :or   {conn    (utils/create-conn)
                      remotes (remote-order)
                      merge   (merge/merge!)}
               :as   reconciler-config}]
  (let [shared-defaults {:shared/local-storage (local-storage/->local-storage)}
        shared-map (into shared-defaults
                         (comp (filter (comp #{"shared"} namespace key)))
                         reconciler-config)
        overwrites (select-keys reconciler-config [:ui->props :history :logger :root-render :root-unmount])
        reconciler (om/reconciler (into overwrites
                                        {:state      conn
                                         :parser     parser
                                         :remotes    remotes
                                         :send       send-fn
                                         :merge      merge
                                         :shared     shared-map
                                         :tx-listen  tx-listen
                                         :migrate    nil
                                         :instrument instrument}))]
    (when (some? route)
      (router/transact-route! reconciler route {:route-params route-params
                                                :query-params query-params
                                                :queue?       false}))
    reconciler))