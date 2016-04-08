(ns eponai.web.ui.chart-settings
  (:require
    [cljs-time.coerce :as coerce]
    [cljs-time.core :as time]
    [eponai.client.ui :refer-macros [opts]]
    [eponai.common.format :as format]
    [eponai.web.ui.utils :as utils]
    [eponai.web.ui.utils.filter :as filter]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug]]
    ))

(defn- button-class [field value]
  (if (= field value)
    "button primary"
    "button secondary"))

(defn- select-function [component function-id]
  (om/update-state! component assoc-in [:input-function :report.function/id] function-id))

(defn- select-group-by [component input-group-by]
  (om/update-state! component assoc-in [:input-report :report/group-by] input-group-by))

(defn- chart-function [component input-function]
  (let [function-id (:report.function/id input-function)]
    (html
      [:fieldset.fieldset
       [:legend "Calculate"]
       [:input
        (opts {:type     "radio"
               :on-click #(select-function component :report.function.id/sum)
               :name     "function"
               :id       "function-sum"
               :checked  (= function-id :report.function.id/sum)})]
       [:label {:for "function-sum"} "Sum"]
       [:input
        (opts {:type     "radio"
               :on-click #(select-function component :report.function.id/mean)
               :name     "function"
               :id       "function-mean"
               :checked  (= function-id :report.function.id/mean)})]
       [:label {:for "function-mean"} "Mean"]])))

(defn- chart-group-by [component {:keys [report/group-by]} groups]
  (html
    [:fieldset.fieldset
     [:legend "Group by"]
     (map
       (fn [k]
         (let [conf {:transaction/tags     {:icon "fa fa-tag"
                                            :text "Tags"}
                     :transaction/currency {:icon "fa fa-usd"
                                            :text "Currencies"}
                     :transaction/date     {:icon "fa fa-calendar"
                                            :text "Dates"}}]
           [:div
            (opts {:style {:display :inline-block}})
            [:input
             (opts {:type     "radio"
                    :on-click #(select-group-by component k)
                    :name     "group-by"
                    :id       (str "group-by-" k)
                    :checked  (= group-by k)})]
            [:label {:for (str "group-by-" k)}
             [:i
              (opts {:key   [(get-in conf [k :icon])]
                     :class (get-in conf [k :icon])
                     :style {:margin-right 5}})]
             [:span
              (opts {:key [(get-in conf [k :text])]})
              (get-in conf [k :text])]]]
           ))
       groups)]))



(defui ChartSettings
  Object
  (initLocalState [this]
    (let [{:keys [report]} (om/get-computed this)]
      {:input-function (first (:report/functions report))
       :input-report   report}))
  (componentWillReceiveProps [this new-props]
    (let [{:keys [graph
                  report]} (::om/computed new-props)
          {:keys [include-mean-line?]} (om/get-state this)
          include-mean-option? (let [{:keys [graph/style]} graph]
                                 (or (= style :graph.style/line) (= style :graph.style/area)))]
      (om/update-state! this assoc
                        :input-function (first (:report/functions report))
                        :input-report report
                        :include-mean-line? (if include-mean-option? include-mean-line? false))))
  (componentDidUpdate [this _ _]
    (let [{:keys [input-function
                  input-report
                  include-mean-line?]} (om/get-state this)
          {:keys [on-change]} (om/get-computed this)]
      (when on-change
        (let [functions (if include-mean-line?
                          [input-function (assoc input-function :report.function/id :report.function.id/mean)]
                          [input-function])]
          (on-change (assoc input-report :report/functions functions))))))
  (render [this]
    (let [{{:keys [graph/style]} :graph} (om/get-computed this)
          {:keys [input-function
                  input-report
                  include-mean-line?]} (om/get-state this)]
      (debug "Include mean: " include-mean-line?)
      (html
        [:div
         (cond
           (= style :graph.style/bar)
           [:div
            [:div.row.small-up-2.collapse
             [:div.column
              (chart-function this input-function)]
             [:div.column
              (chart-group-by this input-report [:transaction/tags :transaction/currency])]]]

           (or (= style :graph.style/area) (= style :graph.style/line))
           [:div
            [:div.row.small-up-2.collapse
             [:div.column
              (chart-function this input-function)]
             ;[:div.column
             ; (chart-group-by this input-report [:transaction/date])]
             [:div.column
              [:fieldset.fieldset
               [:legend "Options"]
               [:input
                {:type     :checkbox
                 :id       "mean-check"
                 :checked  include-mean-line?
                 :on-click #(om/update-state! this update :include-mean-line? not)}]
               [:label
                {:for "mean-check"}
                "Include average line"]]]]]

           (= style :graph.style/number)
           [:div
            (chart-function this input-function)])]))))

(def ->ChartSettings (om/factory ChartSettings))