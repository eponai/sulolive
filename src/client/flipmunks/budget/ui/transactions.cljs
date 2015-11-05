(ns flipmunks.budget.ui.transactions
  (:require [om.next :as om :refer-macros [defui]]
            [flipmunks.budget.ui :refer [style]]
            [flipmunks.budget.ui.tag :as tag]
            [cljs-time.core :as t]
            [cljs-time.format :as t.format]
            [sablono.core :as html :refer-macros [html]]
            [garden.core :refer [css]]
            [flipmunks.budget.parse :as parse]))

(defui Transaction
  static om/IQueryParams
  (params [this] {:tag (om/query tag/Tag)})
  static om/IQuery
  (query [this]
         '[:db/id
           :transaction/uuid
           :transaction/name
           :transaction/amount
           :transaction/details
           {:transaction/currency [:currency/name]}
           {:transaction/tags ?tag}

           :ui.transaction/expanded
           :ui.transaction/edit-mode
           :ui.transaction/show-tags
           :ui.transaction/expanded])
  Object
  (render [this]
          (let [{;; rename to avoid replacing clojure.core/name
                 transaction-name :transaction/name
                 :keys            [transaction/tags
                                   transaction/amount
                                   transaction/currency
                                   transaction/details
                                   ui.transaction/show-tags]} (om/props this)]
            (html
              [:div
               [:style (css [:#ui-transaction {:background-color #"fff"}
                             [:&:hover {:background-color "rgb(250,250,250)"}]
                             [:&:active {:background-color "#eee"}]])]
               [:div (-> (style {:display        "flex"
                                 :flex-direction "column"
                                 :border-style   "solid"
                                 :border-width   "1px 0px 0px"
                                 :border-color   "#ddd"
                                 :padding        "0.5em"})
                         (assoc :id "ui-transaction"))
                [:div (style {:display         "flex"
                              :flex-direction  "row"
                              :justify-content "flex-start"
                              })
                 [:div (str amount " " (:currency/name currency))]
                 [:div (style {:margin-left "0.5em"}) transaction-name]]
                (when details
                  [:div (style {:margin    "0em 1.0em"
                                :padding   "0.3em"
                                :fontStyle "italic"})
                   details])
                (when show-tags
                  [:div
                   (map tag/->Tag tags)])]]))))

(def ->Transaction (om/factory Transaction))

;; Transactions grouped by a day

(defn day-of-the-week
  "Given date, returns name of the date and it's number with the appropriate suffix.
  Examples:
  2015-10-16 => Friday 16th
  2015-10-21 => Wednesday 21st"
  [{:keys [date/year date/month date/day]}]
  (str
    ((get t.format/date-formatters "EEEE") (t/date-time year month day))
    " "
    day (condp = (mod day 10)
          1 "st"
          2 "nd"
          3 "rd"
          "th")))
(defn sum
  "Adds transactions amounts together.
  TODO: Needs to convert the transactions to the 'primary currency' some how."
  [transactions]
  {:amount   (transduce (map :transaction/amount) + 0 transactions)
   ;; TODO: Should instead use the primary currency
   :currency (-> transactions first :transaction/currency :currency/name)})

(defn expand-day [this {:keys [db/id ui.day/expanded]}]
  (om/transact! this
                `[(datascript/transact
                    {:txs [[:db.fn/cas ~id
                            :ui.day/expanded
                            ~expanded
                            ~(not expanded)]]})]))

(defui DayTransactions
  static om/IQueryParams
  (params [this] {:transactions (om/get-query Transaction)})
  static om/IQuery
  (query [this]
         '[:db/id
           :date/year
           :date/month
           :date/day
           {:transaction/_date ?transactions}
           :ui.day/expanded])
  Object
  (render [this]
          (let [{transactions :transaction/_date
                 :keys        [ui.day/expanded] :as props} (om/props this)]
            (html [:div
                   (style {:borderWidth "0px 0px 1px"
                           :borderStyle "solid"})
                   [:style (css [:#ui-day {:background-color "#fff"}
                                 [:&:hover {:background-color "rgb(250,250,250)"}]
                                 [:&:active {:background-color "#eee"}]])]
                   [:div (-> (style {:display         "flex"
                                     :flex-direction  "row"
                                     :flex-wrap       "nowrap"
                                     :align-items     "center"
                                     :justify-content "flex-start"})
                             (assoc :id "ui-day"
                                    :on-click #(expand-day this props)))
                    [:div (style {:fontSize   "1.3em"
                                  :fontWeight "bold"})
                     (let [{:keys [amount currency]} (sum transactions)]
                       (str amount " " currency))]
                    [:div (style {:fontSize    "1.3em"
                                  :fontWeight  "bold"
                                  :margin-left "0.5em"})
                     [:span (day-of-the-week props)]]]
                   (when expanded
                     [:div
                      (map ->Transaction transactions)])]))))

(def ->DayTransactions (om/factory DayTransactions))

(defn group-dates-by-year-month [dates]
  (->> dates
       (group-by :date/year)
       (reduce (fn [m [y dates]]
                 (assoc m y (->> dates
                                 (group-by :date/month)
                                 (into (sorted-map)))))
               (sorted-map))))

(defmethod parse/read :query/all-dates
  [{:keys [state selector]} _ _]
  {:value (parse/pull-all state selector '[[?e :date/ymd]])})

(defui AllTransactions
  static om/IQueryParams
  (params [this] {:dates (conj (om/get-query DayTransactions)
                               :date/year
                               :date/month
                               :date/day)})
  static om/IQuery
  (query [this]
         '[{:query/all-dates ?dates}])
  Object
  (render [this]
          (let [{:keys [query/all-dates]} (om/props this)
                by-year-month (group-dates-by-year-month all-dates)]
            (html [:div
                   (map (fn [[year months]]
                          [:div [:span year]
                           (map (fn [[month dates]]
                                  [:div
                                   [:h2 ((get t.format/date-formatters "MMMM")
                                          (t/date-time year month))]
                                   (map ->DayTransactions
                                        (rseq (sort-by :date/day dates)))])
                                (rseq months))])
                        (rseq by-year-month))]))))

(def ->AllTransactions (om/factory AllTransactions))
