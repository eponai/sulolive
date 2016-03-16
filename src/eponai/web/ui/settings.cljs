(ns eponai.web.ui.settings
  (:require [eponai.client.ui :refer [map-all] :refer-macros [opts]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [datascript.core :as d]
            [taoensso.timbre :refer-macros [debug]]))

(defui Settings
  static om/IQuery
  (query [_]
    [{:query/current-user [:user/uuid
                           :user/email
                           :user/name
                           {:user/currency [:currency/code
                                            :currency/name]}]}
     {:query/all-currencies [:currency/code
                             :currency/name]}])
  Object
  (componentWillReceiveProps [this new-props]
    (let [{:keys [query/current-user]} new-props]
      (om/update-state! this assoc :input-currency (-> current-user
                                                       :user/currency
                                                       :currency/code))))
  (componentWillMount [this]
    (let [{:keys [query/current-user]} (om/props this)]
      (om/update-state! this assoc :input-currency (-> current-user
                                                       :user/currency
                                                       :currency/code))))
  (render [this]
    (let [{:keys [query/current-user
                  query/all-currencies]} (om/props this)
          {user-name :user/name
           :keys [user/email]} current-user
          {:keys [input-currency]} (om/get-state this)]
      (html
        [:div
         (opts {:style {:display        "flex"
                        :flex-direction "column"
                        :align-items    "center"}})

         [:div#general
          [:h3
           "General"]
          [:label
           "Plan:"]
          [:div
           [:a
            {:class    "button secondary"
             :on-click #(om/transact! this `[(stripe/cancel)])}
            "Cancel plan"]]
          [:label
           "Email:"]
          [:input
           {:value    email
            :type "email"
            :disabled true}]
          [:label
           "Name"]
          [:input.form-control
           {:value user-name
            :type "text"}]

          [:label
           "Main currency"]
          [:input
           {:value     input-currency
            :type      "text"
            :on-change #(om/update-state! this assoc :input-currency (.-value (.-target %)))
            :list      "currency-name"}]
          [:datalist#currency-name
           (map-all
             all-currencies
             (fn [c]
               [:option {:value (:currency/code c)}
                (:currency/name c)]))]
          [:a.button.primary
           {:on-click #(om/transact! this `[(settings/save ~{:currency      input-currency
                                                             :user          current-user
                                                             :mutation-uuid (d/squuid)})
                                            :query/dashboard
                                            :query/transactions])}
           "Save settings"]]]))))

(def ->Settings (om/factory Settings))

