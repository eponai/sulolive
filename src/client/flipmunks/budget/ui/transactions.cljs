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

(defui Transaction
  static om/IQuery
  (query [this] [:db/id :transaction/uuid
                 :transaction/name :transaction/amount
                 {:transaction/date [:date/ymd :date/day]}
                 {:transaction/currency [:currency/name]}
                 {:transaction/tags [:tag/name]}

                 :ui.transaction/expanded
                 :ui.transaction/edit-mode
                 :ui.transaction/expanded])
    Object
    (render [this]
            (let [{:keys [transaction/tags
                          transaction/date
                          transaction/amount
                          transaction/currency]} (om/props this)
                  ]
              (prn tags)
              (html
                [:div 
                 ;; TODO: Figure out what to do about the #
                 [:style (str "#"(css [:foo:active {:background-color "#ddd"}]))]
                [:div {:id "foo"
                        :style #js {:display "flex"
                                    :flex-direction "column"}}
                     [:div {:style #js {:display "flex"
                                        :flex-direction "row"
                                        :flex-wrap "nowrap"
                                        :justify-content "space-between"}}
                      [:div {:style #js {:display "flex"
                                         :flex-direction "row"}}
                       (day-of-the-week date)]
                      [:div {:style #js {:display "flex"
                                         :flex-direction "reverse-row"}}
                     (str amount " " (:currency/name currency))]]
                     [:div {:style #js {:display "flex" :flex-direction "column"
                                        :align-items "center"}}
                      [:div {:style #js {:height "1px" :width "50%" :background-color "#ddd"}}]]
                     [:div "Tags: "
                      (->> tags
                           (map :tag/name)
                           (map #(vector :span {:style #js {:padding "0.3em"
                                                            :border "2px"}} %)))]]]))))

(def transaction (om/factory Transaction))

(defn transaction-props [name [y m d] amount currency tags]
  {:transaction/name name
   :transaction/date {:date/ymd (str y "-" m "-" d)
                      :date/year y :date/month m :date/day d}
   :transaction/amount amount
   :transaction/currency {:currency/name currency}
   :transaction/tags (mapv #(hash-map :tag/name %) tags)})

(defcard transaction-card
  (let [data (transaction-props "coffee" [2015 10 16] 140 "THB" 
                                ["thailand" "2015" "chiang mai"])]
    (prn data)
    (transaction data)))

