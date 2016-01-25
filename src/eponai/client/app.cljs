(ns eponai.client.app
  (:require [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [goog.dom :as gdom]
    ;; To initialize ReactDOM:
            [cljsjs.react.dom]
            [datascript.core :as d]
            [eponai.client.backend :as backend]
            [eponai.common.parser :as parser]
            [eponai.client.parser.mutate]
            [eponai.client.parser.read]
            [eponai.client.ui.add_transaction :refer [AddTransaction ->AddTransaction]]
            [eponai.client.ui.all_transactions :refer [AllTransactions ->AllTransactions]]
            [eponai.client.ui.modal :refer [Modal ->Modal]]
            [eponai.client.ui.stripe :refer [->Payment Payment]]
            [eponai.client.ui.header :refer [Header ->Header]]
            [taoensso.timbre :as timbre :refer-macros [info debug error trace]]))

(defui App
  static om/IQuery
  (query [_]
    [:datascript/schema
     {:proxy/header (om/get-query Header)}
     {:proxy/transactions (om/get-query AllTransactions)}
     {:proxy/modal (om/get-query Modal)}])
  Object
  (render
    [this]
    (let [{:keys [proxy/header
                  proxy/transactions
                  proxy/modal]} (om/props this)]
      (html [:div
             [:div (->Modal modal)]
             [:div (->Header header)]
             [:div {:class "content-section-b"}
              (->Payment)
              ;(->AllTransactions transactions)
              ]]))))

(defonce conn-atom (atom nil))

(defn init-conn
  "Sets up the datascript state. Caches the state so we can keep our app state between
  figwheel reloads."
  []
  (if @conn-atom
    (do
      (debug "Reusing old conn. It currently has schema for attributes:" (-> @conn-atom deref :schema keys))
      @conn-atom)
    (let [ui-schema {:ui/singleton {:db/unique :db.unique/identity}}
          ui-state [{:ui/singleton :ui.singleton/app}

                    {:ui/singleton :ui.singleton/modal
                     :ui.singleton.modal/visible false}

                    {:ui.singleton :ui.singleton/menu
                     :ui.singleton.menu/visible false}]
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
                                   :merge   (backend/merge! conn)})]
    (om/add-root! reconciler App (gdom/getElement "my-app"))))

(defn run []
  (info "Run called in: " (namespace ::foo))
  (initialize-app (init-conn)))
