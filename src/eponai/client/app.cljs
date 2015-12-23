(ns eponai.client.app
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [goog.dom :as gdom]
    ;; To initialize ReactDOM:
            [cljsjs.react.dom]
            [datascript.core :as d]
            [cljs.core.async :as async]
            [eponai.client.backend :as backend]
            [eponai.client.parser :as parser]
            [eponai.client.datascript :as budget.d]
            [eponai.client.ui.add_transaction :refer [AddTransaction ->AddTransaction]]
            [eponai.client.ui.header :refer [Header ->Header]]
            [eponai.client.ui.transactions :refer [AllTransactions ->AllTransactions]]))

(defui App
  static om/IQuery
  (query [_]
    [{:proxy/header (om/get-query Header)}
     {:proxy/transactions (om/get-query AllTransactions)}])
  Object
  (render
    [this]
    (let [{:keys [proxy/header proxy/transactions]} (om/props this)]
      (html [:div (->Header header)
             (->AllTransactions transactions)]))))

(defonce conn-atom (atom nil))

(defn init-conn
  "Sets up the datascript state. Caches the state so we can keep our app state between
  figwheel reloads."
  [schema txs]
  (if @conn-atom
    @conn-atom
    (let [conn (d/create-conn schema)]
      (d/transact! conn txs)
      (reset! conn-atom conn))))

(defn initialize-app [conn]
  (let [parser (om/parser {:read parser/read
                           :mutate parser/mutate})
        reconciler (om/reconciler {:state   conn
                                   :parser  parser
                                   :remotes [:remote]
                                   :send    (backend/send!)
                                   :merge   (backend/merge! conn)})]
    (om/add-root! reconciler App (gdom/getElement "my-app")))
  )

(defn run []
  (go
    (let [schema (:body (async/<! (backend/db-schema)))
          ds-schema (-> (budget.d/schema-datomic->datascript schema)
                        (assoc :ui/singleton {:db/unique :db.unique/identity}))
          ui-state [{:ui/singleton :budget/header}]]
      (initialize-app (init-conn ds-schema
                                 ui-state)))))
