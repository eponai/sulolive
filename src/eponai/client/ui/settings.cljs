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
                           {:user/currency [:currency/code
                                            :currency/name]}]}])
  Object
  (componentWillReceiveProps [this new-props]
    (let [{:keys [query/current-user]} new-props]
      (om/update-state! this assoc :input-currency (-> current-user
                                                       :user/currency
                                                       :currency/code))))
  (render [this]
    (let [{:keys [query/current-user]} (om/props this)
          {user-name :user/name
           :keys [user/email]} current-user
          {:keys [input-currency]} (om/get-state this)]
      (prn "Currency: " input-currency)
      (html
        [:div
         (opts {:style {:display        "flex"
                        :flex-direction "column"
                        :align-items    "center"}})
         (prn "Currenct user: " current-user)


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
           {:value    email
            :disabled true}]
          [:label.form-control-static
           "Name"]
          [:input.form-control
           {:value user-name}]

          [:label.form-control-static
           "Main currency"]
          [:input.form-control
           {:value     input-currency
            :on-change #(om/update-state! this assoc :input-currency (.-value (.-target %)))
            :list      "currency-name"}]
          [:datalist#currency-name
           [:option {:value "SEK"}]
           [:option {:value "USD"}]]
          [:button.btn.btn-info.btn-md
           {:on-click #(om/transact! this `[(settings/save ~{:currency input-currency
                                                             :user current-user})])}
           "Save settings"]]]))))

(def ->Settings (om/factory Settings))

