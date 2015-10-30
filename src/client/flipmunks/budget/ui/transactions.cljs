(ns flipmunks.budget.ui.transactions
  (:require [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [cljs-time.core :as t]
            [cljs-time.format :as t.format]
            [sablono.core :as html :refer-macros [html]]
            [clojure.string :as s]
            [clojure.walk :as w]
            [garden.core :refer [css]]))

(defn ->camelCase [k]
  (when (namespace k)
    (throw (str "cannot camelCase a keyword with a namespace. key=" k)))
  (let [[a & xs] (s/split (name k) "-")]
    (s/join (cons a (map s/capitalize xs)))))

;; Using memoize, since the number of possible keys is limited to css keys
(def ->memCamelCase (memoize ->camelCase))

;; TODO: Make this a macro, so that the transformations are made in compile time

(defn style [style-map]
  (let [camelCased (w/postwalk (fn [x] (if (keyword? x) (->memCamelCase x) x))
                               style-map)]
    {:style (clj->js camelCased)}))

(defn render-tag [tag-name]
  [:div (style {:display "inline-block"})
   [:style (css [:#ui-transaction-tag {:display        "inline-block"
                                       :padding        "0.2em 0.2em"
                                       :margin         "0.2em"
                                       :border-width   "1px"
                                       :border-style   "solid"
                                       :border-radius  "0.3em"
                                       :text-transform "capitalize"
                                       :font-size      "1em"
                                       :border-color   "#ddd"
                                       :cursor         "default"}
                 [:&:hover {:border-color "#aaa"}]
                 [:&:active {:border-color "#ddd"}]])]
   [:div {:id       "ui-transaction-tag"
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
                      :keys            [transaction/tags
                                        transaction/date
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
                        (->> tags (map :tag/name) (map render-tag))])]]))))

(def transaction (om/factory Transaction))

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

(defn expand-day [this {:keys [db/id ui.day/expand]}]
  (om/transact! this
                `[(datascript/transact
                    [[:db.fn/cas ~id
                      :ui.day/expand
                      ~expand
                      (not ~expand)]])]))

(defui DayTransactions
       static om/IQueryParams
       (params [this] {:transactions (om/get-query Transaction)})
       static om/IQuery
       (query [this]
              '[:db/id
                :date/year
                :date/month
                :date/day
                :transaction/_date ?transactions

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
                           (map transaction transactions)])]))))

(def day-of-transactions (om/factory DayTransactions))

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
       (params [this] {:days (conj (om/get-query DayTransactions)
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
                                        (map day-of-transactions
                                             (rseq (sort-by :date/day dates)))])
                                     (rseq months))])
                             (rseq by-year-month))]))))

(def all-transactions (om/factory AllTransactions))
