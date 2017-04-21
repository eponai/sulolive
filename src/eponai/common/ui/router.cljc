(ns eponai.common.ui.router
  (:require
    [om.next :as om :refer [defui]]
    [om.dom :as dom]
    [eponai.common.routes :as routes]
    [eponai.common.ui.help :as help]
    [eponai.common.ui.checkout :as checkout]
    [eponai.common.ui.shopping-bag :as bag]
    [eponai.common.ui.store :as store]
    [eponai.common.ui.store.new-store :as new-store]
    [eponai.common.ui.store.dashboard :as store-dashboard]
    [eponai.common.ui.goods :as goods]
    [eponai.common.ui.index :as index]
    [eponai.common.ui.product :as product]
    [eponai.common.ui.streams :as streams]
    [eponai.common.ui.business :as business]
    [eponai.common.ui.user :as user]
    [eponai.common.ui.settings :as settings]))

(def dom-app-id "the-sulo-app")

(def route->component
  {:index           {:component index/Index
                     :factory   index/->Index}
   :coming-soon     {:component index/ComingSoon
                     :factory   index/->ComingSoon}
   :sell-soon       {:component index/ComingSoonBiz
                     :factory   index/->ComingSoonBiz}
   :store           {:component store/Store
                     :factory   store/->Store}
   :new-store       {:component new-store/NewStore
                     :factory   new-store/->NewStore}
   :store-dashboard {:component store-dashboard/Dashboard
                     :factory   store-dashboard/->Dashboard}
   :checkout        {:component checkout/Checkout
                     :factory   checkout/->Checkout}
   :shopping-bag    {:component bag/ShoppingBag
                     :factory   bag/->ShoppingBag}
   :help            {:component help/Help
                     :factory   help/->Help}
   :products        {:component goods/Goods
                     :factory   goods/->Goods}
   :product         {:component product/ProductPage
                     :factory   product/->ProductPage}
   :live            {:component streams/Streams
                     :factory   streams/->Streams}
   :business        {:component business/Business
                     :factory   business/->Business}
   :user            {:component user/User
                     :factory   user/->User}
   :settings        {:component settings/Settings
                     :factory   settings/->Settings}
   :login           {:component index/Login
                     :factory   index/->Login}
   :unauthorized    {:component index/Unauthorized
                     :factory index/->Unauthorized}})

(defn normalize-route
  "We need to normalize our routes now that we have namespaced route matches.
  The namespaced route matches help us set new routes."
  [route]
  (if-let [ns (namespace route)]
    (keyword ns)
    route))

(defui Router
  static om/IQuery
  (query [this]
    [:query/current-route
     {:routing/app-root (into {}
                              (map (fn [[route {:keys [component]}]]
                                     [route (om/get-query component)]))
                              route->component)}])
  Object
  (render [this]
    (let [{:keys [routing/app-root query/current-route]} (om/props this)
          route (normalize-route (:route current-route :index))
          factory (get-in route->component [route :factory])]
      (factory app-root))))
