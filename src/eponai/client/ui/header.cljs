(ns eponai.client.ui.header
  (:require [om.next :as om :refer-macros [defui]]
            [eponai.client.ui :refer-macros [opts map-all]]
            [eponai.client.ui.modal :refer [->Modal Modal]]
            [eponai.client.ui.add_transaction :as add.t :refer [->AddTransaction]]
            [sablono.core :refer-macros [html]]
            [garden.core :refer [css]]))

(defui Menu
  static om/IQuery
  (query [_]
    [{:query/all-budgets [:budget/uuid
                          :budget/name]}])
  Object
  (render [this]
    (let [{:keys [query/all-budgets
                  on-close]} (om/props this)]
      (html
        [:div
         {:class "dropdown open"}
         [:div#click-outside-target
          (opts {:style    {:top      0
                            :bottom   0
                            :right    0
                            :left     0
                            :position "fixed"}
                 :on-click on-close})]
         [:ul
          {:class "dropdown-menu dropdown-menu-right"}
          (when (not-empty all-budgets)
            [:li.dropdown-header
             "Budgets"])
          (map
            (fn [budget]
              [:li
               (opts {:key [(:budget/uuid budget)]})
               [:a (or (:budget/name budget) "Untitled")]])
            all-budgets)

          [:li.divider]
          [:li
           [:a
            "All Transactions"]]
          [:li.divider]
          [:li
           [:a
            "Profile"]]
          [:li
           [:a
            "Settings"]]
          [:li.divider]
          [:li
           [:a
            {:href "/api/logout"}
            "Sign Out"]]]]))))

(def ->Menu (om/factory Menu))

(defui Header
  static om/IQuery
  (query [_]
    [{:query/modal [:ui.singleton.modal/visible]}
     {:query/menu [:ui.singleton.menu/visible]}
     {:proxy/profile-menu (om/get-query Menu)}
     {:proxy/add-transaction (om/get-query add.t/AddTransaction)}])
  Object
  (render
    [this]
    (let [{:keys [query/modal
                  query/menu
                  proxy/profile-menu
                  proxy/add-transaction]} (om/props this)
          {modal-visible :ui.singleton.modal/visible} modal
          {menu-visible :ui.singleton.menu/visible} menu]
      (html
        [:div
         [:nav
          (opts {:class "navbar navbar-default navbar-fixed-top topnav"
                 :role  "navigation"})

          [:div.navbar-brand
           "JourMoney"]

          [:div
           (opts {:style {:display         "flex"
                          :flex            "row-reverse"
                          :align-items     "flex-end"
                          :justify-content "flex-end"}})


           [:button
            (opts {:style    {:display "block"
                              :margin  "0.5em 0.2em"}
                   :on-click #(om/transact! this `[(ui.modal/show) :query/modal])
                   :class    "btn btn-default btn-md"})
            "New"]

           [:img
            (opts {:class    "img-circle"
                   :style    {:margin "0.1em 1em"
                              :width  "40"
                              :height "40"}
                   :src      "http://thesocialmediamonthly.com/wp-content/uploads/2015/08/photo.png"
                   :on-click #(om/transact! this `[(ui.menu/show) :query/menu])})]]

          (when menu-visible
            (->Menu (merge profile-menu
                           {:on-close #(om/transact! this `[(ui.menu/hide) :query/menu])})))]

         (when modal-visible
           (->Modal (merge modal
                           {:header   #(str "Add Transaction")
                            :body     #(->AddTransaction add-transaction)
                            :on-close #(om/transact! this `[(ui.modal/hide) :query/modal])})))]))))

(def ->Header (om/factory Header))
