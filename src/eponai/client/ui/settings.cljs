(ns eponai.client.ui.settings
  (:require [eponai.client.ui :refer [map-all] :refer-macros [opts]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]))

(defui Settings
  static om/IQuery
  (query [_]
    [{:query/current-user [:user/uuid
                           :user/email
                           :user/name
                           :user/currency]}])
  Object
  (render [this]
    (let [{:keys [query/current-user]} (om/props this)]
      (html
        [:div
         (opts {:style {:display        "flex"
                        :flex-direction "column"
                        :align-items    "center"}})


         [:div#general
          [:h3
           "General"]
          [:label.form-control-static
           "Plan:"]
          [:div
           [:button
            {:class    "btn btn-default btn-lg"
             :on-click #(om/transact! this `[(stripe/cancel)])}
            "Cancel plan"]]
          [:label.form-control-static
           "Email:"]
          [:input.form-control
           {:value    (:user/email current-user)
            :disabled true}]
          [:label.form-control-static
           "Name"]
          [:input.form-control
           {:value (:user/name current-user)}]

          [:label.form-control-static
           "Main currency"]
          [:input.form-control
           {:value (:user/currency current-user)}]]]))))

(def ->Settings (om/factory Settings))

