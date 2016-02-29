(ns eponai.client.app
  (:require [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [goog.dom :as gdom]
    ;; To initialize ReactDOM:
            [cljsjs.react.dom]
            [datascript.core :as d]
            [eponai.client.backend :as backend]
            [eponai.common.parser :as parser]
            [eponai.client.history :as history]
            [eponai.client.parser.mutate]
            [eponai.client.parser.read]
            [eponai.common.report]
            [eponai.client.ui.navbar :as navbar]
            [taoensso.timbre :refer-macros [info debug error trace]]
            [eponai.client.ui :refer-macros [opts]]))

(defonce reconciler-atom (atom nil))

(defui ^:once App
  static om/IQueryParams
  (params [_]
    (let [{:keys [url/component url/factory]}
          (history/url-query-params (history/url-handler-form-token))
          ;; HACK: Gets the component from the reconciler if there is one.
          ;;       This only works if there's every only going to be a
          ;;       single instance of the component.
          query (om/get-query (or (when-let [r @reconciler-atom]
                                    (om/class->any r component))
                                  component))]
      {:url/component component
       :url/query query
       :url/factory factory}))
  static om/IQuery
  (query [_]
    [:datascript/schema
     {:proxy/nav-bar (navbar/navbar-query)}
     {:proxy/side-bar (navbar/sidebar-query)}
     '{:proxy/app-content ?url/query}
     '(:return/content-factory ?url/factory)])
  Object
  (initLocalState [_]
    {:sidebar-visible? true})
  (render
    [this]
    (let [{:keys [proxy/app-content
                  return/content-factory
                  proxy/nav-bar
                  proxy/side-bar]} (om/props this)
          {:keys [sidebar-visible?]} (om/get-state this)]
      (html
        [:div#wrapper
         (when-not sidebar-visible?
           {:class "toggled"})
         (navbar/sidebar-create side-bar)

         [:div#page-content-wrapper
          (opts {:style {:height "100%"}})

          (navbar/navbar-create nav-bar {:on-sidebar-toggle #(om/update-state! this update :sidebar-visible? not)
                                         :sidebar-visible? sidebar-visible?})

          [:div
           (opts {:class "container-fluid content-section-b"
                  :style {:border "1px solid transparent"}})
           (when content-factory
             (content-factory app-content))]]]))))

(defonce conn-atom (atom nil))

(defn init-conn
  "Sets up the datascript state. Caches the state so we can keep our app state between
  figwheel reloads."
  []
  (if @conn-atom
    (do
      (debug "Reusing old conn. It currently has schema for attributes:" (-> @conn-atom deref :schema keys))
      @conn-atom)
    (let [ui-schema {:ui/singleton {:db/unique :db.unique/identity}
                     :ui/component {:db/unique :db.unique/identity}}
          ui-state [{:ui/singleton :ui.singleton/app}]
          conn (d/create-conn ui-schema)]
      (d/transact! conn ui-state)
      (reset! conn-atom conn))))

(defn initialize-app [conn]
  (debug "Initializing App")
  (let [parser (parser/parser)
        reconciler (om/reconciler {:state   conn
                                   :parser  parser
                                   :remotes [:remote]
                                   :send    (backend/send! "/user/")
                                   :merge   (backend/merge! conn)})
        history (history/init-history reconciler)]
    (reset! reconciler-atom reconciler)
    (om/add-root! reconciler App (gdom/getElement "my-app"))
    (history/start! history)))

(defn run []
  (info "Run called in: " (namespace ::foo))
  (initialize-app (init-conn)))
