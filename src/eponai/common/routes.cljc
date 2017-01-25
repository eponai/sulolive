(ns eponai.common.routes
  (:require
    [eponai.common.ui.shopping-bag :as bag]
    [eponai.common.ui.store :as store]
    [eponai.common.ui.goods :as goods]
    [eponai.common.ui.index :as index]
    [eponai.common.ui.product :as product]
    [eponai.common.ui.streams :as streams]
    [eponai.common.ui.business :as business]
    [eponai.common.ui.profile :as profile]
    [eponai.common.ui.settings :as settings]))

(def routes
  ["/" {""                     :index
        "coming-soon"          :coming-soon
        ["store/" :store-id]   :store
        "goods"                :goods
        ["goods/" :product-id] :product
        "streams"              :streams
        "checkout"             :checkout
        "shopping-bag"                 :shopping-bag
        "business"             :business
        "profile"              :profile
        "settings"             :settings}])

(def route->component
  {:index        {:component index/Index
                  :factory   index/->Index}
   :coming-soon  {:component index/ComingSoon
                  :factory   index/->ComingSoon}
   :store        {:component store/Store
                  :factory   store/->Store}
   :checkout     {:component bag/ShoppingBag
                  :factory   bag/->ShoppingBag}
   :shopping-bag {:component bag/ShoppingBag
                  :factory   bag/->ShoppingBag}
   :goods        {:component goods/Goods
                  :factory   goods/->Goods}
   :product      {:component product/ProductPage
                  :factory   product/->ProductPage}
   :streams      {:component streams/Streams
                  :factory   streams/->Streams}
   :business     {:component business/Business
                  :factory   business/->Business}
   :profile      {:component profile/Profile
                  :factory   profile/->Profile}
   :settings     {:component settings/Settings
                  :factory   settings/->Settings}})
