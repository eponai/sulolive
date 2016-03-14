(ns eponai.mobile.ios.app
  (:require-macros [natal-shell.components :refer [view text image touchable-highlight navigator]]
                   [natal-shell.alert :refer [alert]])
  (:require [datascript.core :as d]
            [eponai.client.backend :as backend]
            [eponai.client.parser.merge :as merge]
            [eponai.mobile.parser.merge :as mobile.merge]
            [eponai.client.parser.mutate]
            [eponai.client.parser.read]
            [eponai.mobile.parser.mutate]
            [eponai.mobile.parser.read]
            [eponai.client.ui :as ui :refer-macros [opts]]
            [eponai.common.datascript :as common.datascript]
            [eponai.common.parser :as parser]
            [eponai.common.report]
            [eponai.mobile.ios.ui.transactions :refer [Transactions ->Transactions]]
            [eponai.mobile.ios.ui.landing :as landing]
            [goog.object :as obj]
            [om.next :as om :refer-macros [defui]]
            [re-natal.support :as sup]
            [taoensso.timbre :refer-macros [info debug error trace]]))

(set! js/React (js/require "react-native"))

(def app-registry (.-AppRegistry js/React))
(def logo-img (js/require "./images/cljs.png"))

(comment (view (opts {:style {:flex-direction "column" :margin 40 :align-items "center"}})
               (image {:source logo-img
                       :style  {:width 80 :height 80 :marginBottom 30}})
               ))

(def app-route->transition
  {:loading {:login js/React.Navigator.SceneConfigs.FloatFromRight
             :app   js/React.Navigator.SceneConfigs.FloatFromRight}
   :login   {:signup js/React.Navigator.SceneConfigs.FloatFromRight
             :app    js/React.Navigator.SceneConfigs.FloatFromRight}
   :signup  {:login js/React.Navigator.SceneConfigs.FloatFromRight
             :app   js/React.Navigator.SceneConfigs.FloatFromRight}
   :app     {:login js/React.Navigator.SceneConfigs.FloatFromRight}})

(def app-route->ui
  {:loading {:class landing/Loading
             :factory landing/->Loading}
   :login  {:class landing/Login
            :factory   landing/->Login}
   ;; :signup #(text {} "signup")
   :app    {:class Transactions
            :factory ->Transactions}})

(defn props->route [props]
  (or (get-in props [:query/app :ui.component.app/route])
      :loading))

(defui App
  static om/IQuery
  (query [this]
    (let [subq-ref (props->route (when (om/component? this) (om/props this)))
          subq-class (get-in app-route->ui [subq-ref :class])
          query (cond-> [:datascript/schema
                         {:query/app [:ui.component.app/route]}]
                        ;; When there's a query, add the route-data key.
                        (and (om/iquery? subq-class) (om/get-query subq-class))
                        (conj {:proxy/route-data
                               (om/subquery this subq-ref subq-class)}))]
      query))
  Object
  (initLocalState [this]
    (letfn [(render-scene [_ _]
              (let [props (om/props this)
                    {:keys [proxy/route-data]} props
                    route (props->route props)
                    factory (get-in app-route->ui [route :factory])]
                (factory (assoc route-data :ref :app/route))))]
      {:navigator (navigator {:renderScene render-scene})}))
  (componentWillUpdate [this next-props _]
    (let [next-route (props->route next-props)]
      (when (not= next-route (props->route (om/props this)))
        (.replace (:navigator (om/get-state this))
                  next-route))))
  (render [this]
    (let [{:keys [navigator]} (om/get-state this)]
      navigator)))


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
          ui-state [{:ui/component :ui.component/app}]
          conn (d/create-conn ui-schema)]
      (d/transact! conn ui-state)
      (reset! conn-atom conn))))

(def root-node-id 1)
(defonce RootNode (sup/root-node! root-node-id))
(defonce app-root (om/factory RootNode))

(defonce reconciler-atom (atom nil))

(defn initialize-app [conn]
  (debug "Initializing App")
  (let [parser (parser/parser)
        reconciler (om/reconciler {:state        conn
                                   :parser       parser
                                   :remotes      [:remote]
                                   :send         (backend/send! {:remote "http://localhost:3000/api/user/"})
                                   :merge        (merge/merge! mobile.merge/mobile-merge)
                                   :root-render  sup/root-render
                                   :root-unmount sup/root-unmount
                                   })]
    (reset! reconciler-atom reconciler)
    (om/add-root! reconciler App root-node-id)
    (.registerComponent app-registry "JourMoneyApp" (fn [] app-root))))

(defn run []
  (info "Run called in: " (namespace ::foo))
  (try (initialize-app (init-conn))
       (catch :default e
         (error "Initialization error:" e)
         (throw e))))

;; For figwheel. See env/dev/ios/main.cljs
(defn reload! []
  (om/add-root! @reconciler-atom App root-node-id))
