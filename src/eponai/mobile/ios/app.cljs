(ns eponai.mobile.ios.app
  (:require [eponai.client.utils :as utils]
            [eponai.mobile.parser.merge :as m-merge]
            [om.next :as om :refer [defui ui]]
            [eponai.mobile.ios.ui.root :as root]
            [re-natal.support :as sup]
            [eponai.common.database :as db]
            [taoensso.timbre :refer [info debug error trace warn]]
            [eponai.common.parser :as parser]
            [eponai.client.remotes :as remotes]
            [eponai.client.backend :as backend]
            [eponai.client.parser.merge :as merge]
            [eponai.client.reconciler :as reconciler]
            [eponai.web.chat :as web.chat]))

(def app-registry (.-AppRegistry js/ReactNative))
(def logo-img (js/require "./images/cljs.png"))

(defonce reconciler-atom (atom nil))

(defonce conn-atom (atom nil))

(def root-node-id 1)
(defonce RootNode (sup/root-node! root-node-id))
(defonce app-root (om/factory RootNode))

(defn ignore-yellow-box-warnings! []
  (set! (.-ignoredYellowBox js/console) #js [
                                             ;; TODO: This should be fixed in react-native 0.23?
                                             "Warning: Failed propType"
                                             ]))

(defn prepend-server-url [remote-config server-url]
  (reduce-kv (fn [remotes remote-key]
               (update remotes remote-key remotes/update-key :url #(str server-url %)))
             remote-config
             (:order remote-config)))

(defn initialize-app [config]
  {:pre [(:server-address config)]}
  (debug "Initializing App")
  ;; (ignore-yellow-box-warnings!)
  (let [conn (utils/create-conn)
        parser (parser/client-parser)
        remote-config (-> (reconciler/remote-config conn)
                          (prepend-server-url (:server-address config)))

        ;; Use reconciler/create instead of this vvvvvvvvvv
        reconciler (reconciler/create {:conn                       conn
                                       :parser                     parser
                                       :ui->props                  (utils/cached-ui->props-fn parser)
                                       :merge                      (merge/merge! m-merge/mobile-merge)
                                       :send-fn                    (backend/send!
                                                                     reconciler-atom
                                                                     remote-config)
                                       :remotes                    (:order remote-config)
                                       ;; We can use websockets in react-native?!
                                       :shared/store-chat-listener (web.chat/store-chat-listener reconciler-atom)
                                       :shared/local-storage       nil
                                       :logger                     nil
                                       :migrate                    nil
                                       :root-render                sup/root-render
                                       :root-unmount               sup/root-unmount
                                       })]
    (reset! reconciler-atom reconciler)
    (om/add-root! reconciler root/Root root-node-id)
    (.registerComponent app-registry "SuloLive" (fn [] app-root))
    (debug "Done initializing app")))

(defn run [config]
  (info "Run called in: " (namespace ::foo))
  ;; (set! js/console.disableYellowBox true)
  ;; (debug "Disabled yellow box warnings")
  (try
    (initialize-app config)
    (catch :default e
      (error "Initialization error:" e)
      (throw e))))

;; For figwheel. See env/dev/ios/main.cljs
(defn reload! []
  (debug "Reload!")
  (om/add-root! @reconciler-atom root/Root root-node-id))
