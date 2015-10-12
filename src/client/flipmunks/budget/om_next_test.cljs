(ns flipmunks.budget.om_next_test
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [goog.dom :as gdom]
            [sablono.core :as html :refer-macros [html]]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [datascript.core :as d]
            [cljs.core.async :as async]
            [clojure.walk :as w]
            [flipmunks.budget.om_query :as omq]
            [flipmunks.budget.datascript :as budgetd]))

(defmulti mutate om/dispatch)

(defui Transaction
  static om/IQuery
  (query [this]
         [:db/id
          :transaction/uuid :transaction/name :transaction/amount
          {:transaction/date [:date/ymd]}
          {:transaction/currency [:currency/name]}])
  Object
  (render [this]
          (let [{:keys [db/id 
                        transaction/date 
                        transaction/uuid 
                        transaction/name
                        transaction/amount 
                        transaction/currency]} (om/props this)]
            (html 
              [:div 
               [:h3 name]
               [:p (str "Id: " id  " uuid: " uuid)]
               [:p (str "Amount: " amount " " (:currency/name currency))]
               [:p (str "Date: " (:date/ymd date))]]))))

(def transaction (om/factory Transaction))

(defui Month
  static om/IQuery
  (query [this]
         [:date/year :date/month
          {:transaction/_date (om/get-query Transaction)}])
  Object
  (render [this]
          (let [{:keys [:date/year :date/month :transaction/_date]}
                (om/props this)]
            (html [:div
                   [:h2 (str year "-" month)]
                   ;; making us not entierly decoupled with the Transaction component
                   (map #(transaction (assoc % :react-key (:transaction/uuid %))) 
                        _date)]))))

(def month (om/factory Month))

(defui Year
  static om/IQuery
  (query [this]
         [{['?app [:app :state]]
           {:q '{:find   [?date .]
                 :where [[?app :app/year ?year]
                         [?app :app/month ?month]
                         [?date :date/year ?year]
                         [?date :date/month ?month]]}
            :each (om/get-query Month)}}])
  Object
  (render [this]
          (let [props (om/props this)
                app (get-in props [['?app [:app :state]]])]
            (prn app)
            (month (first app)))))


(defn find-refs 
  "finds attributes where :db/valueType is ref"
  [datomic-schema]
  (let [conn (d/create-conn)]
    (d/transact conn datomic-schema)
    (set (d/q '[:find [?v ...] 
                :where [?e :db/ident ?v]
                       [?e :db/valueType :db.type/ref]]
              @conn))))

(defn initialize-app [c]
  (go
    (let [{:keys [schema entities]} (:data (async/<! c))
          ref-types  (find-refs schema)
          ds-schema  (-> (budgetd/schema-datomic->datascript schema)
                         (assoc :app {:db/unique :db.unique/identity}))
          conn       (d/create-conn ds-schema)
          parser     (om/parser {:read omq/read :mutate mutate})
          reconciler (om/reconciler {:state conn :parser parser})]
      (d/transact conn [{:app :state :app/year 2015 :app/month 10}])
      (d/transact conn (budgetd/db-id->temp-id ref-types entities))
      (om/add-root! reconciler Year (gdom/getElement "my-app")))))

(defn run 
  "give data-provider a channel -> returns {:schema [] :entities []}"
  [data-provider]
  (let [c (async/chan)]
    (initialize-app c)
    (data-provider c)))

