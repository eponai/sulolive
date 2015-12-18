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

(defn just-read
  [{:keys [parser query] :as env} _ _]
  (let [v (parser env query nil)]
    {:value v}))

(defmethod parser/read :just/add-transaction [e k p] (just-read e k p))
(defmethod parser/read :just/header [e k p] (just-read e k p))
(defmethod parser/read :just/transactions [e k p] (just-read e k p))

(defui App
  static om/IQuery
  (query [this]
    [{:just/header (om/get-query Header)}
     {:just/transactions (om/get-query AllTransactions)}])
  Object
  (render
    [this]
    (let [{:keys [just/header just/transactions]} (om/props this)]
      (html [:div (->Header header)
             (->AllTransactions transactions)]))))

(defn find-refs
  "finds attributes where :db/valueType is ref"
  [datomic-schema]
  (let [conn (d/create-conn)]
    (d/transact conn datomic-schema)
    (set (d/q '[:find [?v ...]
                :where [?e :db/ident ?v]
                [?e :db/valueType :db.type/ref]]
              @conn))))

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

(defn initialize-app [c]
  (go
    (let [{:keys [schema entities]} (async/<! c)
          ref-types (find-refs schema)
          ds-schema (-> (budget.d/schema-datomic->datascript schema)
                        (assoc :app {:db/unique :db.unique/identity}
                               :ui/singleton {:db/unique :db.unique/identity}))
          default-txs (concat [{:app :state :app/year 2015 :app/month 10}]
                              [{:ui/singleton :budget/header}]
                              (budget.d/db-id->temp-id ref-types entities))
          conn (init-conn ds-schema default-txs)
          parser (om/parser {:read parser/read :mutate parser/mutate})
          reconciler (om/reconciler {:state conn :parser parser :remotes [:remote]
                                     :send  (backend/send conn)
                                     :merge (fn [reconciler state f]
                                              (f reconciler state))})]
      (om/add-root! reconciler App (gdom/getElement "my-app")))))

(defn run
  "give data-provider a channel -> returns {:schema [] :entities []}"
  [data-provider]
  (let [c (async/chan)]
    (initialize-app c)
    (data-provider c)))
