(ns eponai.client.reconciler
  (:require [om.next :as om]
            [eponai.client.parser.merge :as merge]
            [eponai.common.parser :as parser]
            [eponai.client.remotes :as remotes]
            [eponai.client.routes :as routes]
            [eponai.client.backend :as backend]
            [eponai.client.auth :as auth]
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
                    (remotes/with-auth conn))
   :remote/chat (-> (remotes/post-to-url "/api/chat")
                    (remotes/read-basis-t-remote-middleware conn)
                    (remotes/with-auth conn))})

;; TODO: Use this from run.cljs and from fullstack tests.

(defn create [{:keys [parser
                      ui->props
                      send-fn
                      ;; Optional
                      history
                      route
                      route-params
                      instrument
                      tx-listen
                      ;; With defaults:
                      conn
                      remotes
                      merge]
               :shared/keys [loading-bar
                             wowza-player
                             browser-history
                             auth-lock
                             local-storage
                             store-chat-listener
                             modules]
               :or   {conn          (utils/create-conn)
                      local-storage (local-storage/->local-storage)
                      remotes       (remote-order)
                      merge         (merge/merge!)}
               :as reconciler-config}]
  (let [overwrites (select-keys reconciler-config [:logger :root-render :root-unmount])
        reconciler (om/reconciler (into overwrites
                                        {:state      conn
                                         :ui->props  ui->props
                                         :parser     parser
                                         :remotes    remotes
                                         :send       send-fn
                                         :merge      merge
                                         :shared     {:shared/loading-bar         loading-bar
                                                      :shared/wowza-player        wowza-player
                                                      :shared/modules             modules
                                                      :shared/browser-history     browser-history
                                                      :shared/local-storage       local-storage
                                                      :shared/auth-lock           auth-lock
                                                      :shared/store-chat-listener store-chat-listener}
                                         :tx-listen  tx-listen
                                         :history    history
                                         :migrate    nil
                                         :instrument instrument}))]
    (when (some? route)
      (routes/transact-route! reconciler route {:route-params route-params
                                                :queue?       false}))
    reconciler))