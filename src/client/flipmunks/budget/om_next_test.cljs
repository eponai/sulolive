(ns flipmunks.budget.om_next_test
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [goog.dom :as gdom]
            [sablono.core :as html :refer-macros [html]]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [datascript.core :as d]
            [datascript.db :as d.db]
            [cljs.core.async :as async]
            [clojure.walk :as w]
            [flipmunks.budget.datascript :as budgetd]))

(defn find-value [conn lookup-ref]
  (d/pull @conn '[*] lookup-ref))

(defn query [conn q-map sym sym-val]
  (let [gen-sym (gensym "?x")
        {:keys [find where]} (w/postwalk #(if (= sym %) gen-sym %)
                                         q-map)
        a (vec (concat [:find]
                       find
                       [:in '$ gen-sym]
                       [:where]
                       where))]
    (d/q a 
         @conn
         sym-val)))

(defn make-seq [x]
  (if (or (seq? x) (vector? x))
    x
    [x]))

(defn read [{:keys [parser state selector entity] :as env} attr params]
  (let [ret (cond 
              (keyword? attr) 
              (if (nil? selector)
                (-> (d/entity (d/db state) entity)
                    (get attr))
                (let [entities (-> (d/pull (d/db state) [attr] entity)
                                   (get attr)
                                   make-seq
                                   (->> (map :db/id)))
                      query  (if (vector? selector)
                               selector
                               (:each selector))
                      result (map #(parser (assoc env :entity % :selector nil) query)
                                 entities)
                      schema (d.db/-schema (d/db state))]
                  (if (or
                        (= "_" (first (name attr)))
                        (= :db.cardinality/many (get-in schema [attr :db/cardinality])))
                    (vec result)
                    (first result))))

              (symbol? attr)
              (let [sym-val (:db/id (find-value state (:id params)))
                    res     (-> (query state (:q selector) attr sym-val)
                                (make-seq))]
                (map #(parser (assoc env :entity % :selector nil) (:each selector)) 
                     res)))]
    {:value ret}))

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

(defui Month
  static om/IQueryParams
  (params [this]
          {:trans (om/get-query Transaction)})
  static om/IQuery
  (query [this]
         '[:date/year
           :date/month
           {:transaction/_date {:each ?trans}}])
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
  static om/IQueryParams
  (params [this]
          {:month-query (om/get-query Month)})
  static om/IQuery
  (query [this]
         '[{[?app [:app :state]]
            {:q {:find   [?date .]
                 :where [[?app :app/year ?year]
                         [?app :app/month ?month]
                         [?date :date/year ?year]
                         [?date :date/month ?month]]}
             :each ?month-query}}])
  Object
  (render [this]
          (let [props (om/props this)
                app (get-in props [['?app [:app :state]]])]
            (prn (first app))
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
          parser     (om/parser {:read read :mutate mutate})
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

