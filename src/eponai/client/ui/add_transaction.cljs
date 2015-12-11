(ns eponai.client.ui.add_transaction
  (:require [om.next :as om :refer-macros [defui]]
            [eponai.client.ui :refer [style]]
            [eponai.client.ui.datepicker :refer [->Datepicker]]
            [eponai.client.ui.tag :as tag]
            [cljs.reader :as reader]
            [sablono.core :as html :refer-macros [html]]
            [cljs-time.core :as t]
            [cljs-time.format :as t.format]
            [cljsjs.pikaday]
            [cljsjs.moment]
            [goog.date.DateTime]
            [garden.core :refer [css]]
            [eponai.client.parser :as parser]
            [datascript.core :as d]))

(defn node [name on-change opts & children]
  (apply vector
         name
         (merge {:on-change on-change} opts)
         children))

(defn input [on-change opts]
  (node :input on-change opts))

(defn select [on-change opts & children]
  (apply node :select on-change opts children))

(defn on-change [this k]
  #(om/update-state! this assoc k (.-value (.-target %))))

(defn new-input-tag! [this name]
  (let [id (random-uuid)]
    (om/update-state!
      this
      (fn [state]
        (-> state
            (assoc ::input-tag "")
            (update ::input-tags conj
                    (assoc
                      (tag/tag-props
                        name
                        #(om/update-state!
                          this update ::input-tags
                          (fn [tags]
                            (into []
                                  (remove (fn [{:keys [::tag-id]}] (= id tag-id)))
                                  tags))))
                      ::tag-id id)))))))

(defmethod parser/read :query/all-currencies
  [{:keys [state query]} _ _]
  {:value (parser/pull-all state query '[[?e :currency/code]])})

(defn input->date [js-date]
  (let [date (doto (goog.date.DateTime.) (.setTime (.getTime js-date)))
        ymd (t.format/unparse (t.format/formatter "yyyy-MM-dd") date)
        ret {:date/ymd   ymd
             :date/day   (t/day date)
             :date/month (t/month date)
             :date/year  (t/year date)}]
    (prn {:input js-date
          :date  ret})
    ret))

(defn input->currency [currency]
  {:currency/code currency})

(defn input->tags [tags]
  (map #(select-keys % [:tag/name]) tags))

(defn input->transaction [amount title description uuid created-at]
  {:transaction/amount     (reader/read-string amount)
   :transaction/name       title
   :transaction/details    description
   :transaction/uuid       uuid
   :transaction/created-at created-at})

(defmethod parser/mutate 'transaction/create
  [{:keys [state]} _ {:keys [::input-amount ::input-currency ::input-title
                             ::input-date ::input-description ::input-tags
                             ::input-uuid ::input-created-at]}]
  (try
    (let [date (assoc (input->date input-date) :db/id -1)
          curr (assoc (input->currency input-currency) :db/id -2)
          tags (map #(assoc %2 :db/id %1)
                    (range (- (- (count input-tags)) 2) -2)
                    (input->tags input-tags))
          transaction (-> (input->transaction input-amount
                                              input-title
                                              input-description
                                              input-uuid
                                              input-created-at)
                          (assoc :transaction/date -1)
                          (assoc :transaction/status :transaction.status/pending)
                          (assoc :transaction/currency -2)
                          (assoc :transaction/tags (mapv :db/id tags)))
          entities (concat [date curr transaction] tags)]
      {:remote true
       :action (fn []
                 (prn {:entities entities})
                 (d/transact! state entities))})
    (catch :default e
      (prn e))))

(defui AddTransaction
  static om/IQuery
  (query [this] [{:query/all-currencies [:currency/code]}])
  Object
  (initLocalState [this] {::input-date (js/Date.)})
  (render
    [this]
    (let [{:keys [query/all-currencies]} (om/props this)
          {:keys [::input-amount ::input-currency ::input-title ::input-date
                  ::input-description ::input-tags ::input-tag]}
          ;; merging state with props, so that we can test the states
          ;; with devcards
          (merge (om/props this)
                 (om/get-state this))
          input-currency (if input-currency input-currency (-> all-currencies
                                                               first
                                                               :currency/code))]
      (html
        [:div
         [:h2 "New Transaction"]
         [:div [:span "Amount:"]
          (input (on-change this ::input-amount)
                 (merge (style {:text-align "right"})
                        {:type        "number"
                         :placeholder "enter amount"
                         :value       input-amount}))
          (apply select (on-change this ::input-currency)
                 {:default-value input-currency}
                 (->> all-currencies
                      (map :currency/code)
                      (map #(let [v (name %)]
                             (vector :option
                                     {:value v
                                      :key   (str "add-transaction-select-currency-value=" v)}
                                     v)))))]
         [:div [:span "Date:"]
          (->Datepicker {:value       input-date
                         :placeholder "enter date"
                         :on-change   #(om/update-state! this assoc ::input-date %)})]
         [:div [:span "Title:"]
          (input (on-change this ::input-title)
                 {:type        "text"
                  :placeholder "enter title"
                  :value       input-title})]
         [:div [:span "Description:"]
          (node :textarea (on-change this ::input-description)
                {:value       input-description
                 :placeholder "enter description"})]
         [:div [:span "Tags:"]
          [:div (style {:display "inline-block"})
           (input (on-change this ::input-tag)
                  {:type        "text"
                   :placeholder "enter tag"
                   :value       input-tag
                   :on-key-down #(when (and (= 13 (.-keyCode %))
                                            (seq (.. % -target -value)))
                                  (.preventDefault %)
                                  (new-input-tag! this input-tag))})]
          (map (fn [props] (tag/->Tag (assoc props :key (::tag-id props))))
               input-tags)]
         [:div "footer"
          [:button {:on-click #(om/transact! this `[(transaction/create ~(-> (om/get-state this)
                                                                             (assoc ::input-uuid (d/squuid))
                                                                             (assoc ::input-created-at (.getTime (js/Date.)))
                                                                             (assoc ::input-currency input-currency)
                                                                             (dissoc ::input-tag)
                                                                             (update ::input-tags
                                                                                     (fn [tags]
                                                                                       (map (fn [t]
                                                                                              (dissoc t ::tag/delete-fn))
                                                                                            tags))))) :query/all-dates])}
           "Save"]]]))))

(def ->AddTransaction (om/factory AddTransaction))
