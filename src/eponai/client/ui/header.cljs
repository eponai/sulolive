(ns eponai.client.ui.header
  (:require [om.next :as om :refer-macros [defui]]
            [eponai.client.ui :refer-macros [opts]]
            [eponai.client.ui.modal :refer [->Modal Modal]]
            [eponai.client.ui.add_transaction :as add.t :refer [->AddTransaction]]
            [sablono.core :as html :refer-macros [html]]
            [garden.core :refer [css]]))

(defui Header
  static om/IQuery
  (query [_]
    [{:query/modal [:ui.singleton.modal/visible]}
     {:proxy/add-transaction (om/get-query add.t/AddTransaction)}])
  Object
  (render
    [this]
    (let [{:keys [query/modal proxy/add-transaction]} (om/props this)
          {:keys [ui.singleton.modal/visible]} modal]
      (html [:nav
             (opts {:style {}
                    :class "navbar navbar-default navbar-fixed-top topnav"
                    :role  "navigation"})

             [:div.navbar-brand
              "JourMoney"]

             [:form
              {:action "/api/logout"
               :id     "logout-form"
               :method "get"}]

             ;[:button
             ; {:class "btn btn-default btn-md"
             ;  :type  "submit"
             ;  :form  "logout-form"} "logout"]

             [:div
              (opts {:style {:display "flex"
                             :flex "row-reverse"
                             :align-items "flex-end"
                             :justify-content "flex-end"}})


              [:button
               (opts {:style {:display "block"
                              :margin "0.5em 0.2em"}
                      :on-click #(om/transact! this `[(ui.modal/show) :query/modal])
                      :class    "btn btn-default btn-md"})
               "New"]

              [:img
               (opts {:class "img-circle"
                      :style {:margin "0.1em 1em"
                              :width "40"
                              :height "40"}
                      :src   "http://thesocialmediamonthly.com/wp-content/uploads/2015/08/photo.png"})]
              ]

             (when visible
               (->Modal (merge modal
                               {:dialog-content #(->AddTransaction add-transaction)
                                :on-close       #(om/transact! this `[(ui.modal/hide) :query/modal])})))]))))

(def ->Header (om/factory Header))
