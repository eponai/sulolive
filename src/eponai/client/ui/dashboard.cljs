(ns eponai.client.ui.dashboard
  (:require [eponai.client.ui :refer [map-all] :refer-macros [style opts]]
            [garden.core :refer [css]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]))

(defui Dashboard
  om/IQuery
  (query [_]
    ;; TODO: Hook up a mutate to set the :budget-uuid
    ['{(:query/one-budget {:budget-uuid {:e [:ui/component :ui.component/dashboard]
                                         :a :ui.component.dashboard/active-budget}})
       [:budget/uuid]}])
  Object
  (render [this]
    (let [{:keys [query/one-budget]} (om/props this)]
      (html
        [:div (str "Showing budget: " (:budget/uuid one-budget))]))))

(def ->Dashboard (om/factory Dashboard))
