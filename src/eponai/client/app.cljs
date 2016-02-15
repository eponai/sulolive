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
            [eponai.client.report]
            [eponai.client.ui.add_transaction :refer [AddTransaction ->AddTransaction]]
            [eponai.client.ui.all_transactions :refer [AllTransactions ->AllTransactions]]
            [eponai.client.ui.modal :refer [Modal ->Modal]]
            [eponai.client.ui.stripe :refer [->Payment Payment]]
            [eponai.client.ui.header :refer [Header ->Header]]
            [taoensso.timbre :as timbre :refer-macros [info debug error trace]]
            [eponai.client.ui :refer-macros [opts]]
            ))

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
     {:proxy/header (om/get-query Header)}
     {:query/loader [:ui.singleton.loader/visible]}
     {:proxy/modal (om/get-query Modal)}
     '{:proxy/app-content ?url/query}
     '(:return/content-factory ?url/factory)])
  Object
  (render
    [this]
    (let [{:keys [proxy/header
                  proxy/app-content
                  proxy/modal
                  return/content-factory
                  query/loader]} (om/props this)]
      (debug ":proxy/app-content: " app-content)
      (html [:div
             [:div (->Header header)]
             [:div (->Modal modal)]
             (when (:ui.singleton.loader/visible loader)
               (prn "Render loader")
               [:div.loader-circle-black
                (opts {:style {:top      "50%"
                               :left     "50%"
                               :position "fixed"
                               :z-index  1050}})])
             [:div {:class "content-section-b"}
              (when content-factory
                (content-factory app-content))]]))))

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
          ui-state [{:ui/singleton :ui.singleton/app}

                    {:ui/singleton :ui.singleton/modal
                     :ui.singleton.modal/visible false}

                    {:ui.singleton :ui.singleton/menu
                     :ui.singleton.menu/visible false}

                    {:ui/singleton :ui.singleton/loader
                     :ui.singleton.loader/visible false}

                    {:ui/component :ui.component/dashboard}]
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
