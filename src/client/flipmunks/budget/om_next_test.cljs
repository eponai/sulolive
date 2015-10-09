(ns flipmunks.budget.om_next_test
  (:require [goog.dom :as gdom]
            [sablono.core :as html :refer-macros [html]]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [datascript.core :as d]
            [flipmunks.budget.datascript :as budgetd]))

(defmulti read om/dispatch)
(defmethod read :app/transaction
  [{:keys [state selector]} _ _]
  {:value (d/q '[:find [(pull ?e ?selector) ...]
                 :in $ ?selector
                 :where [?e :transaction/uuid]]
               (d/db state) 
               selector)})
(defmethod read :app/counter
  [{:keys [state selector]} _ _]
  {:value (d/q '[:find [(pull ?e ?selector) ...]
                 :in $ ?selector
                 :where [?e :app/title]]
               (d/db state) 
               selector)})

(defmulti mutate om/dispatch)
(defmethod mutate 'app/increment
  [{:keys [state]} _ entity]
  {:value [:app/counter]
   :action #(d/transact! state [(update-in entity [:app/count] inc)])})

(defui DayList
  static om/IQuery
  (query [this] [{:app/transaction [{:transaction/date [:date/ymd]}
                                    :transaction/name
                                    :transaction/amount
                                    {:transaction/currency [:currency/name]}]}])
  Object
  (render [this]
          (html [:div
                 (->> (get-in (om/props this) [:app/transaction])
                   (map (fn [{:keys [transaction/date
                                    transaction/name
                                    transaction/amount
                                    transaction/currency]}]
                          [:div 
                           [:h3 name]
                           [:p (str "Amount: " amount " " (:currency/name currency))]
                           [:p (str "Date: " (:date/ymd date))]])))])))

(defui Counter
  static om/IQuery
  (query [this]
         [{:app/counter [:db/id :app/title :app/count]}])
  Object
  (render [this]
    (let [{:keys [app/title app/count] :as entity} 
          (get-in (om/props this) [:app/counter 0])]
      (html [:div
             [:h2 title]
             [:span (str "Count: " count)]
             [:button
              {:on-click #(om/transact! this `[(app/increment ~entity)])}
              "Click me!"]]))))

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
    (d/transact conn [{:db/id -1
                       :app/title "Hello, DataScript!"
                       :app/count 0}])
    (om/add-root! reconciler DayList (gdom/getElement "my-app"))))

