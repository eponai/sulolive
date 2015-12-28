(ns eponai.client.ui.add_transaction
  (:require [om.next :as om :refer-macros [defui]]
            [eponai.client.ui :refer [style]]
            [eponai.client.ui.datepicker :refer [->Datepicker]]
            [eponai.client.ui.tag :as tag]
            [eponai.client.format :as format]
            [sablono.core :as html :refer-macros [html]]
            [cljsjs.pikaday]
            [cljsjs.moment]
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
            (assoc :input-tag "")
            (update :input-tags conj
                    (assoc
                      (tag/tag-props
                        name
                        #(om/update-state!
                          this update :input-tags
                          (fn [tags]
                            (into []
                                  (remove (fn [{:keys [::tag-id]}] (= id tag-id)))
                                  tags))))
                      ::tag-id id)))))))

(defui AddTransaction
  static om/IQuery
  (query [this] [{:query/all-currencies [:currency/code]}])
  Object
  (initLocalState [this] {:input-date (js/Date.)})
  (render
    [this]
    (let [{:keys [query/all-currencies]} (om/props this)
          {:keys [input-amount input-currency input-title input-date
                  input-description input-tags input-tag]}
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
          (input (on-change this :input-amount)
                 (merge (style {:text-align "right"})
                        {:type        "number"
                         :placeholder "enter amount"
                         :value       input-amount}))
          (apply select (on-change this :input-currency)
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
                         :on-change   #(om/update-state! this assoc :input-date %)})]
         [:div [:span "Title:"]
          (input (on-change this :input-title)
                 {:type        "text"
                  :placeholder "enter title"
                  :value       input-title})]
         [:div [:span "Description:"]
          (node :textarea (on-change this :input-description)
                {:value       input-description
                 :placeholder "enter description"})]
         [:div [:span "Tags:"]
          [:div (style {:display "inline-block"})
           (input (on-change this :input-tag)
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
          [:button {:on-click #(om/transact! this `[(transaction/create
                                                      ~(let [state (om/get-state this)]
                                                         (-> state
                                                             (assoc :input-date (format/date->ymd-str (:input-date state)))
                                                             (assoc :input-uuid (d/squuid))
                                                             (assoc :input-created-at (.getTime (js/Date.)))
                                                             (assoc :input-currency input-currency)
                                                             (dissoc :input-tag)
                                                             (update :input-tags
                                                                     (fn [tags]
                                                                       (map :tag/name tags))))))
                                                    :query/all-dates])}
           "Save"]]]))))

(def ->AddTransaction (om/factory AddTransaction))
