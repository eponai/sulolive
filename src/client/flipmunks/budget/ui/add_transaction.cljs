(ns flipmunks.budget.ui.add_transaction
  (:require [om.next :as om :refer-macros [defui]]
            [flipmunks.budget.ui :refer [style]]
            [sablono.core :as html :refer-macros [html]]
            [cljsjs.pikaday]
            [cljsjs.moment]
            [garden.core :refer [css]]))

(defn node [name on-change opts & children]
  (apply vector
         name
         (merge {:on-change on-change} opts)
         children))

(defn input [on-change opts]
  (node :input on-change opts))

(defn select [on-change opts children]
  (apply node :select on-change opts children))

(defn on-change [this k]
  #(om/update-state! this assoc k (.-value (.-target %))))

(def pikaday-ref-name "pikaday")

(defn get-pikaday-dom-node [this]
  (.getDOMNode (om/react-ref this pikaday-ref-name)))

(defn set-date-if-changed [this new-date old-date]
  (let [new-time (when new-date (.getTime new-date))
        old-time (when old-date (.getTime old-date))]
    (when (not= new-time old-time)
      (if new-date
        ;; pass true to avoid calling onSelect
        (some-> (om/get-state this) ::picker deref (.setDate new-date true))
        ;; workaround for pikaday not clearing value when date set to falsey
        (.value (get-pikaday-dom-node this) "")))))

(defui DatePicker
       Object
       (getInitialState [this] {::picker (atom nil)})
       (componentDidMount
         [this]
         (let [{:keys [on-change value]} (om/props this)
               {:keys [::picker]} (om/get-state this)
               p (js/Pikaday.
                   #js {:field    (get-pikaday-dom-node this)
                        :format   "D MMM YYYY"
                        :onSelect on-change})]
           (reset! picker p)
           (set-date-if-changed this value nil)))
       (componentWillUnmount [this] (some-> (om/get-state this)
                                            (::picker)
                                            (reset! nil)))
       (componentWillReceiveProps [this next-props]
                                  (let [{old-value :value} (om/props this)
                                        {next-value :value} next-props]
                                    (set-date-if-changed this next-value old-value)))
       (render [this]
               (html [:input {:type "text"
                              :ref pikaday-ref-name
                              :placeholder (-> this om/props :placeholder)}])))

(def datepicker (om/factory DatePicker))

(defui AddTransaction
       om/IQuery
       (query [this] [:query/all-currencies])
       Object
       (render
         [this]
         (let [{:keys [query/all-currencies]} (om/props this)
               {:keys [::edit-amount ::edit-currency ::edit-title
                       ::edit-date] :as state}
               ;; merging state with props, so that we can test the states
               ;; with devcards
               (merge (om/props this)
                      (om/get-state this))]
           (prn state)
           (html
             [:div
              [:h2 "New Transaction"]
              [:div [:span "Amount:"]
               (input (on-change this ::edit-amount)
                      (merge (style {:text-align "right"})
                             {:type        "number"
                              :placeholder "enter amount"
                              :value       edit-amount}))
               (select (on-change this ::edit-currency)
                       nil
                       (map #(let [v (name %)]
                              (vector :option
                                      (merge {:value v}
                                             (when (= v edit-currency)
                                               {:selected "selected"}))
                                      v))
                            all-currencies))]
              [:div [:span "Date:"]
               (datepicker {:value edit-date
                            :placeholder "enter date"
                            :on-change #(om/update-state!
                                         this
                                         assoc
                                         ::edit-date
                                         %)})]
              [:div [:span "Title:"]
               (input (on-change this ::edit-title)
                      {:type        "text"
                       :placeholder "enter title"
                       :value       edit-title})]]))))

(def add-transaction (om/factory AddTransaction))
