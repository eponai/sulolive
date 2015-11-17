(ns flipmunks.budget.omtest
  (:require [om.core :as om]
            [sablono.core :as html :refer-macros [html]]
            [cognitect.transit :as t]))

(defn days-in-month [x]
  ;;TODO: GET A DATE LIBRARY
  31)

(defn month-to-str [n]
  ;;TODO: GET A DATE LIBRARY
  (get {1 "January"
        2 "February"
        3 "March"
        4 "April"
        5 "TODO"} n))

(defn purchase-view [rates {:keys [name cost]}]
  [:div {:class "row"}
   [:div {:class "col-md-6"} name]
   [:div {:class "col-md-6"} (str (:price cost) " " (:currency cost))]])

(defn day-budget-view [day budget-day owner]
  (reify
    om/IInitState
    (init-state [_]
      {:expanded false})
    om/IRenderState
    (render-state [_ {:keys [expanded]}]
      (let [{:keys [purchases rates]} @budget-day
            prices (map (juxt (comp :price :cost)
                              #(let [curr (-> % :cost :currency)]
                                 (* (get rates curr)
                                    (-> % :cost :price))))
                        purchases)]
        ;;TODO: want to be able to expand this view. What about local state?
        (html
          [:div {:class "row"
                 :on-click #(om/update-state! owner :expanded not)} ;;inverse the value
           "day: " day
           (when expanded
             [:div {:class "col-md-4"} (str "Day " day ".")] 
             [:div {:class "col-md-4"} (reduce + 0 (map first prices))] 
             [:div {:class "col-md-4"} (reduce + 0 (map second prices))] 
             (map (partial purchase-view rates) purchases))])))))

(defn month-budget-view [month days owner]
  (reify
    om/IRender
    (render [this]
      (html [:div 
             (when days
               (for [day (range (days-in-month month))
                     :when (contains? @days day)]
                 (do
                   (om/build (partial day-budget-view day) (get days day)))))]))))

(defn root-widget [data owner]
  (reify
    om/IRender
    (render [this]
      (let [{:keys [year month]} (:date data)]
        (html [:div {:class "container"}
               [:div {:class "page-header"}
                [:h1 "Budget App"]
                [:h2 (str year "-" (month-to-str month))]]
               (om/build (partial month-budget-view month)
                         (-> data :budget-data (get year) (get month)))])))))

(defn paste-state-field [data]
  [:input {:on-key-down #(when (= (.-keyCode %) 13)
                           (let [text (.-value (.-target %))
                                 to-clj (t/read (t/reader :json-verbose) (js/JSON.parse text))]
                             (om/transact! data (constantly to-clj))))}])

(defn widget [data owner]
  (reify
    om/IRender
    (render [this]
      (prn (->> @data (t/write (t/writer :json-verbose))))
      (html [:div 
             (om/build root-widget data)
             (paste-state-field data)]))))

(defn run [data]
  (let [app-state (atom {:date {:year 2015 :month 1} 
                         :budget-data data})]
    (om/root widget 
             app-state
             {:target (. js/document (getElementById "my-app"))})))

