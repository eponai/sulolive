(ns eponai.common.ui.user
  (:require
    [eponai.common.ui.user.orders :as uo]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.user.profile :as profile]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]))

(defui User
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/user [:db/id :user/email {:user/photo [:photo/path]}]}
     {:query/auth [:db/id]}
     {:proxy/profile (om/get-query profile/Profile)}
     :query/current-route])
  Object
  (render [this]
    (let [{:keys [proxy/navbar query/user query/auth proxy/profile query/current-route]} (om/props this)
          {:keys [route route-params]} current-route]
      (dom/div
        #js {:id "sulo-profile" :className "sulo-page"}
        (common/page-container
          {:navbar navbar}
          (condp = route
            :user/order-list (uo/->Orders)
            :user (profile/->Profile (om/computed profile
                                                  {:user             user
                                                   :is-current-user? (= (:db/id user) (:db/id auth))})))
          )))))

(def ->User (om/factory User))