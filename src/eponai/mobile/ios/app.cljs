(ns eponai.mobile.ios.app
  (:require [datascript.core :as d]
            [eponai.client.backend :as backend]
            [eponai.client.parser.merge :as merge]
            [eponai.mobile.parser.merge :as mobile.merge]
            [eponai.client.parser.mutate]
            [eponai.client.parser.read]
            [eponai.mobile.parser.mutate]
            [eponai.mobile.parser.read]
            [eponai.common.datascript :as common.datascript]
            [eponai.common.parser :as parser]
            [eponai.common.report]
            [eponai.mobile.ios.linking :as linking]
            [eponai.mobile.ios.remotes :as remotes]
            [eponai.mobile.ios.ui.root :as root]
            [eponai.mobile.ios.routes :as routes]
            [eponai.mobile.om-helper :as omhelper]
            [om.next :as om :refer-macros [defui]]
            [re-natal.support :as sup]
            [taoensso.timbre :refer-macros [info debug error trace warn]]
            [eponai.web.ui.utils :as web.utils]))

(def app-registry (.-AppRegistry js/ReactNative))
(def logo-img (js/require "./images/cljs.png"))

(defonce reconciler-atom (atom nil))

(defonce conn-atom (atom nil))

(defn init-conn
  "Sets up the datascript state. Caches the state so we can keep our app state between
  figwheel reloads."
  []
  (if @conn-atom
    (do
      (debug "Reusing old conn. It currently has schema for attributes:" (-> @conn-atom deref :schema keys))
      @conn-atom)
    (let [ui-schema (common.datascript/ui-schema)
          ui-state [{:ui/singleton :ui.singleton/auth}
                    {:ui/component           :ui.component/app
                     :ui.component.app/route routes/default-route}
                    {:ui/component :ui.component/root}
                    {:ui/component :ui.component/loading
                     :ui.component.loading/is-logged-in? false}
                    {:ui/singleton :ui.singleton/configuration}]
          conn (d/create-conn ui-schema)]
      (d/transact! conn ui-state)
      (reset! conn-atom conn))))

(def root-node-id 1)
(defonce RootNode (sup/root-node! root-node-id))
(defonce app-root (om/factory RootNode))

(defn ignore-yellow-box-warnings! []
  (set! (.-ignoredYellowBox js/console) #js [
                                             ;; TODO: This should be fixed in react-native 0.23?
                                             "Warning: Failed propType"
                                             ]))

(defn initialize-app [config conn]
  {:pre [(:server-address config)]}
  (debug "Initializing App")
  (ignore-yellow-box-warnings!)
  (let [server (:server-address config)
        parser (parser/parser)
        _ (d/transact! conn [{:ui/singleton                                  :ui.singleton/configuration
                              :ui.singleton.configuration.endpoints/user-api (str server "/api/user")
                              :ui.singleton.configuration.endpoints/api      (str server "/api")
                              :ui.singleton.configuration.endpoints/verify   (str server "/verify")}])
        reconciler (om/reconciler {:state        conn
                                   :parser       parser
                                    :ui->props    (web.utils/cached-ui->props-fn parser)
                                   ;; :ui->props    (web.utils/debug-ui->props-fn parser)
                                   :remotes      [:remote :http/call]
                                   :send         (backend/send! {:remote    (remotes/switching-remote conn)
                                                                 :http/call (remotes/http-call-remote reconciler-atom)})
                                   :merge        (merge/merge! mobile.merge/mobile-merge)
                                   :root-render  sup/root-render
                                   :root-unmount sup/root-unmount
                                   :logger       nil
                                   :migrate      nil})]
    (reset! reconciler-atom reconciler)
    (om/add-root! reconciler root/RootView root-node-id)
    (.registerComponent app-registry "JourMoneyApp" (fn [] app-root))
    (linking/start! reconciler)))

(defn run [config]
  (info "Run called in: " (namespace ::foo))
  (set! js/console.disableYellowBox true)
  (debug "Disabled yellow box warnings")
  (try (initialize-app config (init-conn))
       (catch :default e
         (error "Initialization error:" e)
         (throw e))))

;; For figwheel. See env/dev/ios/main.cljs
(defn reload! []
  (debug "Reload!")
  (om/add-root! @reconciler-atom root/RootView root-node-id))
