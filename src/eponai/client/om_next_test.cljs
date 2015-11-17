(ns eponai.client.om_next_test
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [goog.dom :as gdom]
            [sablono.core :as html :refer-macros [html]]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [datascript.core :as d]
            [cljs.core.async :as async]
            [clojure.walk :as w]
            [eponai.client.om_query :as omq]
            [eponai.client.datascript :as budgetd]))

(defmulti mutate om/dispatch)

(defui Transaction
  static om/IQuery
  (query [this]
         [:db/id
          :transaction/uuid 
          :transaction/name 
          :transaction/amount
          {:transaction/date [:date/ymd]}
          {:transaction/currency [:currency/name]}])
  Object
  (render [this]
          (let [{:keys [db/id
                        transaction/date
                        transaction/uuid
                        transaction/name
                        transaction/amount
                        transaction/currency]} (om/props this)
                {:keys [expanded]} (om/get-state this)]
            (prn {:expanded expanded})
            (html
              [:div
               [:p {:on-click #(om/update-state! 
                                 this 
                                 (fn [s] (prn s) (update s :expanded not)))} 
                name]
               (when expanded
                 [:p (str "Id: " id  " uuid: " uuid)] 
                 [:p (str "Amount: " amount " " (:currency/name currency))] 
                 [:p])]))))

(def transaction (om/factory Transaction))

(defui Days
  static om/IQuery
  (query [this]
         [:date/year
          :date/month
          :date/day
          {:transaction/_date (om/get-query Transaction)}])
  Object
  (render [this]
          (let [{:keys [:date/year :date/month :date/day :transaction/_date]}
                (om/props this)]
            (html [:div
                   [:h2 (str year "-" month "-" day)]
                   ;; making us not entierly decoupled with the Transaction component
                   (map #(transaction (assoc % :react-key (:transaction/uuid %))) 
                        _date)]))))

(def days-view (om/factory Days))

(defui Year
  static om/IQuery
  (query [this]
         [{['?app [:app :state]]
           {:q '{:find   [[?date ...]]
                 :where [[?app :app/year ?year]
                         [?app :app/month ?month]
                         [?date :date/year ?year]
                         [?date :date/month ?month]]}
            :each (om/get-query Days)}}])
  Object
  (render [this]
          (let [props  (om/props this)
                days   (get-in props [['?app [:app :state]]])
                days   (sort-by :date/day days)]
            (prn days)
            (html 
              [:div 
               (map #(days-view (assoc % :react-key (str "day-" (:date/day %))))
                    days)]))))


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

