(ns eponai.client.ui.header
  (:require [om.next :as om :refer-macros [defui]]
            [eponai.client.ui :refer-macros [opts]]
            [eponai.client.ui.modal :refer [->Modal Modal]]
            [eponai.client.ui.add_transaction :as add.t :refer [->AddTransaction]]
            [sablono.core :refer-macros [html]]
            [garden.core :refer [css]]))

(defui Menu
  Object
  (render [this]
    (let [{:keys [on-close]} (om/props this)]
      (println "Render menu")
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
         [:ul.dropdown-menu
          (opts {:style {:right 0
                         :left "auto"}})
          [:li.dropdown [:a "All Transactions"]]
          [:li.dropdown
           [:a
            {:href "/api/logout"}
            "Sign Out"]]]]))))

(def ->Menu (om/factory Menu))

(defui Header
  static om/IQuery
  (query [_]
    [{:query/modal [:ui.singleton.modal/visible]}
     {:query/menu [:ui.singleton.menu/visible]}
     {:proxy/add-transaction (om/get-query add.t/AddTransaction)}])
  Object
  (render
    [this]
    (let [{:keys [query/modal
                  query/menu
                  proxy/add-transaction]} (om/props this)
          {modal-visible :ui.singleton.modal/visible} modal
          {menu-visible :ui.singleton.menu/visible} menu]
      (println "Menu props: " menu)
      (html
        [:div
         [:nav
          (opts {:class "navbar navbar-default navbar-fixed-top topnav"
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
            (->Menu (merge menu
                           {:on-close #(om/transact! this `[(ui.menu/hide) :query/menu])})))]

         (when modal-visible
           (->Modal (merge modal
                           {:header   #(str "Add Transaction")
                            :body     #(->AddTransaction add-transaction)
                            :on-close #(om/transact! this `[(ui.modal/hide) :query/modal])})))]))))

(def ->Header (om/factory Header))
