(ns eponai.common.routes
  (:require
    [eponai.common.ui.shopping-bag :as bag]
    [eponai.common.ui.store :as store]
    [eponai.common.ui.store.new-store :as new-store]
    [eponai.common.ui.store.store-dashboard :as store-dashboard]
    [eponai.common.ui.goods :as goods]
    [eponai.common.ui.index :as index]
    [eponai.common.ui.product :as product]
    [eponai.common.ui.streams :as streams]
    [eponai.common.ui.business :as business]
    [eponai.common.ui.profile :as profile]
    [eponai.common.ui.settings :as settings]))

(def store-routes
  {""           :store
   "/dashboard" {""                                    :store-dashboard
                 ["/" [#"products" :dashboard-option]] {""                             :store-dashboard
                                                        ["/" [#"create" :action]] :store-dashboard
                                                        ["/" [#"\d+" :product-id]]     :store-dashboard}
                 ["/" [#"orders" :dashboard-option]]   {""                       :store-dashboard
                                                        ["/" [#"\d+" :order-id]] :store-dashboard}}})
(def routes
  ["/" {""                            :index
        "coming-soon"                 :coming-soon
        "sell/coming-soon"            :sell-soon
        "store/new"                   :new-store
        ["store/" [#"\d+" :store-id]] store-routes
        "goods"                       :goods
        ["goods/" :product-id]        :product
        "streams"                     :streams
        "checkout"                    :checkout
        "shopping-bag"                :shopping-bag
        "business"                    :business
        ["profile/" :user-id]         :profile
        "settings"                    :settings}])

;; Used on the client side to avoid us using routing for kick-off lab stuff.
(defn without-coming-soon-route [routes]
  (update routes 1 dissoc "coming-soon" "sell/coming-soon"))

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
   :store-dashboard {:component store-dashboard/StoreDashboard
                     :factory   store-dashboard/->StoreDashboard}
   :checkout        {:component bag/ShoppingBag
                     :factory   bag/->ShoppingBag}
   :shopping-bag    {:component bag/ShoppingBag
                     :factory   bag/->ShoppingBag}
   :goods           {:component goods/Goods
                     :factory   goods/->Goods}
   :product         {:component product/ProductPage
                     :factory   product/->ProductPage}
   :streams         {:component streams/Streams
                     :factory   streams/->Streams}
   :business        {:component business/Business
                     :factory   business/->Business}
   :profile         {:component profile/Profile
                     :factory   profile/->Profile}
   :settings        {:component settings/Settings
                     :factory   settings/->Settings}})
