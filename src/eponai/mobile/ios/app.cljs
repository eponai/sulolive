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
            [eponai.mobile.om-helper :as om-helper :refer-macros [with-om-vars]]
            [eponai.client.ui :as ui :refer-macros [opts]]
            [eponai.common.datascript :as common.datascript]
            [eponai.common.parser :as parser]
            [eponai.common.report]
            [eponai.mobile.ios.routes.ui-handlers :as ui.routes]
            [eponai.mobile.ios.linking :as linking]
            [eponai.mobile.ios.remotes :as remotes]
            [goog.object :as gobj]
            [om.next :as om :refer-macros [defui]]
            [re-natal.support :as sup]
            [taoensso.timbre :refer-macros [info debug error trace warn]]))

(set! js/React (js/require "react-native"))

(def app-registry (.-AppRegistry js/React))
(def logo-img (js/require "./images/cljs.png"))

(defonce reconciler-atom (atom nil))

(def route->transition
  {:route/login {:route/transactions js/React.Navigator.SceneConfigs.FloatFromBottom}
   :route/transactions {:route/login js/React.Navigator.SceneConfigs.FloatFromBottom}})

(defn props->route [props]
  (or (get-in props [:query/app :ui.component.app/route])
      :route/login))

(defui ^:once App
  static om/IQuery
  (query [this]
    (let [route (props->route (when (om/component? this) (om/props this)))
          static-query (get-in ui.routes/route-handler->ui-component [route :component])
          ;; Our navigator will contain the ref to the component which we'll
          ;; want to subquery.
          subquery (om-helper/subquery-in this [:nav route] static-query)
          query (cond-> [:datascript/schema
                         {:query/app [:ui.component.app/route]}]
                        (some? subquery)
                        (conj {:proxy/route-data subquery}))]
      query))
  Object
  ;; Need to pass the identical function to add/removeEventListener.
  (componentWillMount [this]
    (om/update-state! this assoc :url-handler (fn [event]
                                                (linking/load-url! this (gobj/get event "url")))))
  (componentDidMount [this]
    (.addEventListener linking/linking "url" (-> this om/get-state :url-handler)))
  (componentWillUnmount [this]
    (.removeEventListener linking/linking "url", (-> this om/get-state :url-handler)))

  (render-scene [this _ _]
    (with-om-vars
      this
      (let [props (om/props this)
            route (props->route props)
            factory (get-in ui.routes/route-handler->ui-component [route :factory])]
        (factory (-> props :proxy/route-data (assoc :ref route))))))
  (componentWillUpdate [this next-props _]
    (let [route (props->route (om/props this))
          next-route (props->route next-props)]
      (when (not= next-route route)
        (debug "Changing route to: " next-route)
        (om/update-state! this assoc
                          :route next-route
                          :prev-route route)
        (.replace (om-helper/react-ref this :nav)
                  next-route))))
  (componentDidUpdate [this prev-props _]
    (let [prev-route (props->route prev-props)
          route (props->route (om/props this))]
      (when (not= prev-route route)
        (debug "Changed route from:" prev-route " to: " route)
        (debug "nav ref: " (om-helper/get-ref-in this [:nav route]))
        (om-helper/set-runtime-query! this))))
  (render [this]
    (navigator {:ref            (str :nav)
                :renderScene    #(.render-scene this %1 %2)
                :configureScene #(get-in route->transition [(:prev-route (om/get-state this))
                                                            (:route (om/get-state this))]
                                         js/React.Navigator.SceneConfigs.FloatFromRight)})))

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
          ui-state [{:ui/component :ui.component/app}
                    {:ui/component :ui.component/loading
                     :ui.component.loading/is-logged-in? false}
                    {:ui/singleton :ui.singleton/configuration}]
          conn (d/create-conn ui-schema)]
      (d/transact! conn ui-state)
      (reset! conn-atom conn))))

(def root-node-id 1)
(defonce RootNode (sup/root-node! root-node-id))
(defonce app-root (om/factory RootNode))

(defn initialize-app [conn]
  (debug "Initializing App")
  (let [parser (parser/parser)
        _ (d/transact! conn [{:ui/singleton :ui.singleton/configuration
                              :ui.singleton.configuration.endpoints/user-api "http://localhost:3000/api/user"
                              :ui.singleton.configuration.endpoints/api "http://localhost:3000/api"
                              :ui.singleton.configuration.endpoints/verify "http://localhost:3000/verify"}])
        reconciler (om/reconciler {:state        conn
                                   :parser       parser
                                   :remotes      [:remote :http/call]
                                   :send         (backend/send! {:remote    (remotes/switching-remote conn)
                                                                 :http/call (remotes/http-call-remote)})
                                   :merge        (merge/merge! mobile.merge/mobile-merge)
                                   :root-render  sup/root-render
                                   :root-unmount sup/root-unmount
                                   :migrate      nil})]
    (reset! reconciler-atom reconciler)
    (om/add-root! reconciler App root-node-id)
    (.registerComponent app-registry "JourMoneyApp" (fn [] app-root))
    (linking/start! reconciler)))

(defn run []
  (info "Run called in: " (namespace ::foo))
  (try (initialize-app (init-conn))
       (catch :default e
         (error "Initialization error:" e)
         (throw e))))

;; For figwheel. See env/dev/ios/main.cljs
(defn reload! []
  (om/add-root! @reconciler-atom App root-node-id))
