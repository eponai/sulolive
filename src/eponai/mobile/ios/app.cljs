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
            [eponai.client.parser.merge :as merge]))

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

(defn init-conn
  "Sets up the datascript state. Caches the state so we can keep our app state between
  figwheel reloads."
  []
  (if @conn-atom
    (do
      (debug "Reusing old conn. It currently has schema for attributes:" (-> @conn-atom deref :schema keys))
      @conn-atom)
    (let [ui-state [{:ui/singleton :ui.singleton/auth}
                    ;{:ui/component           :ui.component/app
                    ; :ui.component.app/route routes/default-route}
                    {:ui/component :ui.component/root}
                    {:ui/component :ui.component/loading
                     :ui.component.loading/is-logged-in? false}
                    {:ui/singleton :ui.singleton/configuration}
                    {:ui/component :ui.component/mutation-queue}]
          conn (utils/create-conn)]
      (db/transact conn ui-state)
      (reset! conn-atom conn))))

(defn initialize-app [config conn]
  {:pre [(:server-address config)]}
  (debug "Initializing App")
  (ignore-yellow-box-warnings!)
  (let [server (:server-address config)
        parser (parser/client-parser)
        _ (db/transact conn [{:ui/singleton                                  :ui.singleton/configuration
                              :ui.singleton.configuration.endpoints/user-api (str server "/api/user")
                              :ui.singleton.configuration.endpoints/api      (str server "/api")}])
        remote (-> (remotes/switching-remote conn)
                   (remotes/read-remote-on-auth-change reconciler-atom)
                   (remotes/read-basis-t-remote-middleware conn))
        reconciler (om/reconciler {:state        conn
                                   :parser       parser
                                   :ui->props    (utils/cached-ui->props-fn parser)
                                   ;; :ui->props    (web.utils/debug-ui->props-fn parser)
                                   :remotes      [:remote :http/call]
                                   :send         (backend/send!
                                                   reconciler-atom
                                                   {:remote    remote
                                                    :http/call (remotes/http-call-remote reconciler-atom)})
                                   :merge        (merge/merge! m-merge/mobile-merge)
                                   :root-render  sup/root-render
                                   :root-unmount sup/root-unmount ;#(.unmountComponentAtNode js/ReactDOM %)
                                   :logger       nil
                                   :migrate      nil})]
    (reset! reconciler-atom reconciler)
    (om/add-root! reconciler root/Root root-node-id)
    (.registerComponent app-registry "JourMoney" (fn [] app-root))
    (debug "Done initializing app")))

(defn run [config]
  (info "Run called in: " (namespace ::foo))
  (set! js/console.disableYellowBox true)
  (debug "Disabled yellow box warnings")
  (try
    (initialize-app config (init-conn))
    (catch :default e
      (error "Initialization error:" e)
      (throw e))))

;; For figwheel. See env/dev/ios/main.cljs
(defn reload! []
  (debug "Reload!")
  (comment
    "Uncomment when we've got an ios app."
    (om/add-root! @reconciler-atom root/RootView root-node-id)))

