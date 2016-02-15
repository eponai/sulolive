(ns eponai.client.ui.header
  (:require [om.next :as om :refer-macros [defui]]
            [eponai.client.ui :refer-macros [opts map-all]]
            [eponai.client.ui.format :as f]
            [eponai.client.routes :as routes]
            [eponai.client.ui.modal :refer [->Modal Modal]]
            [eponai.client.ui.add_transaction :as add.t :refer [->AddTransaction]]
            [sablono.core :refer-macros [html]]
            [garden.core :refer [css]]))

(defn menu-item [component name opts]
  [:a
   (merge {:on-click #(om/transact! component '[(ui.menu/hide) :query/menu])}
          opts)
   name])

(defui Menu
  static om/IQuery
  (query [_]
    [{:query/all-budgets [:budget/uuid
                          :budget/name]}
     {:query/current-user [:user/uuid
                           :user/activated-at]}])
  Object
  (render [this]
    (let [{:keys [query/all-budgets
                  query/current-user
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
          [:li.dropdown-header
           (let [activated-at (:user/activated-at current-user)]
             (if activated-at
               (str "Trial: " (max 0 (- 14 (f/days-since activated-at))) " days left")
               (str "Trial ended")))]
          [:li
           (menu-item this "Buy" (opts {:style {:display "block"
                                                :margin  "0.5em 0.2em"}
                                        :class "btn btn-primary btn-md"
                                        :href  (routes/inside "/subscribe/")}))]
          [:li.divider]

          (when (not-empty all-budgets)
            [:li.dropdown-header
             "Sheets"])
          (map
            (fn [budget]
              [:li
               (opts {:key [(:budget/uuid budget)]})
               (menu-item this
                          (or (:budget/name budget) "Untitled")
                          {:href (routes/inside "/dashboard/" (:budget/uuid budget))})])
            all-budgets)

          [:li.divider]
          [:li
           (menu-item this
                      "All Transactions"
                      {:href (routes/inside "/transactions")})]
          [:li.divider]
          [:li
           (menu-item this
                      "Profile"
                      {:href "#"})]
          [:li
           (menu-item this
                      "Settings"
                      {:href (routes/inside "/settings")})]
          [:li.divider]
          [:li
           (menu-item this
                      "Sign Out"
                      {:href (routes/outside "/api/logout")})]]]))))

(def ->Menu (om/factory Menu))

(defui Header
  static om/IQuery
  (query [_]
    [{:query/menu [:ui.singleton.menu/visible]}
     {:proxy/profile-menu (om/get-query Menu)}])
  Object
  (render
    [this]
    (let [{:keys [query/menu
                  proxy/profile-menu]} (om/props this)
          {menu-visible :ui.singleton.menu/visible} menu]
      (html
        [:nav
         (opts {:class "navbar navbar-default navbar-fixed-top topnav"
                :role  "navigation"})

         [:div
          [:a.navbar-brand
           {:href (routes/inside "/")}
           [:strong
            "JourMoney"]
           [:span.small
            " by eponai"]]]

         [:div
          (opts {:style {:display         "flex"
                         :flex            "row-reverse"
                         :align-items     "flex-end"
                         :justify-content "flex-end"}})

          [:button
           (opts {:style    {:display "block"
                             :margin  "0.5em 0.2em"}
                  :on-click #(om/transact! this `[(ui.modal/show ~{:content :ui.singleton.modal.content/add-transaction}) :query/modal])
                  :class    "btn btn-default btn-md"})
           "New"]

          [:img
           (opts {:class    "img-circle"
                  :style    {:margin "0.1em 1em"
                             :width  "40"
                             :height "40"}
                  :src      "/style/img/profile.png"
                  :on-click #(om/transact! this `[(ui.menu/show) :query/menu])})]]

         (when menu-visible
           (->Menu (merge profile-menu
                          {:on-close #(om/transact! this `[(ui.menu/hide) :query/menu])})))]))))

(def ->Header (om/factory Header))
