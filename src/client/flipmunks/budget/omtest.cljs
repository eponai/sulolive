(ns flipmunks.budget.omtest
  (:require [om.core :as om]
            [sablono.core :as html :refer-macros [html]]
            [cognitect.transit :as t]))

(def testdata {2015 {1 {1 {:purchases [{:name "coffee" :cost {:currency "LEK"
                                                              :price 400}}
                                       {:name "dinner" :cost {:currency "LEK"
                                                              :price 1000}}]
                           :rates {"LEK" 0.0081}}
                        2 {:purchases [{:name "lunch" :cost {:currency "LEK"
                                                             :price 600}}]
                           :rates {"LEK" 0.0081}}}}})

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
    om/IWillMount
    (will-mount [this] (prn {:will-mount-day day}))
    om/IDidMount
    (did-mount [this] (prn {:did-mount-day day }))
    om/IWillUnmount
    (will-unmount [this] (prn {:will-unmount-day day}))
    om/IWillUpdate
    (will-update [this next-props next-state] (prn {:will-update-day day}))
    om/IDidUpdate 
    (did-update [this prev-props prev-state] (prn {:did-update-day day}))
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
             (for [day (range (days-in-month month))
                   :when (contains? @days day)]
               (do
                 (om/build (partial day-budget-view day) (get days day))))]))))

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

(defn paste-state-field [app-state]
  [:input {:on-key-down #(when (= (.-keyCode %) 13)
                           (let [text (.-value (.-target %))
                                 to-clj (t/read (t/reader :json-verbose) (js/JSON.parse text))]
                             (reset! app-state to-clj)))}])

(defn widget [app-state data owner]
  (prn (->> @data (t/write (t/writer :json-verbose))))
  (reify
    om/IRender
    (render [this]
      (html [:div 
             (om/build root-widget (:level1 data))
             (paste-state-field app-state)]))))

(defn run []
  (let [app-state (atom {:level1 {:date {:year 2015 :month 1}
                                  :budget-data testdata}})]
    (om/root (partial widget app-state)
             app-state
             {:target (. js/document (getElementById "my-app"))
              :instrument (let [desc (om/no-local-descriptor om/no-local-state-methods)]
                           (fn [f cursor m]
                            (om/build* f cursor 
                                       (assoc m :descriptor desc))))
              })))

