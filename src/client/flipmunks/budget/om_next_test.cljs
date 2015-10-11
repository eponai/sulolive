(ns flipmunks.budget.om_next_test
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [goog.dom :as gdom]
            [sablono.core :as html :refer-macros [html]]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [datascript.core :as d]
            [cljs.core.async :as async]
            [clojure.walk :as w]
            [flipmunks.budget.datascript :as budgetd]))

(defn lookup-vals [conn entity attr]
  (d/q '[:find [?v ...] 
         :in $ ?e ?a 
         :where [?e ?a ?v]]
       (d/db conn)
       entity
       attr))

(defn pull-entities [conn entity attr]
  (d/pull @conn [attr] entity))

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
              (if selector
                (let [es (-> (pull-entities state entity attr)
                             (get attr)
                             make-seq
                             (->> (map :db/id)))
                      query (or (:each selector)) ;; introduce :once?
                      parse (fn [e] 
                              (->> query 
                                   (parser (assoc env :entity e :selector nil))
                                   (reduce (fn [m [k v]]
                                             ;; a parsed query can return maps with keys
                                             ;; of the results? (wat).
                                             ;; do the right thing!
                                             (if (contains? v k)
                                               (merge m v)
                                               (assoc m k v)))
                                           {})))]
                  (if (:each selector)
                    (map parse es)
                    (throw "selector needs the :each keyword. was: " selector)))
                (d/pull @state [attr] entity))

              (symbol? attr)
              (let [sym-val (:db/id (find-value state (:id params)))
                    res     (-> (query state (:q selector) attr sym-val)
                                (make-seq))]
                (map #(parser (assoc env :entity % :selector nil) (:each selector)) 
                     res)))]
    {:value ret}))

(comment (defn read [{:keys [state] :as env} attr {:keys [filters] :as params}]
  (let [selector (or (:selector env) (:selector params))
        q '[:find [(pull ?e ?selector) ...]
            :in $ ?attr ?selector
            :where [?e ?attr]]]
    {:value (d/q (vec (concat q filters))
                 (d/db state) 
                 attr
                 selector)})))

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
          {:transaction/date     {:each [:date/ymd]}}
          {:transaction/currency {:each [:currency/name]}}])
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
         {:year 2015 :month 10 :day 10
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
          (html [:div "Foo"])))

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
            (prn (first app)))
          (html [:div "Foo"])))


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
  "call data-provider with dates? -> returns {:schema [] :entities []}"
  [data-provider]
  (let [c (async/chan)]
    (initialize-app c)
    (data-provider c)))

