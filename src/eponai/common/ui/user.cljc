(ns eponai.common.ui.user
  (:require
    [eponai.common.ui.user.order-list :as uo]
    [eponai.common.ui.user.order-receipt :as o]
    [eponai.common.ui.router :as router]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug warn]]
    [eponai.common.ui.dom :as dom]))

(defui User
  static om/IQuery
  (query [_]
    [{:query/auth [:db/id :user/email
                   {:user/profile [{:user.profile/photo [:photo/path
                                                         :photo/id]}
                                   :user.profile/name]}]}
     {:proxy/order (om/get-query o/Order)}
     {:proxy/order-list (om/get-query uo/OrderList)}
     :query/current-route])
  Object
  (render [this]
    (let [{:proxy/keys [order order-list]
           :query/keys [auth current-route]} (om/props this)
          {:keys [route]} current-route]
      (dom/div
        {:id     "sulo-user"}
        (condp = route
          :user/order-list (uo/->OrderList order-list)
          :user/order (o/->Order order)
          (warn "Unknown route: " route))))))

(def ->User (om/factory User))

(router/register-component :user User)