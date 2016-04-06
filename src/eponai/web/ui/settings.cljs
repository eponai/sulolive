(ns eponai.web.ui.settings
  (:require [eponai.client.ui :refer [map-all] :refer-macros [opts]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [datascript.core :as d]
            [taoensso.timbre :refer-macros [debug]]
            [eponai.web.ui.utils :as utils]
            [eponai.web.ui.format :as f]))

(defn get-loader [props]
  (get props [:ui/singleton :ui.singleton/loader]))
(defui Settings
  static om/IQuery
  (query [_]
    [{:query/current-user [:user/uuid
                           :user/email
                           :user/name
                           {:user/currency [:currency/code
                                            :currency/name]}]}
     {[:ui/singleton :ui.singleton/loader] [:ui.singleton.loader/visible]}
     {:query/all-currencies [:currency/code
                             :currency/name]}
     {:query/stripe [:stripe/user
                     {:stripe/subscription [:stripe.subscription/period-end
                                            :stripe.subscription/status]}]}
     {:query/fb-user [:fb-user/name
                      :fb-user/id
                      :fb-user/picture]}])
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
                  query/all-currencies
                  query/fb-user
                  query/stripe] :as props} (om/props this)
          loader (get-loader props)
          {user-name :user/name
           :keys [user/email]} current-user
          {:keys [input-currency]} (om/get-state this)
          {:keys [stripe/subscription]} stripe
          {subscription-status :stripe.subscription/status} subscription]
      (html
        [:div
         [:div#settings-general.row.column.small-12.medium-6
          [:div.callout.clearfix
           [:h3
            "General"]
           [:div.row
            [:div.columns.small-2.text-right
             [:label
              "Plan:"]]
            (if (= subscription-status :active)
              [:div.columns.small-10
               [:div
                [:strong "Monthly"]
                [:a.link
                 (opts {:style    {:margin "1em 0"}
                        :on-click #(om/transact! this `[(stripe/cancel ~{:mutation-uuid (d/squuid)})
                                                        :query/stripe])})
                 [:small
                  "Cancel plan"]]]
               (when (:ui.singleton.loader/visible loader)
                 (utils/loader))]

              [:div.columns.small-10
               ;[:p "You have "
               ; (f/days-until (:stripe.subscription/period-end subscription)) " days left on your trial."]
               ;[:div.columns.small-12.medium-6.text-center]
               (utils/upgrade-button {:style {:margin 0}})
               ;[:div [:small "You have "
               ;       (f/days-until (:stripe.subscription/period-end subscription)) " days left on your trial."]]
               ])]
           [:hr]
           [:div.row
            [:div.columns.small-2.text-right
             [:label
              "Email:"]]
            [:div.columns.small-10
             [:p [:strong email]]]]

           [:div.row
            [:div.columns.small-2.text-right
             [:label
              "Name:"]]
            [:div.columns.small-10
             [:input
              {:value (or user-name "")
               :type  "text"}]]]
           [:div.row

            [:div.columns.small-2.text-right
             [:label
              "Currency:"]]
            [:div.columns.small-10
             [:input
              {:value     (or input-currency "")
               :type      "text"
               :on-change #(om/update-state! this assoc :input-currency (.-value (.-target %)))
               :list      "currency-name"}]]
            [:datalist#currency-name
             (map-all
               all-currencies
               (fn [{:keys [currency/code currency/name]}]
                 [:option (opts {:key   [code]
                                 :value code})
                  name]))]]

           [:a.button.primary.hollow.float-right
            {:on-click #(om/transact! this `[(settings/save ~{:currency      input-currency
                                                              :user          current-user
                                                              :mutation-uuid (d/squuid)})
                                             :query/dashboard
                                             :query/transactions])}
            "Save settings"]]]
         [:div.row.column.small-12.medium-6
          [:div.callout
           [:h3 "Social"]
           [:hr]
           [:div#facebook-connect
            (if (nil? fb-user)
              [:div.row
               [:div.column
                [:a.button.btn-facebook [:i.fa.fa-facebook.fa-fw] "Connect to Facebook"]]]
              [:div.row.collapse
               [:div.columns.small-1
                [:i.fa.fa-facebook.fa-fw]]
               [:div.columns.small-8
                [:strong (:fb-user/name fb-user)]]

               [:div.columns.small-3
                [:a.link
                 [:small
                  "Disconnect"]]]])]]]]))))

(def ->Settings (om/factory Settings))

