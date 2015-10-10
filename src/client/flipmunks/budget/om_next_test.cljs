(ns flipmunks.budget.om_next_test
  (:require [goog.dom :as gdom]
            [sablono.core :as html :refer-macros [html]]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [datascript.core :as d]
            [flipmunks.budget.datascript :as budgetd]))

(defn read [{:keys [state] :as env} attr {:keys [filters] :as params}]
  (let [selector (or (:selector env) (:selector params))
        q '[:find [(pull ?e ?selector) ...]
            :in $ ?attr ?selector
            :where [?e ?attr]]]
    {:value (d/q (vec (concat q filters))
                 (d/db state) 
                 attr
                 selector)}))

(defmulti mutate om/dispatch)
(comment "om-next datascript tutorial example"
  (defmethod mutate 'app/increment
    [{:keys [state]} _ entity]
    {:value [:app/counter]
     :action #(d/transact! state [(update-in entity [:app/count] inc)])}))

(defui Transaction
  static om/IQuery
  (query [this]
         [:db/id
          :transaction/uuid
          :transaction/name
          :transaction/amount
          {:transaction/date     [:date/ymd]}
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

(defui TransactionList
  static om/IQueryParams
  (params [this]
         {:year 2015 :month 01 :day 01
          ;; By conjing the uuid, we know that we can use it for the react-key
          :transaction (conj (om/get-query Transaction) :transaction/uuid)})
  static om/IQuery
  (query [this]
         '[(:date/ymd {:selector [:date/year :date/month]
                       :filter   [[?e :date/year  ?year]
                                  [?e :date/month ?month]]})
           (:transaction/uuid {:selector ?transaction
                               :filters [[?e :transaction/date ?d]
                                         [?d :date/year  ?year]
                                         [?d :date/month ?month]
                                         [?d :date/day   ?day]]})])
  Object
  (render [this]
    (let [props (om/props this)
          {:keys [:date/year :date/month]} (get-in props [:date/ymd 0])]
      (html [:div
             [:h1 (str year "-" month)]
             (map #(transaction (assoc % :react-key (str (:transaction/uuid %)))) 
                  (:transaction/uuid props))]))))

(defn find-refs 
  "finds attributes where :db/valueType is ref"
  [datomic-schema]
  (let [conn (d/create-conn)]
    (d/transact conn datomic-schema)
    (set (d/q '[:find [?v ...] 
                :where [?e :db/ident ?v]
                       [?e :db/valueType :db.type/ref]]
              @conn))))

(defn run 
  "call data-provider with dates? -> returns {:schema [] :entities []}"
  [data-provider]
  (let [{:keys [schema entities]} (data-provider {:year 2015 :month 01})
        ref-types  (find-refs schema)
        ds-schema  (budgetd/schema-datomic->datascript schema)
        conn       (d/create-conn ds-schema)
        parser     (om/parser {:read read :mutate mutate})
        reconciler (om/reconciler {:state conn :parser parser})] 
    (d/transact conn (budgetd/db-id->temp-id ref-types entities))
    (om/add-root! reconciler TransactionList (gdom/getElement "my-app"))))

