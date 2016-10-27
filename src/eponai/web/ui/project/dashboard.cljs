(ns eponai.web.ui.project.dashboard
  (:require
    [eponai.common.report :as report]
    [eponai.web.ui.project.all-transactions :refer [Transaction]]
    [eponai.web.ui.d3.balance-chart :as bc]
    [eponai.web.ui.d3.pie-chart :as pc]
    [eponai.web.ui.icon :as icon]
    [om.next :as om :refer-macros [defui]]
    [goog.string :as gstring]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [error debug]]))

(defui Dashboard
  static om/IQueryParams
  (params [_]
    {:filter {}
     :transaction (om/get-query Transaction)})
  static om/IQuery
  (query [_]
    ['({:query/transactions ?transaction} {:filter ?filter})])
  Object
  (render [this]
    (let [{:keys [query/transactions]} (om/props this)
          {:keys [housing limit transport spent avg-daily-spent left-by-end budget]} (report/summary transactions)
          balance-report (report/balance-vs-spent transactions)
          ]
      (debug "Balance: " balance-report)
      (html
        [:div#dashboard
         [:div.row.align-center
          [:a.button.black.hollow.month
           "September"
           ;[:i.fa.fa-caret-down.fa-fw]
           ]]
         [:div.content-section
          [:div.row.section-title
           (icon/dashboard-balance)
           [:span "Overview"]]

          [:div.row#balance-spent
           (bc/->BalanceChart {:id     "balance-spent-chart"
                               :report balance-report})]

          [:div#key-metrics
           [:div.key-metric
            [:div.val-txt (- limit spent)]
            [:div.title-txt "Balance"]]
           [:div.key-metric
            [:div.val-txt (gstring/format "%.2f" avg-daily-spent)]
            [:div.title-txt "Avg. Spent per day"]]
           [:div.key-metric
            [:div.val-txt (gstring/format "%.2f" left-by-end)]
            [:div.title-txt "By Oct 31"]]]]
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
                            :limit limit})]]]

         [:div.content-section
          [:div.row.column
           [:div.section-title
            (icon/dashboard-categories)
            [:span "Top Categories"]]]]]))))

(def ->Dashboard (om/factory Dashboard))
