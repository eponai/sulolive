(ns flipmunks.budget.ui.transactions
  (:require [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [devcards.core :as dc]
            [cljs-time.core :as t]
            [cljs-time.format :as t.format]
            [sablono.core :as html :refer-macros [html]]
            [garden.core :refer [css]])
  (:require-macros [devcards.core :refer [defcard]]))

(defn day-of-the-week [{:keys [date/year date/month date/day]}]
  (str (get t.format/days (t/day-of-week (t/date-time year month day)))
       " "
       day
       (condp = (mod day 10)
         1 "st"
         2 "nd"
         3 "rd"
         "th")))

(defn style [style-map]
  {:style (clj->js style-map)})

(defn render-tag [tag-name]
  (vector :span (style {:padding "0.5em" :border "2px"}) 
          tag-name))

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
            (let [{:keys [transaction/tags
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
                                :justify-content "space-between"})
                   [:p (style {:display "flex"
                                 :flex-direction "row"
                                 :fontWeight "bold"})
                    (day-of-the-week date)]
                   [:div (style {:display "flex"
                                 :flex-direction "reverse-row"})
                    (str amount " " (:currency/name currency))]]
                  (when details
                     [:div details])
                  (when show-tags
                    [:div
                     (->> tags (map :tag/name) (map render-tag))])]]))))

(def transaction (om/factory Transaction))

(defn transaction-props [name [y m d] amount currency tags]
  {:transaction/name name
   :transaction/date {:date/ymd (str y "-" m "-" d)
                      :date/year y :date/month m :date/day d}
   :transaction/amount amount
   :transaction/currency {:currency/name currency}
   :transaction/tags (mapv #(hash-map :tag/name %) tags)})

(def standard-transaction 
  (transaction-props "coffee" [2015 10 16] 140 "THB" 
                     ["thailand" "2015" "chiang mai"]))

(defcard transaction-card
  (transaction standard-transaction))

(defcard transaction-with-details
  (transaction (assoc standard-transaction
                      :transaction/details
                      "Great latte next to the dumpling place. We should come back here next time we're in Chaing Mai, Thailand.")))

(defcard transaction-with-tags
  (transaction (assoc standard-transaction
                      :ui.transaction/show-tags
                      true)))

(defcard transaction-with-details-and-tags
  (transaction (assoc standard-transaction
                      :transaction/details
                      "Very good latte! We should come back."
                      :ui.transaction/show-tags
                      true)))


