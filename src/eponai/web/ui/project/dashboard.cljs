(ns eponai.web.ui.project.dashboard
  (:require
    [eponai.common.report :as report]
    [eponai.web.ui.project.add-transaction :as at]
    [eponai.web.ui.project.all-transactions :refer [Transaction]]
    [eponai.web.ui.d3.balance-chart :as bc]
    [eponai.web.ui.d3.pie-chart :as pc]
    [eponai.web.ui.daterangepicker :refer [->DateRangePicker]]
    [eponai.web.ui.icon :as icon]
    [om.next :as om :refer-macros [defui]]
    [goog.string :as gstring]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [error debug]]
    [eponai.common.format.date :as date]))

(defn keep-this-period [transactions start-date end-date]
  (let [start-time (date/date-time->long start-date)
        end-time (date/date-time->long end-date)
        get-time (comp :date/timestamp :transaction/date)
        _ (assert (apply >= (mapv get-time transactions))
                  (str "Transactions not in timestamp order!: " (mapv get-time transactions)))
        ret (into [] (comp (drop-while #(> (get-time %) end-time))
                           (take-while #(<= start-time (get-time %))))
                  transactions)]
    ret))

(defui Dashboard
  static om/IQueryParams
  (params [_]
    {:filter {}
     :transaction (om/get-query Transaction)})
  static om/IQuery
  (query [_]
    ['({:query/transactions ?transaction} {:filter ?filter})
     {:proxy/quick-add-transaction (om/get-query at/QuickAddTransaction)}])
  Object
  (initLocalState [this]
    {:start-date        (date/first-day-of-this-month)
     :end-date          (date/last-day-of-this-month)
     ::on-date-apply-fn (fn [{:keys [start-date end-date selected-range]}]
                          (om/update-state! this assoc :start-date start-date :end-date end-date))})
  (render [this]
    (let [{:keys [query/transactions proxy/quick-add-transaction]} (om/props this)
          {:keys [::on-date-apply-fn start-date end-date]} (om/get-state this)
          {:keys [project]} (om/get-computed this)
          transactions (keep-this-period transactions start-date end-date)
          {:keys [housing limit transport spent avg-daily-spent left-by-end budget]} (report/summary transactions start-date end-date)
          balance-report (report/balance-vs-spent transactions start-date end-date)]
      (html
        [:div#dashboard
         (at/->QuickAddTransaction (om/computed quick-add-transaction
                                                {:project project}))
         [:div.row.align-center
          (->DateRangePicker (om/computed {:single-calendar? false
                                           :start-date       start-date
                                           :end-date         end-date}
                                          {:on-apply on-date-apply-fn
                                           :format   "MMM dd"}))]
         [:div.content-section
          [:div.row.section-title
           (icon/dashboard-balance)
           [:span "Overview"]]

          [:div.row#balance-spent
           (bc/->BalanceChart {:id     "balance-spent-chart"
                               :report balance-report})]

          [:div.row#key-metrics
           [:div.column.key-metric
            [:div.val-txt (gstring/format "%.2f" (- limit spent))]
            [:div.title-txt "Balance"]]
           [:div.column.key-metric
            [:div.val-txt (gstring/format "%.2f" (or avg-daily-spent 0))]
            [:div.title-txt "Avg. Spent per day"]]
           [:div.column.key-metric
            [:div.val-txt (gstring/format "%.2f" (or left-by-end 0))]
            [:div.title-txt "By " (date/date->string (date/last-day-of-this-month) "MMM dd")]]]]
         [:div.content-section


          [:div.row#pie-charts
           [:div.column
            (pc/->PieChart {:id    "housing-chart"
                            :title "Housing"
                            :value (or housing 0)
                            :limit spent})]
           [:div.column
            (pc/->PieChart {:id    "transport-chart"
                            :title "Transport"
                            :value (or transport 0)
                            :limit spent})]
           [:div.column
            (pc/->PieChart {:id    "budget-pie-chart"
                            :title "Budget"
                            :value (or budget 0)
                            :limit limit})]]]]))))

(comment
  "Top categories code not included in our first launch (beta)."
  [:div.content-section
   [:div.row.column
    [:div.section-title
     (icon/dashboard-categories)
     [:span "Top Categories"]]]])

(def ->Dashboard (om/factory Dashboard))
