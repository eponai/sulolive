(ns eponai.common.routes
  (:require
    [eponai.common.ui.checkout :as checkout]
    [eponai.common.ui.store :as store]
    [eponai.common.ui.goods :as goods]
    [eponai.common.ui.index :as index]
    [eponai.common.ui.product :as product]
    [eponai.common.ui.streams :as streams]))

(def routes
  ["/" {""                     :index
        ["store/" :store-id]   :store
        "goods"                :goods
        ["goods/" :product-id] :goods
        "streams"              :streams
        "checkout"             :checkout}])

(def route->component
  {:index    {:component index/Index
              :factory   index/->Index}
   :store    {:component store/Store
              :factory   store/->Store}
   :checkout {:component checkout/Checkout
              :factory   checkout/->Checkout}
   :goods    {:component goods/Goods
              :factory   goods/->Goods}
   :product  {:component product/ProductPage
              :factory   product/->ProductPage}
   :streams  {:component streams/Streams
              :factory   streams/->Streams}})
