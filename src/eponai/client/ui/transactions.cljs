(ns eponai.client.ui.transactions
  (:require [om.next :as om :refer-macros [defui]]
            [eponai.client.ui :refer [style]]
            [eponai.client.ui.tag :as tag]
            [cljs-time.core :as t]
            [cljs-time.format :as t.format]
            [sablono.core :as html :refer-macros [html]]
            [garden.core :refer [css]]
            [eponai.client.parser :as parser]))

(defui Transaction
  static om/IQueryParams
  (params [this] {:tag (om/get-query tag/Tag)})
  static om/IQuery
  (query [this]
         '[:db/id
           :transaction/uuid
           :transaction/name
           :transaction/amount
           :transaction/details
           :transaction/status
           {:transaction/currency [:currency/name]}
           {:transaction/tags ?tag}
           ::transaction-show-tags?])
  Object
  (render [this]
          (let [{;; rename to avoid replacing clojure.core/name
                 transaction-name :transaction/name
                 :keys            [transaction/tags
                                   transaction/amount
                                   transaction/currency
                                   transaction/details
                                   transaction/status
                                   ::transaction-show-tags?]} (om/props this)]
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
                 [:div (style {:margin-left "0.5em"
                               :font-weight "bold"
                               :color       (if status
                                              (condp = status
                                               :transaction.status/synced "green"
                                               :transaction.status/pending "orange"
                                               :transaction.status/failed "red")
                                              "blue")})
                  transaction-name]]
                (when details
                  [:div (style {:margin    "0em 1.0em"
                                :padding   "0.3em"
                                :fontStyle "italic"})
                   details])
                (when transaction-show-tags?
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

(defui DayTransactions
  static om/IQueryParams
  (params [this] {:transactions (om/get-query Transaction)})
  static om/IQuery
  (query [this]
         '[:db/id
           :date/year
           :date/month
           :date/day
           :date/ymd
           {:transaction/_date ?transactions}
           ::day-expanded?])
  Object
  (render [this]
          (let [{transactions :transaction/_date
                 :keys        [::day-expanded?] :as props} (om/props this)
                expanded (if (nil? day-expanded?)
                           (::day-expanded? (om/get-computed this))
                           day-expanded?)]
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
                                    :on-click #(parser/cas! this (:db/id props)
                                                            ::day-expanded?
                                                            (::day-expanded? props)
                                                            (not expanded))))
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

(def ->DayTransactions (om/factory DayTransactions {:keyfn :date/ymd
                                                    :validator :date/ymd}))

(defn group-dates-by-year-month [dates]
  (->> dates
       (group-by :date/year)
       (reduce (fn [m [y dates]]
                 (assoc m y (->> dates
                                 (group-by :date/month)
                                 (into (sorted-map)))))
               (sorted-map))))

(defui AllTransactions
  static om/IQueryParams
  (params [this] {:dates (om/get-query DayTransactions)})
  static om/IQuery
  (query [this]
         '[{:query/all-dates ?dates}])
  Object
  (render [this]
          (let [{:keys [query/all-dates]} (om/props this)
                by-year-month (group-dates-by-year-month all-dates)]
            (html [:div {:key "transactions"}
                   (->> (rseq by-year-month)
                        (map (fn [[year months]]
                               [:div {:key (str "transactions-by-year=" year)}
                                [:span {:key (str "transaction-span-year=" year)} year]
                                (->> (rseq months)
                                     (map (fn [[month dates]]
                                            [:div {:key (str "transactions-by-year=" year "-month=" month)}
                                             [:h2 {:key (str "transactions-h2-year=" year "-month=" month)}
                                              ((get t.format/date-formatters "MMMM")
                                                    (t/date-time year month))]
                                             (map ->DayTransactions
                                                  (->> dates
                                                       (sort-by :date/day)
                                                       (rseq)))])))])))]))))

(def ->AllTransactions (om/factory AllTransactions))
