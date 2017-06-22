(ns eponai.common.ui.user
  (:require
    [eponai.common.ui.user.order-list :as uo]
    [eponai.common.ui.user.order-receipt :as o]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.router :as router]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug warn]]
    [eponai.web.ui.footer :as foot]))

(defui User
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:proxy/footer (om/get-query foot/Footer)}
     {:query/auth [:db/id :user/email
                   {:user/profile [{:user.profile/photo [:photo/path
                                                         :photo/id]}
                                   :user.profile/name]}]}
     {:proxy/order (om/get-query o/Order)}
     {:proxy/order-list (om/get-query uo/OrderList)}
     :query/current-route])
  Object
  (render [this]
    (let [{:proxy/keys [order navbar footer order-list profile-edit]
           :query/keys [auth current-route]} (om/props this)
          {:keys [route]} current-route]
      (common/page-container
        {:navbar navbar
         :footer (om/computed footer
                              {:on-change-location #(om/transact! this [:query/featured-items])})
         :id     "sulo-user"}
        (condp = route
          :user/order-list (uo/->OrderList order-list)
          :user/order (o/->Order order)
          (warn "Unknown route: " route))))))

(def ->User (om/factory User))

(router/register-component :user User)