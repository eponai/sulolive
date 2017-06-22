(ns eponai.client.reconciler
  (:require [om.next :as om]
            [eponai.client.parser.merge :as merge]
            [eponai.client.remotes :as remotes]
            [eponai.client.routes :as routes]
            [eponai.client.utils :as utils]
            [eponai.client.local-storage :as local-storage]))

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
                    (remotes/with-route conn))
   :remote/chat (-> (remotes/post-to-url "/api/chat")
                    (remotes/read-basis-t-remote-middleware conn)
                    (remotes/with-auth conn)
                    (remotes/with-route conn))})

;; TODO: Use this from run.cljs and from fullstack tests.

(defn create [{:keys [parser
                      ui->props
                      send-fn
                      ;; Optional
                      history
                      route
                      route-params
                      query-params
                      instrument
                      tx-listen
                      ;; With defaults:
                      conn
                      remotes
                      merge]
               :shared/keys [scroll-helper
                             loading-bar
                             browser-history
                             auth-lock
                             local-storage
                             store-chat-listener
                             modules
                             stripe]
               :or   {conn          (utils/create-conn)
                      local-storage (local-storage/->local-storage)
                      remotes       (remote-order)
                      merge         (merge/merge!)}
               :as reconciler-config}]
  (let [overwrites (select-keys reconciler-config [:ui->props :history :logger :root-render :root-unmount])
        reconciler (om/reconciler (into overwrites
                                        {:state      conn
                                         :parser     parser
                                         :remotes    remotes
                                         :send       send-fn
                                         :merge      merge
                                         :shared     {:shared/stripe              stripe
                                                      :shared/scroll-helper       scroll-helper
                                                      :shared/loading-bar         loading-bar
                                                      :shared/modules             modules
                                                      :shared/browser-history     browser-history
                                                      :shared/local-storage       local-storage
                                                      :shared/auth-lock           auth-lock
                                                      :shared/store-chat-listener store-chat-listener}
                                         :tx-listen  tx-listen
                                         :migrate    nil
                                         :instrument instrument}))]
    (when (some? route)
      (routes/transact-route! reconciler route {:route-params route-params
                                                :query-params query-params
                                                :queue?       false}))
    reconciler))