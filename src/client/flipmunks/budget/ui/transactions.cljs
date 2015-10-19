(ns flipmunks.budget.ui.transactions
  (:require [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [cljs-time.core :as t]
            [cljs-time.format :as t.format]
            [sablono.core :as html :refer-macros [html]]
            [garden.core :refer [css]]))

(defn day-of-the-week 
  "Given date, returns name of the date and it's number with the appropriate suffix.
  Examples:
  2015-10-16 => Friday 16th
  2015-10-21 => Wednesday 21st"
  [{:keys [date/year date/month date/day]}]
  (str (get t.format/days (t/day-of-week (t/date-time year month day)))
       " "
       day (condp = (mod day 10)
             1 "st"
             2 "nd"
             3 "rd"
             "th")))

(defn style [style-map]
  {:style (clj->js style-map)})

(defn render-tag [tag-name]
  [:div (style {:display "inline-block"})
   [:style (css [:#ui-transaction-tag {:display "inline-block"
                                       :padding "0.2em 0.2em" 
                                       :margin "0.2em"
                                       :border-width "1px"
                                       :border-style "solid"
                                       :border-radius "0.3em"
                                       :text-transform "capitalize"
                                       :font-size "1em"
                                       :border-color "#ddd"
                                       :cursor "default"}
                 [:&:hover {:border-color "#aaa"}]
                 [:&:active {:border-color "#ddd"}]])]
   [:div {:id "ui-transaction-tag"
          :on-click #(prn %)}
    tag-name]])

(defui Transaction
  static om/IQuery
  (query [this] [:db/id :transaction/uuid
                 :transaction/name :transaction/amount
                 :transaction/details
                 {:transaction/date [:date/ymd :date/day]}
                 {:transaction/currency [:currency/name]}
                 {:transaction/tags [:tag/name]}

                 :ui.transaction/expanded
                 :ui.transaction/edit-mode
                 :ui.transaction/show-tags
                 :ui.transaction/expanded])
    Object
    (render [this]
            (let [{;; rename to avoid replacing clojure.core/name
                   transaction-name :transaction/name 
                   :keys [transaction/tags
                          transaction/date
                          transaction/amount
                          transaction/currency
                          transaction/details
                          ui.transaction/show-tags]} (om/props this)]
              (prn tags)
              (html
                [:div 
                 [:style (css [:#day:active {:background-color "rgb(250,250,250)"}])]
                 [:div (assoc (style {:display "flex" :flex-direction "column"
                                      :borderStyle "solid"
                                      :borderWidth "1px"
                                      :borderRadius "0.5em"
                                      :padding "0.5em"}) 
                              :id "day")
                  [:div (style {:display "flex"
                                :flex-direction "row"
                                :flex-wrap "nowrap"
                               :align-items "center"
                                :justify-content "space-between"})
                   [:div (style {:display "flex"
                                 :flex-direction "row"
                                 :fontWeight "bold"})
                    transaction-name]
                   [:div (style {:display "flex"
                                 :flex-direction "reverse-row"})
                    (str amount " " (:currency/name currency))]]
                  (when details
                    [:div (style {:margin "0em 1.0em":padding "0.3em"}) details])
                  (when show-tags
                    [:div
                     (->> tags (map :tag/name) (map render-tag))])]]))))

(def transaction (om/factory Transaction))

;; Transactions grouped by a day

(defn sum
  "Adds transactions amounts together.
  TODO: Needs to convert the transactions to the 'primary currency' some how."
  [transactions]
  {:amount (transduce (map :transaction/amount) + 0 transactions)
   ;; TODO: Should instead use the primary currency
   :currency (-> transactions first :transaction/currency :currency/name)})

(defui DayTransactions
  static om/IQueryParams
  (params [this] {:transactions (om/get-query Transaction)})
  static om/IQuery
  (query [this] 
         '[:db/id
           :date/year
           :date/month
           :date/day
           :transaction/_date ?transactions])
  Object
  (render [this]
          (let [{:keys [date/year date/month date/day
                        transaction/_date] :as date} (om/props this)
                ]
            (html [:div
                   [:div (style {:display "flex"
                                 :flex-direction "row"
                                 :flex-wrap "nowrap"
                                 :align-items "center"
                                 :justify-content "space-between"})
                    [:div (style {:display "flex"
                                  :flex-direction "row"
                                  :fontWeight "bold"})
                     (day-of-the-week date)]
                    [:div (style {:display "flex"
                                  :flex-direction "reverse-row"})
                     (let [{:keys [amount currency]} (sum _date)]
                       (str amount " " currency))]]]))))

(def day-of-transactions (om/factory DayTransactions))
